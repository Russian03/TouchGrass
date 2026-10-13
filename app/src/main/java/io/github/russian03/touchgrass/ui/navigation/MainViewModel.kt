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

    private val _unlockRequest = MutableStateFlow<String?>(null)

    /** App para la que se ha pedido tiempo desde la pantalla de bloqueo, aún sin atender. */
    val unlockRequest: StateFlow<String?> = _unlockRequest.asStateFlow()

    fun requestUnlock(packageName: String?) {
        if (packageName != null) _unlockRequest.value = packageName
    }

    fun onUnlockRequestHandled() {
        _unlockRequest.value = null
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
