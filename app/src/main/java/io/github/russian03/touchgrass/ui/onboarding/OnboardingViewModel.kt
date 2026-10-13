package io.github.russian03.touchgrass.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.ui.common.LIMIT_RANGE
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val limitMinutes: Int = DEFAULT_LIMIT,
    val saving: Boolean = false,
    val error: Boolean = false,
) {
    companion object {
        const val DEFAULT_LIMIT = 30
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repository: BudgetRepository) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onLimitChange(minutes: Int) = _state.update {
        it.copy(limitMinutes = minutes.coerceIn(LIMIT_RANGE), error = false)
    }

    /** Guarda el límite elegido y llama a [onSaved] si se ha podido. */
    fun saveLimit(onSaved: () -> Unit) = save({ repository.setInitialLimit(_state.value.limitMinutes) }, onSaved)

    /** Marca los primeros pasos como hechos: a partir de aquí el límite solo se cambia en Ajustes. */
    fun finish(onFinished: () -> Unit) = save({ repository.completeOnboarding() }, onFinished)

    private fun save(operation: suspend () -> Any?, onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val denial = operation()
            _state.update { it.copy(saving = false, error = denial != null) }
            if (denial == null) onDone()
        }
    }
}
