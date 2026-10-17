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
    fun `la racha actual cuenta hoy mientras siga siendo de poco uso`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-01"))
        // Del 1 al 4 sin pedir tiempo; el 5, hoy, aún sin nada.
        assertThat(StatsPolicy.currentStreak(state, day("2026-10-05"))).isEqualTo(5)
        // Hoy se pasa del 25 %: la racha se rompe.
        state = StatsPolicy.recordUnlock(state, day("2026-10-05"), unlock(minutes = 10), 30)
        assertThat(StatsPolicy.currentStreak(state, day("2026-10-05"))).isEqualTo(0)
        assertThat(StatsPolicy.currentStreak(state, day("2026-10-06"))).isEqualTo(1)
    }

    @Test
    fun `la mejor racha es la más larga desde el primer día`() {
        var state = StatsPolicy.start(StatsState(), day("2026-09-01"))
        // Racha del 1 al 14, se corta el 15 (saldo agotado) y vuelve a empezar.
        state = StatsPolicy.recordUnlock(state, day("2026-09-15"), unlock(minutes = 30), 30)
        assertThat(StatsPolicy.bestStreak(state, day("2026-09-20"))).isEqualTo(14)
        assertThat(StatsPolicy.currentStreak(state, day("2026-09-20"))).isEqualTo(5)
        assertThat(StatsPolicy.bestStreak(StatsState(), day("2026-09-20"))).isEqualTo(0)
    }

    @Test
    fun `quitar la protección corta la racha`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-01"))
        state = state.copy(days = state.days + ("2026-10-03" to DayStats(protectionOff = true)))
        assertThat(StatsPolicy.currentStreak(state, day("2026-10-04"))).isEqualTo(1)
        assertThat(StatsPolicy.bestStreak(state, day("2026-10-04"))).isEqualTo(2)
    }

    @Test
    fun `el calendario tiene la cara de cada día del mes y el recuento de los pasados`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-03"))
        state = StatsPolicy.recordUnlock(state, day("2026-10-03"), unlock(minutes = 30), 30)
        state = StatsPolicy.recordUnlock(state, day("2026-10-04"), unlock(minutes = 5), 30)
        val month = StatsPolicy.month(state, oct, day("2026-10-06"))
        assertThat(month.calendar).hasSize(31)
        assertThat(month.calendar[day("2026-10-01")]).isEqualTo(DayFace.Untracked)
        assertThat(month.calendar[day("2026-10-03")]).isEqualTo(DayFace.Sad)
        assertThat(month.calendar[day("2026-10-04")]).isEqualTo(DayFace.Happy)
        assertThat(month.calendar[day("2026-10-07")]).isNull()
        // Hoy (el 6) aún no cuenta; los días sin empezar, tampoco.
        assertThat(month.faces).containsExactly(DayFace.Sad, 1, DayFace.Happy, 1, DayFace.Free, 1)
    }

    @Test
    fun `los bloqueos se cuentan día a día hasta hoy`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-01"))
        state = StatsPolicy.recordBlock(state, day("2026-10-02"), IG)
        state = StatsPolicy.recordBlock(state, day("2026-10-02"), TT)
        state = StatsPolicy.recordBlock(state, day("2026-10-04"), IG)
        val month = StatsPolicy.month(state, oct, day("2026-10-04"))
        assertThat(month.blocksByDay).containsExactly(0, 2, 0, 1).inOrder()
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
    fun `se ven la semana anterior y la actual con la cara y la racha de cada día`() {
        // 2026-10-14 es miércoles: se ven del lunes 5 al domingo 18.
        var state = StatsPolicy.start(StatsState(), day("2026-10-13"))
        state = StatsPolicy.recordUnlock(state, day("2026-10-13"), unlock(minutes = 20), 30)

        val weeks = StatsPolicy.weeks(state, day("2026-10-14"))

        assertThat(weeks.map { it.day }).containsExactlyElementsIn(
            (5..18).map { day("2026-10-%02d".format(it)) },
        ).inOrder()
        val thisWeek = weeks.drop(StatsPolicy.WEEK_DAYS)
        assertThat(thisWeek.map { it.face }).containsExactly(
            DayFace.Untracked, // lunes, antes de empezar
            DayFace.Neutral, // martes, 20 de 30 min
            DayFace.Free, // hoy, sin pedir tiempo
            DayFace.Untracked, // jueves a domingo aún no han llegado
            DayFace.Untracked,
            DayFace.Untracked,
            DayFace.Untracked,
        ).inOrder()
        assertThat(weeks.take(StatsPolicy.WEEK_DAYS).map { it.face }.toSet()).containsExactly(DayFace.Untracked)
        assertThat(thisWeek[1].unlockedMinutes).isEqualTo(20)
        assertThat(thisWeek[1].limitMinutes).isEqualTo(30)
        assertThat(thisWeek[2].streak).isEqualTo(1)
    }

    @Test
    fun `el domingo la última semana termina hoy`() {
        val weeks = StatsPolicy.weeks(StatsState(), day("2026-10-18"))
        assertThat(weeks.first().day).isEqualTo(day("2026-10-05"))
        assertThat(weeks.last().day).isEqualTo(day("2026-10-18"))
    }

    @Test
    fun `sin estadísticas ningún día está contado`() {
        val weeks = StatsPolicy.weeks(StatsState(), day("2026-10-12"))
        assertThat(weeks).hasSize(StatsPolicy.WEEK_DAYS * StatsPolicy.WEEKS_SHOWN)
        assertThat(weeks.map { it.face }.toSet()).containsExactly(DayFace.Untracked)
    }

    @Test
    fun `la racha de poco uso mejora la cara`() {
        // Un día de poco uso (5 de 30 min) y luego días sin pedir tiempo.
        var state = StatsPolicy.start(StatsState(), day("2026-10-01"))
        state = StatsPolicy.recordUnlock(state, day("2026-10-01"), unlock(minutes = 5), 30)
        assertThat(StatsPolicy.faceAt(state, day("2026-10-01"))).isEqualTo(DayFace.Happy)
        assertThat(StatsPolicy.faceAt(state, day("2026-10-02"))).isEqualTo(DayFace.Free)
        assertThat(StatsPolicy.faceAt(state, day("2026-10-03"))).isEqualTo(DayFace.Cool)
        assertThat(StatsPolicy.faceAt(state, day("2026-10-07"))).isEqualTo(DayFace.Legend)
        assertThat(StatsPolicy.faceAt(state, day("2026-10-14"))).isEqualTo(DayFace.Mythic)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-14"))).isEqualTo(14)
    }

    @Test
    fun `un día de mucho uso corta la racha`() {
        var state = StatsPolicy.start(StatsState(), day("2026-10-01"))
        state = StatsPolicy.recordUnlock(state, day("2026-10-05"), unlock(minutes = 20), 30)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-04"))).isEqualTo(4)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-05"))).isEqualTo(0)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-06"))).isEqualTo(1)
    }

    @Test
    fun `sin reclamar nunca solo queda ayer`() {
        assertThat(StatsPolicy.unclaimedDays(started, day("2026-10-14"))).containsExactly(day("2026-10-13"))
    }

    @Test
    fun `quedan los días desde el último reclamado hasta ayer`() {
        val state = StatsPolicy.markClaimed(started, day("2026-10-10"))
        assertThat(StatsPolicy.unclaimedDays(state, day("2026-10-14")))
            .containsExactly(day("2026-10-11"), day("2026-10-12"), day("2026-10-13")).inOrder()
        // Ya reclamado ayer, no queda nada; y reclamar uno anterior no hace retroceder.
        val done = StatsPolicy.markClaimed(state, day("2026-10-13"))
        assertThat(StatsPolicy.unclaimedDays(done, day("2026-10-14"))).isEmpty()
        assertThat(StatsPolicy.markClaimed(done, day("2026-10-11")).lastDayClaimed).isEqualTo("2026-10-13")
    }

    @Test
    fun `el primer día no hay nada que reclamar`() {
        val state = StatsPolicy.start(StatsState(), day("2026-10-14"))
        assertThat(StatsPolicy.unclaimedDays(state, day("2026-10-14"))).isEmpty()
        assertThat(StatsPolicy.unclaimedDays(StatsState(), day("2026-10-14"))).isEmpty()
    }

    @Test
    fun `no se reclaman días de antes del primero ni fuera de «Mi semana»`() {
        val fresh = StatsPolicy.markClaimed(StatsPolicy.start(StatsState(), day("2026-10-12")), day("2026-10-01"))
        assertThat(StatsPolicy.unclaimedDays(fresh, day("2026-10-14")))
            .containsExactly(day("2026-10-12"), day("2026-10-13")).inOrder()
        // El miércoles 14, la tira empieza el lunes 5.
        val old = StatsPolicy.markClaimed(StatsPolicy.start(StatsState(), day("2026-09-01")), day("2026-09-10"))
        assertThat(StatsPolicy.unclaimedDays(old, day("2026-10-14")).first()).isEqualTo(day("2026-10-05"))
    }

    @Test
    fun `quitar la protección marca los días hasta que vuelve`() {
        var state = StatsPolicy.markProtectionOff(started, day("2026-10-03"))
        assertThat(state.protectionOffSince).isEqualTo("2026-10-03")
        // Volver a ver que no funciona no cambia desde cuándo.
        state = StatsPolicy.markProtectionOff(state, day("2026-10-04"))
        assertThat(state.protectionOffSince).isEqualTo("2026-10-03")
        // Mientras sigue quitada, esos días ya cuentan sin protección.
        assertThat(StatsPolicy.faceAt(state, day("2026-10-05"))).isEqualTo(DayFace.Disabled)
        state = StatsPolicy.markProtectionOn(state, day("2026-10-05"))
        assertThat(state.protectionOffSince).isNull()
        assertThat(state.days.filterValues { it.protectionOff }.keys)
            .containsExactly("2026-10-03", "2026-10-04", "2026-10-05")
        assertThat(StatsPolicy.faceAt(state, day("2026-10-06"))).isNotEqualTo(DayFace.Disabled)
        // Volver sin haberla quitado no cambia nada.
        assertThat(StatsPolicy.markProtectionOn(state, day("2026-10-07"))).isEqualTo(state)
    }

    @Test
    fun `sin haber empezado quitar la protección no marca nada`() {
        assertThat(StatsPolicy.markProtectionOff(StatsState(), day("2026-10-03"))).isEqualTo(StatsState())
    }

    @Test
    fun `un día sin protección sale triste y corta la racha`() {
        var state = StatsPolicy.markProtectionOff(started, day("2026-10-03"))
        state = StatsPolicy.markProtectionOn(state, day("2026-10-03"))
        assertThat(StatsPolicy.faceAt(state, day("2026-10-03"))).isEqualTo(DayFace.Disabled)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-02"))).isEqualTo(2)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-03"))).isEqualTo(0)
        assertThat(StatsPolicy.streakAt(state, day("2026-10-04"))).isEqualTo(1)
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val TT = "com.zhiliaoapp.musically"
    }
}
