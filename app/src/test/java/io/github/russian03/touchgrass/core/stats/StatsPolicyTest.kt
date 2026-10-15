package io.github.russian03.touchgrass.core.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class StatsPolicyTest {

    private fun day(text: String) = LocalDate.parse(text)

    private fun unlock(pkg: String = IG, minutes: Int = 10, start: Long = 1L, reason: UnlockReason = UnlockReason.Bus) =
        UnlockRecord(pkg, minutes, start, reason)

    private val oct = YearMonth.of(2026, 10)
    private val started = StatsPolicy.start(StatsState(), day("2026-10-01"))

    @Test
    fun `empezar solo fija el primer día una vez`() {
        assertThat(started.since).isEqualTo("2026-10-01")
        assertThat(StatsPolicy.start(started, day("2026-10-05")).since).isEqualTo("2026-10-01")
    }

    @Test
    fun `apuntar algo sin haber empezado empieza ese día`() {
        val state = StatsPolicy.recordBlock(StatsState(), day("2026-10-07"), IG)
        assertThat(state.since).isEqualTo("2026-10-07")
    }

    @Test
    fun `bloqueos y salidas se cuentan por app y día`() {
        var state = started
        repeat(3) { state = StatsPolicy.recordBlock(state, day("2026-10-02"), IG) }
        state = StatsPolicy.recordBlock(state, day("2026-10-02"), TT)
        state = StatsPolicy.recordLeave(state, day("2026-10-02"), IG)
        val stats = state.days.getValue("2026-10-02")
        assertThat(stats.blocks).containsExactly(IG, 3, TT, 1)
        assertThat(stats.leaves).containsExactly(IG, 1)
    }

    @Test
    fun `la nota se recorta y una vacía no se guarda`() {
        val long = "x".repeat(100)
        var state = StatsPolicy.recordUnlock(started, day("2026-10-02"), unlock().copy(note = "  $long  "), 30)
        assertThat(state.days.getValue("2026-10-02").unlocks.single().note).hasLength(StatsPolicy.NOTE_MAX_LENGTH)
        state = StatsPolicy.recordUnlock(started, day("2026-10-02"), unlock().copy(note = "   "), 30)
        assertThat(state.days.getValue("2026-10-02").unlocks.single().note).isNull()
    }

    @Test
    fun `los minutos devueltos se restan del desbloqueo`() {
        var state = StatsPolicy.recordUnlock(started, day("2026-10-02"), unlock(minutes = 20, start = 5L), 30)
        state = StatsPolicy.recordRefund(state, day("2026-10-02"), 5L, 10)
        val record = state.days.getValue("2026-10-02").unlocks.single()
        assertThat(record.refundedMinutes).isEqualTo(10)
        assertThat(record.netMinutes).isEqualTo(10)
        // Un desbloqueo que no está no cambia nada.
        assertThat(StatsPolicy.recordRefund(state, day("2026-10-02"), 99L, 10)).isEqualTo(state)
        assertThat(StatsPolicy.recordRefund(state, day("2026-10-03"), 5L, 10)).isEqualTo(state)
    }

    @Test
    fun `resumen del mes`() {
        var state = started
        state = StatsPolicy.recordUnlock(state, day("2026-10-02"), unlock(IG, 10, 1L, UnlockReason.Bus), 30)
        state = StatsPolicy.recordUnlock(state, day("2026-10-02"), unlock(TT, 20, 2L, UnlockReason.Eat), 30)
        state = StatsPolicy.recordUnlock(state, day("2026-10-03"), unlock(IG, 5, 3L, UnlockReason.Bus), 30)
        state = StatsPolicy.recordBlock(state, day("2026-10-03"), IG)
        state = StatsPolicy.recordLeave(state, day("2026-10-03"), IG)
        // Septiembre no cuenta: es anterior al primer día.
        state = state.copy(days = state.days + ("2026-09-30" to DayStats(blocks = mapOf(IG to 9))))

        val month = StatsPolicy.month(state, oct, day("2026-10-05"))
        assertThat(month.trackedDays).isEqualTo(5)
        assertThat(month.unlockedMinutes).isEqualTo(35)
        assertThat(month.unlockCount).isEqualTo(3)
        assertThat(month.blockCount).isEqualTo(1)
        assertThat(month.leaveCount).isEqualTo(1)
        assertThat(month.daysWithoutUnlock).isEqualTo(3)
        // El día 2 se gastaron los 30 minutos.
        assertThat(month.daysExhausted).isEqualTo(1)
        assertThat(month.daysWithinLimit).isEqualTo(4)
        assertThat(month.averageMinutes).isEqualTo(7.0)
        assertThat(month.reasons).containsExactly(UnlockReason.Bus, 2, UnlockReason.Eat, 1)
        assertThat(month.apps.map { it.packageName }).containsExactly(TT, IG).inOrder()
        assertThat(month.apps.first { it.packageName == IG }.blockCount).isEqualTo(1)
        // Los días van del más reciente al más antiguo, incluidos los que no tienen nada.
        assertThat(month.days.map { it.day.dayOfMonth }).containsExactly(5, 4, 3, 2, 1).inOrder()
    }

    @Test
    fun `un mes sin días que cuenten está vacío`() {
        val month = StatsPolicy.month(started, YearMonth.of(2026, 9), day("2026-10-05"))
        assertThat(month.trackedDays).isEqualTo(0)
        assertThat(month.days).isEmpty()
        assertThat(month.averageMinutes).isEqualTo(0.0)
    }

    @Test
    fun `meses desde el primero hasta hoy`() {
        val state = StatsPolicy.start(StatsState(), day("2026-08-20"))
        assertThat(StatsPolicy.months(state, day("2026-10-05")))
            .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9), oct).inOrder()
        assertThat(StatsPolicy.months(StatsState(), day("2026-10-05"))).containsExactly(oct)
    }

    @Test
    fun `la comparación incluye los meses anteriores con datos, del más antiguo al actual`() {
        var state = StatsPolicy.start(StatsState(), day("2026-08-20"))
        state = StatsPolicy.recordUnlock(state, day("2026-08-21"), unlock(minutes = 60, start = 1L), 60)
        state = StatsPolicy.recordUnlock(state, day("2026-09-21"), unlock(minutes = 30, start = 2L), 60)
        val months = StatsPolicy.comparison(state, oct, day("2026-10-05"))
        assertThat(months.map { it.month }).containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9), oct).inOrder()
        // Agosto solo cuenta desde el día 20: 12 días.
        assertThat(months.first().averageMinutes).isEqualTo(5.0)
        assertThat(months[1].averageMinutes).isEqualTo(1.0)
    }

    @Test
    fun `el resumen del mes anterior queda pendiente hasta abrirlo`() {
        val state = StatsPolicy.start(StatsState(), day("2026-09-10"))
        // A finales de septiembre aún no hay resumen.
        assertThat(StatsPolicy.pendingSummary(state, day("2026-09-30"))).isNull()
        assertThat(StatsPolicy.pendingSummary(state, day("2026-10-01"))).isEqualTo(YearMonth.of(2026, 9))
        val seen = StatsPolicy.markSeen(state, YearMonth.of(2026, 9))
        assertThat(StatsPolicy.pendingSummary(seen, day("2026-10-20"))).isNull()
        // Ver un mes anterior no hace olvidar que ya se vio uno posterior.
        assertThat(StatsPolicy.markSeen(seen, YearMonth.of(2026, 8)).lastMonthSeen).isEqualTo("2026-09")
    }

    @Test
    fun `sin datos del mes anterior no hay resumen ni aviso`() {
        assertThat(StatsPolicy.pendingSummary(started, day("2026-10-20"))).isNull()
        assertThat(StatsPolicy.pendingNotification(started, day("2026-10-20"))).isNull()
    }

    @Test
    fun `el aviso del resumen se envía una vez por mes`() {
        val state = StatsPolicy.start(StatsState(), day("2026-09-10"))
        assertThat(StatsPolicy.pendingNotification(state, day("2026-10-01"))).isEqualTo(YearMonth.of(2026, 9))
        val notified = StatsPolicy.markNotified(state, YearMonth.of(2026, 9))
        assertThat(StatsPolicy.pendingNotification(notified, day("2026-10-02"))).isNull()
        assertThat(StatsPolicy.pendingNotification(notified, day("2026-11-01"))).isEqualTo(oct)
    }

    @Test
    fun `se borran los días de hace más de dos años`() {
        var state = StatsPolicy.start(StatsState(), day("2024-01-15"))
        state = StatsPolicy.recordBlock(state, day("2024-01-15"), IG)
        state = StatsPolicy.recordBlock(state, day("2026-10-02"), IG)
        assertThat(state.days.keys).containsExactly("2026-10-02")
        assertThat(state.since).isEqualTo("2024-11-01")
    }

    @Test
    fun `la semana son los últimos siete días con lo que pasó en cada uno`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-08"))
        state = StatsPolicy.recordUnlock(state, day("2026-10-09"), unlock(minutes = 10), 30)
        state = StatsPolicy.recordUnlock(state, day("2026-10-10"), unlock(minutes = 30), 30)
        state = StatsPolicy.recordBlock(state, day("2026-10-11"), IG)

        val week = StatsPolicy.week(state, day("2026-10-12"))

        assertThat(week.map { it.day }).containsExactlyElementsIn(
            (6 downTo 0).map { day("2026-10-12").minusDays(it.toLong()) },
        ).inOrder()
        assertThat(week.map { it.mark }).containsExactly(
            DayMark.Untracked,
            DayMark.Untracked,
            DayMark.Free,
            DayMark.Unlocked,
            DayMark.Exhausted,
            DayMark.Free,
            DayMark.Free,
        ).inOrder()
        assertThat(week[3].unlockedMinutes).isEqualTo(10)
    }

    @Test
    fun `sin estadísticas toda la semana está sin contar`() {
        val week = StatsPolicy.week(StatsState(), day("2026-10-12"))
        assertThat(week).hasSize(StatsPolicy.WEEK_DAYS)
        assertThat(week.map { it.mark }.toSet()).containsExactly(DayMark.Untracked)
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val TT = "com.zhiliaoapp.musically"
    }
}
