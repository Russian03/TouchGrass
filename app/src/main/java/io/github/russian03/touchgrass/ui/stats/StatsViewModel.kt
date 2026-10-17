package io.github.russian03.touchgrass.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.core.stats.MonthStats
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.data.rules.RuleSource
import io.github.russian03.touchgrass.data.stats.StatsRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUiState(
    val today: LocalDate,
    /** Meses con estadísticas, del primero al actual. */
    val months: List<YearMonth>,
    val month: MonthStats,
    /** El mes que se mira y los anteriores con datos, del más antiguo al más reciente. */
    val comparison: List<MonthStats>,
    /** Nombre de cada app por paquete. */
    val appNames: Map<String, String>,
    /** Racha de días de poco uso que llega hasta hoy (no depende del mes que se mira). */
    val currentStreak: Int = 0,
    /** La racha más larga desde que hay estadísticas. */
    val bestStreak: Int = 0,
) {
    val previous: YearMonth? get() = months.lastOrNull { it < month.month }
    val next: YearMonth? get() = months.firstOrNull { it > month.month }
}

@HiltViewModel
class StatsViewModel @Inject constructor(private val stats: StatsRepository, rules: RuleSource) : ViewModel() {

    private val selected = MutableStateFlow<YearMonth?>(null)

    private val appNames =
        flow { emit(withContext(Dispatchers.IO) { rules.load() }.associate { it.packageName to it.name }) }

    /** `null` mientras se carga. */
    val state: StateFlow<StatsUiState?> =
        combine(stats.state, selected, appNames) { state, chosen, names ->
            val today = stats.today()
            val months = StatsPolicy.months(state, today)
            val month = chosen?.takeIf { it in months } ?: months.last()
            StatsUiState(
                today = today,
                months = months,
                month = StatsPolicy.month(state, month, today),
                comparison = StatsPolicy.comparison(state, month, today),
                appNames = names,
                currentStreak = StatsPolicy.currentStreak(state, today),
                bestStreak = StatsPolicy.bestStreak(state, today),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch { stats.start() }
    }

    /** Muestra [month]. */
    fun show(month: YearMonth) {
        selected.value = month
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
