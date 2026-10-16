package io.github.russian03.touchgrass.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.profile.Profile
import io.github.russian03.touchgrass.data.profile.ProfileRepository
import io.github.russian03.touchgrass.ui.common.LIMIT_RANGE
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val name: String = "",
    val limitMinutes: Int = DEFAULT_LIMIT,
    val saving: Boolean = false,
    val error: Boolean = false,
    /** Ha dicho «No, gracias» al aviso del servicio de accesibilidad: no se le manda a activarlo. */
    val declined: Boolean = false,
) {
    companion object {
        const val DEFAULT_LIMIT = 30
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: BudgetRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onNameChange(name: String) = _state.update { it.copy(name = name.take(Profile.NAME_MAX_LENGTH), error = false) }

    /** Guarda el nombre (opcional: vacío no saluda con nombre) y llama a [onSaved] si se ha podido. */
    fun saveName(onSaved: () -> Unit) =
        save({ if (profile.setName(_state.value.name)) null else Denial.Storage }, onSaved)

    fun onLimitChange(minutes: Int) = _state.update {
        it.copy(limitMinutes = minutes.coerceIn(LIMIT_RANGE), error = false)
    }

    /** Guarda el límite elegido y llama a [onSaved] si se ha podido. */
    fun saveLimit(onSaved: () -> Unit) = save({ repository.setInitialLimit(_state.value.limitMinutes) }, onSaved)

    /** Acepta el aviso del servicio de accesibilidad y llama a [onSaved] si se ha podido guardar. */
    fun acceptAccessibility(onSaved: () -> Unit) {
        _state.update { it.copy(declined = false) }
        save({ if (profile.acceptAccessibility()) null else Denial.Storage }, onSaved)
    }

    /** No acepta el aviso: se sigue sin protección y no se guarda nada. */
    fun declineAccessibility() = _state.update { it.copy(declined = true, error = false) }

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
