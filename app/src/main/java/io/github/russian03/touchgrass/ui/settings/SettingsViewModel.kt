package io.github.russian03.touchgrass.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.ui.common.LIMIT_RANGE
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resultado del último cambio de límite, para avisar al usuario. */
sealed interface LimitChange {
    data object Applied : LimitChange

    data object Scheduled : LimitChange

    data object Cancelled : LimitChange

    data class Failed(val reason: Denial) : LimitChange
}

data class SettingsUiState(
    val budget: BudgetSummary? = null,
    /** Límite que se está eligiendo; `null` si no se ha tocado (se muestra el actual). */
    val draft: Int? = null,
    val saving: Boolean = false,
    val result: LimitChange? = null,
) {
    val shownLimit: Int get() = draft ?: budget?.limitMinutes ?: 0
    val canSave: Boolean get() = budget != null && !saving && draft != null && draft != budget.limitMinutes
    val lowers: Boolean get() = budget != null && draft != null && draft < budget.limitMinutes
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: BudgetRepository) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    private val budget: StateFlow<BudgetSummary?> =
        repository.summary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val state: StateFlow<SettingsUiState> =
        combine(budget, local) { budget, ui -> ui.copy(budget = budget) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** Estado al momento, sin esperar a que [state] se recalcule. */
    private fun current() = local.value.copy(budget = budget.value)

    fun onDraftChange(minutes: Int) = local.update { it.copy(draft = minutes.coerceIn(LIMIT_RANGE), result = null) }

    /** Guarda el límite elegido: si es más bajo se aplica ya; si es más alto, el mes que viene. */
    fun save() {
        val current = current()
        val draft = current.draft ?: return
        if (!current.canSave) return
        change(draft) { if (current.lowers) LimitChange.Applied else LimitChange.Scheduled }
    }

    /** Anula el límite pendiente volviendo a pedir el actual. */
    fun cancelPending() {
        val limit = budget.value?.limitMinutes ?: return
        change(limit) { LimitChange.Cancelled }
    }

    private fun change(minutes: Int, onSuccess: () -> LimitChange) {
        if (local.value.saving) return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val denial = repository.changeLimit(minutes)
            local.update {
                it.copy(
                    saving = false,
                    draft = if (denial == null) null else it.draft,
                    result = denial?.let(LimitChange::Failed) ?: onSuccess(),
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
