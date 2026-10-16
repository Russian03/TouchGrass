package io.github.russian03.touchgrass.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.BuildConfig
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.stats.DayFace
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

/**
 * Días que aún no se han enseñado al abrir la app, del más antiguo a ayer ([days], vacío si no
 * queda ninguno), y cómo fue ayer, para la frase. [disabledDay] es el último de esos días en que se
 * quitó la protección, o `null`.
 */
data class ClaimState(val days: List<LocalDate>, val yesterday: WeekDay?, val disabledDay: LocalDate?)

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

    /** Días por enseñar en el primer acceso del día, o `null` mientras se carga. */
    val claim: StateFlow<ClaimState?> =
        stats.state
            .map { state ->
                val today = stats.today()
                val days =
                    StatsPolicy.unclaimedDays(state, today).ifEmpty {
                        // En la app debug, ayer se vuelve a enseñar siempre, para ver la animación.
                        val yesterday = today.minusDays(1)
                        val since = state.since?.let(LocalDate::parse)
                        if (BuildConfig.DEBUG && since != null && since <= yesterday) listOf(yesterday) else emptyList()
                    }
                val faces = days.associateWith { StatsPolicy.faceAt(state, it) }
                ClaimState(
                    days = days,
                    yesterday = StatsPolicy.weeks(state, today).firstOrNull { it.day == today.minusDays(1) },
                    disabledDay = days.lastOrNull { faces[it] == DayFace.Disabled },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Ya se han enseñado los días que faltaban. En la app debug no se guarda, para que la animación
     * salga igual cada vez que se vuelve a la app.
     */
    fun claim() {
        if (BuildConfig.DEBUG) return
        viewModelScope.launch { stats.markClaimed() }
    }

    /** La protección no funciona: hoy cuenta como día sin protección. */
    fun protectionOff() {
        viewModelScope.launch { stats.markProtectionOff() }
    }

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
