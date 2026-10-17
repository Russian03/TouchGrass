package io.github.russian03.touchgrass.core.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DemoWeekTest {
    private val today = LocalDate.of(2026, 10, 15) // jueves

    @Test
    fun `ayer sale sin protección y el primer acceso lo enseña`() {
        val state = DemoWeek.fill(StatsState(since = today.toString()), today)
        assertThat(StatsPolicy.faceAt(state, today.minusDays(1))).isEqualTo(DayFace.Disabled)
        assertThat(StatsPolicy.unclaimedDays(state, today)).containsExactly(today.minusDays(1))
    }

    @Test
    fun `los días pasados llevan caras variadas y hoy no se toca`() {
        val state = DemoWeek.fill(StatsState(since = today.toString()), today)
        val faces = StatsPolicy.weeks(state, today).filter { it.day < today.minusDays(1) }.map { it.face }
        assertThat(faces).doesNotContain(DayFace.Untracked)
        assertThat(faces.toSet().size).isAtLeast(4)
        assertThat(state.days).doesNotContainKey(today.toString())
    }

    @Test
    fun `respeta los días con datos de verdad`() {
        val real = DayStats(limitMinutes = 30)
        val day = today.minusDays(3).toString()
        val state = DemoWeek.fill(StatsState(since = day, days = mapOf(day to real)), today)
        assertThat(state.days[day]).isEqualTo(real)
    }
}
