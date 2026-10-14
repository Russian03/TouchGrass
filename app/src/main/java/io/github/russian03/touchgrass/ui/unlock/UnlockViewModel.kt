package io.github.russian03.touchgrass.ui.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.rules.RuleSource
import io.github.russian03.touchgrass.data.stats.StatsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App que se puede desbloquear, con lo que se le bloquea (p. ej. Instagram, "Reels"). */
data class UnlockApp(val packageName: String, val name: String, val blocks: String)

/** Pedir tiempo en dos pasos: primero la app y después los minutos. */
data class UnlockFormState(
    /** Apps con reglas; `null` mientras se cargan. */
    val apps: List<UnlockApp>? = null,
    /** App elegida; `null` mientras se está eligiendo. */
    val selected: UnlockApp? = null,
    /** Minutos elegidos; `null` si no se han tocado (se muestran [DEFAULT_UNLOCK_MINUTES]). */
    val draft: Int? = null,
    /** Para qué se pide; hasta elegirlo no se puede pedir. */
    val reason: UnlockReason? = null,
    /** Texto de «Otro». */
    val note: String = "",
    val busy: Boolean = false,
    /** Por qué no se ha podido desbloquear. */
    val error: Denial? = null,
    /** El desbloqueo se ha concedido: la pantalla ya puede cerrarse. */
    val granted: Boolean = false,
) {
    /** Con una sola app no hay nada que elegir y no se puede volver al primer paso. */
    val canChooseApp: Boolean get() = apps.orEmpty().size > 1

    /** Hay motivo y, si es «Otro», también texto. */
    val reasonReady: Boolean get() = reason != null && (reason != UnlockReason.Other || note.isNotBlank())

    /** Minutos que se muestran, siempre entre 1 y lo que queda hoy. */
    fun shown(remaining: Int): Int = (draft ?: DEFAULT_UNLOCK_MINUTES).coerceIn(1, remaining.coerceAtLeast(1))

    companion object {
        const val DEFAULT_UNLOCK_MINUTES = 10
    }
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val repository: BudgetRepository,
    private val stats: StatsRepository,
    private val rules: RuleSource,
) : ViewModel() {

    /** Saldo de hoy, compartido por todas las apps, o `null` mientras se carga. */
    val budget: StateFlow<BudgetSummary?> =
        repository.summary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _form = MutableStateFlow(UnlockFormState())
    val form: StateFlow<UnlockFormState> = _form.asStateFlow()

    /** App pedida antes de que terminen de cargarse las reglas. */
    private var pendingChoice: String? = null

    init {
        viewModelScope.launch {
            val apps =
                withContext(Dispatchers.IO) { rules.load() }.map { UnlockApp(it.packageName, it.name, it.blocks) }
            _form.update { state ->
                val chosen = pendingChoice?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
                state.copy(apps = apps, selected = state.selected ?: chosen ?: apps.singleOrNull())
            }
        }
    }

    /**
     * Elige la app por su paquete, p. ej. al venir de la pantalla de bloqueo. Un paquete sin
     * reglas se ignora y se sigue eligiendo a mano.
     */
    fun choose(packageName: String) {
        val apps = _form.value.apps
        if (apps == null) {
            pendingChoice = packageName
            return
        }
        val app = apps.firstOrNull { it.packageName == packageName } ?: return
        _form.update { it.copy(selected = app, draft = null, reason = null, note = "", error = null) }
    }

    /** Vuelve a elegir la app. Devuelve `false` si no hay paso anterior al que volver. */
    fun backToApps(): Boolean {
        val state = _form.value
        if (state.selected == null || !state.canChooseApp || state.busy) return false
        _form.update { it.copy(selected = null, draft = null, reason = null, note = "", error = null) }
        return true
    }

    fun onDraftChange(minutes: Int) = _form.update { it.copy(draft = minutes, error = null) }

    fun onReasonChange(reason: UnlockReason) = _form.update { it.copy(reason = reason, error = null) }

    fun onNoteChange(note: String) = _form.update { it.copy(note = note.take(StatsPolicy.NOTE_MAX_LENGTH)) }

    /**
     * Desbloquea la app elegida los minutos elegidos; se restan del saldo al momento. Después se
     * apunta en las estadísticas, con el motivo.
     */
    fun request() {
        val state = _form.value
        val remaining = budget.value?.remainingMinutes ?: return
        val app = state.selected ?: return
        val reason = state.reason ?: return
        if (state.busy || !state.reasonReady) return
        val minutes = state.shown(remaining)
        val allowed = state.apps.orEmpty().map { it.packageName }.toSet()
        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val denial = repository.requestUnlock(app.packageName, minutes, allowed)
            if (denial == null) {
                val saved = repository.state.first()
                saved.unlock?.let { session ->
                    val note = state.note.takeIf { reason == UnlockReason.Other }
                    stats.recordUnlock(session, saved.limitMinutes, reason, note)
                }
            }
            _form.update { it.copy(busy = false, error = denial, granted = denial == null) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
