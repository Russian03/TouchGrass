package io.github.russian03.touchgrass.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayMark
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.ui.common.weekdayDay
import io.github.russian03.touchgrass.ui.common.weekdayShort
import io.github.russian03.touchgrass.ui.design.DayPill
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate

/** Retraso entre píldoras al entrar, de izquierda a derecha. */
private const val PILL_STEP_MS = 40L

/**
 * «Mi semana»: siete píldoras, con hoy a la derecha (la tira empieza desplazada al final). Al
 * tocar cualquiera se abren las estadísticas.
 */
@Composable
fun WeekStrip(week: List<WeekDay>, today: LocalDate, onOpenStats: () -> Unit, modifier: Modifier = Modifier) {
    // Empieza al final: el valor se ajusta al máximo real al medir.
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(scroll),
    ) {
        week.forEachIndexed { i, day ->
            val name = weekdayDay(day.day)
            DayPill(
                number = day.day.dayOfMonth.toString(),
                weekday = weekdayShort(day.day),
                mark = day.mark,
                today = day.day == today,
                description =
                when (day.mark) {
                    DayMark.Free -> stringResource(R.string.week_day_free, name)
                    DayMark.Unlocked -> stringResource(R.string.week_day_unlocked, name, day.unlockedMinutes)
                    DayMark.Exhausted -> stringResource(R.string.week_day_exhausted, name)
                    DayMark.Untracked -> stringResource(R.string.week_day_untracked, name)
                },
                onClick = onOpenStats,
                modifier = Modifier.staggerIn(index = i, stepMs = PILL_STEP_MS, offsetY = 8.dp, fromScale = 0.8f),
            )
        }
    }
}

internal fun previewWeek(today: LocalDate = LocalDate.of(2026, 10, 15)): List<WeekDay> {
    val marks =
        listOf(
            DayMark.Untracked,
            DayMark.Free,
            DayMark.Unlocked,
            DayMark.Free,
            DayMark.Exhausted,
            DayMark.Free,
            DayMark.Free,
        )
    return marks.mapIndexed { i, mark ->
        val minutes = if (mark == DayMark.Unlocked) 15 else 0
        WeekDay(today.minusDays((6 - i).toLong()), mark, minutes)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun WeekStripPreview() {
    TouchGrassTheme { WeekStrip(previewWeek(), LocalDate.of(2026, 10, 15), {}) }
}
