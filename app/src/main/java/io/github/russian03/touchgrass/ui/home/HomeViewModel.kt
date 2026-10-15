package io.github.russian03.touchgrass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.profile.ProfileRepository
import io.github.russian03.touchgrass.data.rules.RuleSource
import io.github.russian03.touchgrass.data.stats.StatsRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
class HomeViewModel @Inject constructor(
    private val repository: BudgetRepository,
    private val stats: StatsRepository,
    private val rules: RuleSource,
    profile: ProfileRepository,
) : ViewModel() {

    /** Nombre para el saludo de la cabecera, o `null` si no se ha dicho. */
    val name: StateFlow<String?> =
        profile.profile.map { it.name }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Semilla de la frase de la cabecera: cambia cada vez que se abre Inicio, para darle vida. */
    val quoteSeed: Int = Random.nextInt(Int.MAX_VALUE)

    /** Saldo de hoy, o `null` mientras se carga. */
    val budget: StateFlow<BudgetSummary?> =
        repository.summary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Nombre de cada app por paquete, para decir cuál está desbloqueada. */
    val appNames: StateFlow<Map<String, String>> =
        flow { emit(withContext(Dispatchers.IO) { rules.load() }.associate { it.packageName to it.name }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Mes cuyo resumen está listo y aún no se ha abierto, o `null`. */
    val pendingSummary: StateFlow<YearMonth?> =
        stats.state
            .map { StatsPolicy.pendingSummary(it, stats.today()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Día lógico de hoy (de 8:00 a 8:00), para saber qué días de la semana ya han pasado. */
    fun today(): LocalDate = stats.today()

    /** La semana anterior y la actual, de lunes a domingo, para «Mi semana». */
    val week: StateFlow<List<WeekDay>> =
        stats.state
            .map { StatsPolicy.weeks(it, stats.today()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _unlock = MutableStateFlow(UnlockUiState())
    val unlock: StateFlow<UnlockUiState> = _unlock.asStateFlow()

    /** Termina el desbloqueo y devuelve los minutos que sobran, en múltiplos de 5. */
    fun endUnlock() {
        if (_unlock.value.busy) return
        _unlock.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = repository.endUnlock()
            result.session?.let { stats.recordRefund(it, result.refundMinutes) }
            _unlock.update { it.copy(busy = false, error = result.denial) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
