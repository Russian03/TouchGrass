package io.github.russian03.touchgrass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.rules.RuleRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se está pidiendo en la tarjeta de pedir tiempo. */
data class UnlockUiState(
    /** Minutos elegidos; `null` si no se han tocado (se muestran [DEFAULT_UNLOCK_MINUTES]). */
    val draft: Int? = null,
    val busy: Boolean = false,
    /** Por qué no se ha podido hacer lo último que se pidió. */
    val error: Denial? = null,
) {
    /** Minutos que se muestran, siempre entre 1 y lo que queda hoy. */
    fun shown(remaining: Int): Int = (draft ?: DEFAULT_UNLOCK_MINUTES).coerceIn(1, remaining.coerceAtLeast(1))

    companion object {
        const val DEFAULT_UNLOCK_MINUTES = 10
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: BudgetRepository, private val rules: RuleRepository) :
    ViewModel() {

    /** Saldo de hoy, o `null` mientras se carga. */
    val budget: StateFlow<BudgetSummary?> =
        repository.summary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _unlock = MutableStateFlow(UnlockUiState())
    val unlock: StateFlow<UnlockUiState> = _unlock.asStateFlow()

    fun onUnlockDraftChange(minutes: Int) = _unlock.update { it.copy(draft = minutes, error = null) }

    /** Desbloquea Instagram los minutos elegidos; se restan del saldo al momento. */
    fun requestUnlock() {
        val remaining = budget.value?.remainingMinutes ?: return
        val minutes = _unlock.value.shown(remaining)
        change {
            // Por ahora solo hay reglas de Instagram: es la app que se desbloquea.
            val allowed = withContext(Dispatchers.IO) { rules.load().map { it.packageName }.toSet() }
            val target = allowed.firstOrNull() ?: return@change Denial.UnknownApp
            repository.requestUnlock(target, minutes, allowed)
        }
    }

    /** Termina el desbloqueo y devuelve los minutos que sobran, en múltiplos de 5. */
    fun endUnlock() = change { repository.endUnlock() }

    private fun change(operation: suspend () -> Denial?) {
        if (_unlock.value.busy) return
        _unlock.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val denial = operation()
            _unlock.update {
                it.copy(busy = false, error = denial, draft = if (denial == null) null else it.draft)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
