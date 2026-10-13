package io.github.russian03.touchgrass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.rules.RuleSource
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de la tarjeta de pedir tiempo mientras se termina un desbloqueo. */
data class UnlockUiState(
    val busy: Boolean = false,
    /** Por qué no se ha podido hacer lo último que se pidió. */
    val error: Denial? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: BudgetRepository, private val rules: RuleSource) :
    ViewModel() {

    /** Saldo de hoy, o `null` mientras se carga. */
    val budget: StateFlow<BudgetSummary?> =
        repository.summary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Nombre de cada app por paquete, para decir cuál está desbloqueada. */
    val appNames: StateFlow<Map<String, String>> =
        flow { emit(withContext(Dispatchers.IO) { rules.load() }.associate { it.packageName to it.name }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _unlock = MutableStateFlow(UnlockUiState())
    val unlock: StateFlow<UnlockUiState> = _unlock.asStateFlow()

    /** Termina el desbloqueo y devuelve los minutos que sobran, en múltiplos de 5. */
    fun endUnlock() {
        if (_unlock.value.busy) return
        _unlock.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val denial = repository.endUnlock()
            _unlock.update { it.copy(busy = false, error = denial) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
