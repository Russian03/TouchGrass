package io.github.russian03.touchgrass.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Decide la primera pantalla. Mientras es `null`, se sigue viendo la pantalla de carga. */
@HiltViewModel
class MainViewModel @Inject constructor(repository: BudgetRepository) : ViewModel() {

    private val _start = MutableStateFlow<Any?>(null)
    val start: StateFlow<Any?> = _start.asStateFlow()

    private val _homeRequest = MutableStateFlow(false)

    /** Se ha pedido abrir Inicio desde la pantalla de bloqueo y aún no se ha atendido. */
    val homeRequest: StateFlow<Boolean> = _homeRequest.asStateFlow()

    fun requestHome() {
        _homeRequest.value = true
    }

    fun onHomeRequestHandled() {
        _homeRequest.value = false
    }

    private val _statsRequest = MutableStateFlow<String?>(null)

    /** Mes cuyas estadísticas se han pedido desde la notificación del resumen, aún sin atender. */
    val statsRequest: StateFlow<String?> = _statsRequest.asStateFlow()

    fun openStats(month: String?) {
        if (month != null) _statsRequest.value = month
    }

    fun onStatsRequestHandled() {
        _statsRequest.value = null
    }

    init {
        viewModelScope.launch {
            _start.value = if (repository.state.first().onboardingDone) Home else Onboarding
        }
    }
}
