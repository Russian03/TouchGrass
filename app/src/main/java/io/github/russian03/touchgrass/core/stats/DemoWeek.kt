package io.github.russian03.touchgrass.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Semana de muestra para la app debug: rellena «Mi semana» con caras variadas en los días pasados
 * sin nada apuntado y deja ayer como día sin protección, para ver el primer acceso del día con la
 * cara triste. No se guarda: solo cambia lo que se enseña en Inicio.
 */
object DemoWeek {
    private const val DEMO_LIMIT = 30
    private const val DEMO_APP = "com.instagram.android"

    /** Minutos gastados de [DEMO_LIMIT] en cada día de muestra, en orden: dan caras distintas. */
    private val DemoMinutes = listOf(0, 5, 12, 20, 26, 30, 3, 0, 8, 15, 22, 30)

    fun fill(state: StatsState, today: LocalDate): StatsState {
        val firstShown =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(StatsPolicy.WEEKS_SHOWN - 1L)
        val yesterday = today.minusDays(1)
        val days = state.days.toMutableMap()
        generateSequence(firstShown) { it.plusDays(1) }
            .takeWhile { it < yesterday }
            .forEachIndexed { i, day ->
                val key = day.toString()
                if (key !in days) days[key] = demoDay(DemoMinutes[i % DemoMinutes.size], day)
            }
        val yesterdayKey = yesterday.toString()
        days[yesterdayKey] = (days[yesterdayKey] ?: DayStats()).copy(protectionOff = true)
        val since = state.since?.let(LocalDate::parse)?.let { minOf(it, firstShown) } ?: firstShown
        return state.copy(since = since.toString(), days = days)
    }

    private fun demoDay(minutes: Int, day: LocalDate): DayStats = if (minutes == 0) {
        DayStats()
    } else {
        DayStats(
            limitMinutes = DEMO_LIMIT,
            unlocks =
            listOf(
                UnlockRecord(
                    packageName = DEMO_APP,
                    minutes = minutes,
                    startWallMs = day.toEpochDay() * MS_PER_DAY,
                    reason = UnlockReason.Bath,
                ),
            ),
        )
    }

    private const val MS_PER_DAY = 86_400_000L
}
