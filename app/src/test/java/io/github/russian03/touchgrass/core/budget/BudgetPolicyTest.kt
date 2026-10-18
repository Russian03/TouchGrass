package io.github.russian03.touchgrass.core.budget

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class BudgetPolicyTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val instagram = "com.instagram.android"
    private val allowed = setOf(instagram)

    /** Instante con hora local de Madrid; [elapsed] es el reloj monotónico en minutos. */
    private fun at(dateTime: String, elapsedMin: Long = 1_000, boot: Int = 1, autoTime: Boolean = true) = TimeSnapshot(
        wallMs = LocalDateTime.parse(dateTime).atZone(madrid).toInstant().toEpochMilli(),
        elapsedMs = elapsedMin * 60_000,
        bootCount = boot,
        zone = madrid,
        autoTime = autoTime,
    )

    private fun BudgetResult.ok(): BudgetState = (this as BudgetResult.Ok).state

    private fun BudgetResult.denial(): Denial = (this as BudgetResult.Denied).reason

    private val limit30 = BudgetState(limitMinutes = 30, onboardingDone = true)

    // Día lógico

    @Test
    fun `antes de las 8 cuenta como el día anterior`() {
        assertThat(BudgetPolicy.logicalDay(at("2026-10-13T07:59"))).isEqualTo(LocalDate.parse("2026-10-12"))
        assertThat(BudgetPolicy.logicalDay(at("2026-10-13T08:00"))).isEqualTo(LocalDate.parse("2026-10-13"))
    }

    @Test
    fun `el saldo se renueva a las 8 locales aunque cambie la hora de verano`() {
        // El 25 de octubre de 2026 se atrasa el reloj en Madrid.
        val now = at("2026-10-25T03:30")
        val reset = BudgetPolicy.nextResetMs(now)
        assertThat(reset).isEqualTo(at("2026-10-25T08:00").wallMs)
    }

    // Renovación diaria y mensual

    @Test
    fun `cada día empieza con el saldo completo y lo sobrante no se acumula`() {
        val spent = limit30.copy(day = "2026-10-12", usedMinutes = 10)
        val next = BudgetPolicy.rollover(spent, at("2026-10-13T08:01"))
        assertThat(next.usedMinutes).isEqualTo(0)
        assertThat(BudgetPolicy.remainingMinutes(next, at("2026-10-13T08:01"))).isEqualTo(30)
    }

    @Test
    fun `de madrugada sigue siendo el día anterior`() {
        val spent = limit30.copy(day = "2026-10-12", usedMinutes = 30)
        assertThat(BudgetPolicy.remainingMinutes(spent, at("2026-10-13T07:30"))).isEqualTo(0)
    }

    @Test
    fun `sin hora automática el saldo no se renueva`() {
        val spent = limit30.copy(day = "2026-10-12", usedMinutes = 30)
        val now = at("2026-10-20T09:00", autoTime = false)
        assertThat(BudgetPolicy.rollover(spent, now)).isEqualTo(spent)
        assertThat(BudgetPolicy.remainingMinutes(spent, now)).isEqualTo(0)
    }

    @Test
    fun `si el reloj va hacia atrás no se renueva`() {
        val spent = limit30.copy(day = "2026-10-13", usedMinutes = 30)
        assertThat(BudgetPolicy.rollover(spent, at("2026-10-12T20:00"))).isEqualTo(spent)
    }

    @Test
    fun `el límite pendiente se aplica al empezar el mes siguiente`() {
        val state = limit30.copy(day = "2026-10-31", pendingLimitMinutes = 60, pendingFrom = "2026-11")
        // El 1 de noviembre a las 7:00 aún es 31 de octubre.
        assertThat(BudgetPolicy.rollover(state, at("2026-11-01T07:00")).limitMinutes).isEqualTo(30)
        val november = BudgetPolicy.rollover(state, at("2026-11-01T08:00"))
        assertThat(november.limitMinutes).isEqualTo(60)
        assertThat(november.pendingLimitMinutes).isNull()
        assertThat(november.pendingFrom).isNull()
    }

    // Cambiar el límite

    @Test
    fun `bajar el límite se aplica al momento`() {
        val state = BudgetPolicy.changeLimit(limit30, 20, at("2026-10-13T10:00")).ok()
        assertThat(state.limitMinutes).isEqualTo(20)
        assertThat(state.pendingLimitMinutes).isNull()
    }

    @Test
    fun `subir el límite espera al mes siguiente`() {
        val state = BudgetPolicy.changeLimit(limit30, 45, at("2026-10-13T10:00")).ok()
        assertThat(state.limitMinutes).isEqualTo(30)
        assertThat(state.pendingLimitMinutes).isEqualTo(45)
        assertThat(state.pendingFrom).isEqualTo("2026-11")
    }

    @Test
    fun `en diciembre el cambio pendiente pasa al año siguiente`() {
        val state = BudgetPolicy.changeLimit(limit30, 45, at("2026-12-20T10:00")).ok()
        assertThat(state.pendingFrom).isEqualTo("2027-01")
    }

    @Test
    fun `volver al límite actual cancela el cambio pendiente`() {
        val pending = limit30.copy(pendingLimitMinutes = 45, pendingFrom = "2026-11")
        val state = BudgetPolicy.changeLimit(pending, 30, at("2026-10-13T10:00")).ok()
        assertThat(state.limitMinutes).isEqualTo(30)
        assertThat(state.pendingLimitMinutes).isNull()
    }

    @Test
    fun `subir el límite sin hora automática no se permite`() {
        val result = BudgetPolicy.changeLimit(limit30, 45, at("2026-10-13T10:00", autoTime = false))
        assertThat(result.denial()).isEqualTo(Denial.ManualTime)
    }

    @Test
    fun `bajar el límite sin hora automática sí se permite`() {
        val result = BudgetPolicy.changeLimit(limit30, 10, at("2026-10-13T10:00", autoTime = false))
        assertThat(result.ok().limitMinutes).isEqualTo(10)
    }

    @Test
    fun `límites fuera de rango no se aceptan`() {
        val now = at("2026-10-13T10:00")
        assertThat(BudgetPolicy.changeLimit(limit30, -1, now).denial()).isEqualTo(Denial.InvalidMinutes)
        assertThat(BudgetPolicy.changeLimit(limit30, 245, now).denial()).isEqualTo(Denial.InvalidMinutes)
        // Hasta 4 h sí: subirlo queda pendiente para el mes siguiente.
        assertThat(BudgetPolicy.changeLimit(limit30, 240, now).ok().pendingLimitMinutes).isEqualTo(240)
    }

    @Test
    fun `el límite inicial solo se elige en los primeros pasos`() {
        val now = at("2026-10-13T10:00")
        val first = BudgetPolicy.setInitialLimit(BudgetState(), 40, now).ok()
        assertThat(first.limitMinutes).isEqualTo(40)
        val done = BudgetPolicy.completeOnboarding(first)
        assertThat(BudgetPolicy.setInitialLimit(done, 120, now).denial()).isEqualTo(Denial.OnboardingDone)
    }

    // Desbloqueos

    @Test
    fun `sin límite no se puede desbloquear nada`() {
        val result = BudgetPolicy.requestUnlock(BudgetState(), instagram, 10, allowed, at("2026-10-13T10:00"))
        assertThat(result.denial()).isEqualTo(Denial.NotEnoughBudget)
    }

    @Test
    fun `desbloquear resta los minutos al momento`() {
        val now = at("2026-10-13T10:00")
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, now).ok()
        assertThat(state.usedMinutes).isEqualTo(20)
        assertThat(BudgetPolicy.remainingMinutes(state, now)).isEqualTo(10)
        assertThat(isUnlocked(state, instagram, now)).isTrue()
    }

    @Test
    fun `el desbloqueo dura los minutos pedidos de reloj monotónico`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()
        assertThat(isUnlocked(state, instagram, at("2026-10-13T10:19", elapsedMin = 1_019))).isTrue()
        assertThat(isUnlocked(state, instagram, at("2026-10-13T10:20", elapsedMin = 1_020))).isFalse()
    }

    @Test
    fun `adelantar el reloj de pared no acaba ni alarga el desbloqueo`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()
        // Pared retrasada, pero el monotónico dice que ya han pasado 25 minutos.
        assertThat(isUnlocked(state, instagram, at("2026-10-13T09:00", elapsedMin = 1_025))).isFalse()
    }

    @Test
    fun `tras reiniciar se sigue el reloj de pared si es automático`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()
        assertThat(isUnlocked(state, instagram, at("2026-10-13T10:10", elapsedMin = 1, boot = 2))).isTrue()
        assertThat(
            isUnlocked(state, instagram, at("2026-10-13T10:21", elapsedMin = 2, boot = 2)),
        ).isFalse()
    }

    @Test
    fun `tras reiniciar sin hora automática el desbloqueo se acaba`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()
        val now = at("2026-10-13T10:05", elapsedMin = 1, boot = 2, autoTime = false)
        assertThat(isUnlocked(state, instagram, now)).isFalse()
    }

    @Test
    fun `tras reiniciar el reloj de pared no puede alargar el desbloqueo`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()
        // Pared muy atrasada: como mucho quedan los 20 minutos pedidos.
        val now = at("2026-10-13T08:00", elapsedMin = 1, boot = 2)
        assertThat(BudgetPolicy.unlockRemainingMs(state.unlock!!, now)).isEqualTo(20 * 60_000L)
    }

    @Test
    fun `sin contador de arranques un monotónico menor delata el reinicio`() {
        val start = at("2026-10-13T10:00", boot = -1)
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, start).ok()
        // Con el monotónico de antes quedarían ~1017 minutos; con la pared, 15.
        val now = at("2026-10-13T10:05", elapsedMin = 3, boot = -1)
        assertThat(BudgetPolicy.unlockRemainingMs(state.unlock!!, now)).isEqualTo(15 * 60_000L)
    }

    @Test
    fun `el desbloqueo es solo para la app pedida`() {
        val now = at("2026-10-13T10:00")
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 10, allowed, now).ok()
        assertThat(isUnlocked(state, "com.zhiliaoapp.musically", now)).isFalse()
    }

    @Test
    fun `no se puede pedir más de lo que queda`() {
        val now = at("2026-10-13T10:00")
        val spent = limit30.copy(day = "2026-10-13", usedMinutes = 25)
        assertThat(BudgetPolicy.remainingMinutes(spent, now)).isEqualTo(5)
        assertThat(BudgetPolicy.requestUnlock(spent, instagram, 10, allowed, now).denial())
            .isEqualTo(Denial.NotEnoughBudget)
        assertThat(BudgetPolicy.requestUnlock(spent, instagram, 5, allowed, now).ok().usedMinutes).isEqualTo(30)
    }

    @Test
    fun `con el saldo agotado no se puede pedir nada hasta las 8`() {
        val spent = limit30.copy(day = "2026-10-13", usedMinutes = 30)
        assertThat(BudgetPolicy.remainingMinutes(spent, at("2026-10-14T07:59"))).isEqualTo(0)
        assertThat(BudgetPolicy.remainingMinutes(spent, at("2026-10-14T08:00"))).isEqualTo(30)
    }

    @Test
    fun `no se puede pedir otro desbloqueo con uno en marcha`() {
        val now = at("2026-10-13T10:00")
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 10, allowed, now).ok()
        assertThat(BudgetPolicy.requestUnlock(state, instagram, 10, allowed, now).denial())
            .isEqualTo(Denial.AlreadyUnlocked)
    }

    @Test
    fun `sin hora automática no se puede desbloquear`() {
        val now = at("2026-10-13T10:00", autoTime = false)
        assertThat(BudgetPolicy.requestUnlock(limit30, instagram, 10, allowed, now).denial())
            .isEqualTo(Denial.ManualTime)
    }

    @Test
    fun `solo se desbloquean apps controladas y minutos positivos`() {
        val now = at("2026-10-13T10:00")
        assertThat(BudgetPolicy.requestUnlock(limit30, "com.example", 10, allowed, now).denial())
            .isEqualTo(Denial.UnknownApp)
        assertThat(BudgetPolicy.requestUnlock(limit30, instagram, 0, allowed, now).denial())
            .isEqualTo(Denial.InvalidMinutes)
    }

    // Terminar antes de tiempo

    private fun unlocked20() = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-13T10:00")).ok()

    @Test
    fun `se devuelve lo que queda redondeado hacia abajo a múltiplos de 5`() {
        val state = unlocked20()
        // Quedan 13 minutos: se devuelven 10.
        val now = at("2026-10-13T10:07", elapsedMin = 1_007)
        assertThat(BudgetPolicy.refundMinutes(state, now)).isEqualTo(10)
        val ended = BudgetPolicy.endUnlock(state, now).ok()
        assertThat(ended.unlock).isNull()
        assertThat(ended.usedMinutes).isEqualTo(10)
        assertThat(isUnlocked(ended, instagram, now)).isFalse()
    }

    @Test
    fun `con menos de 5 minutos no se devuelve nada`() {
        val state = unlocked20()
        val now = at("2026-10-13T10:16", elapsedMin = 1_016)
        assertThat(BudgetPolicy.refundMinutes(state, now)).isEqualTo(0)
        assertThat(BudgetPolicy.endUnlock(state, now).ok().usedMinutes).isEqualTo(20)
    }

    @Test
    fun `terminar nada más pedir devuelve todo`() {
        val now = at("2026-10-13T10:00")
        val ended = BudgetPolicy.endUnlock(unlocked20(), now).ok()
        assertThat(ended.usedMinutes).isEqualTo(0)
        assertThat(BudgetPolicy.remainingMinutes(ended, now)).isEqualTo(30)
    }

    @Test
    fun `tras las 8 no se devuelve nada al saldo nuevo`() {
        val state = BudgetPolicy.requestUnlock(limit30, instagram, 20, allowed, at("2026-10-14T07:55")).ok()
        val now = at("2026-10-14T08:00", elapsedMin = 1_005)
        assertThat(BudgetPolicy.refundMinutes(state, now)).isEqualTo(0)
        val ended = BudgetPolicy.endUnlock(state, now).ok()
        assertThat(BudgetPolicy.remainingMinutes(ended, now)).isEqualTo(30)
    }

    @Test
    fun `sin hora automática se puede terminar pero no se devuelve nada`() {
        val now = at("2026-10-13T10:00", autoTime = false)
        val ended = BudgetPolicy.endUnlock(unlocked20(), now).ok()
        assertThat(ended.unlock).isNull()
        assertThat(ended.usedMinutes).isEqualTo(20)
    }

    @Test
    fun `sin desbloqueo en marcha no hay nada que terminar`() {
        val now = at("2026-10-13T10:30", elapsedMin = 1_030)
        assertThat(BudgetPolicy.endUnlock(unlocked20(), now).denial()).isEqualTo(Denial.NotUnlocked)
        assertThat(BudgetPolicy.endUnlock(limit30, now).denial()).isEqualTo(Denial.NotUnlocked)
    }

    @Test
    fun `una sesión guardada sin día no devuelve minutos`() {
        val session = unlocked20().let { it.copy(unlock = it.unlock!!.copy(day = null)) }
        assertThat(BudgetPolicy.refundMinutes(session, at("2026-10-13T10:00"))).isEqualTo(0)
    }
}

/** Si [packageName] está desbloqueada ahora mismo (como lo comprueba el servicio). */
private fun isUnlocked(state: BudgetState, packageName: String, now: TimeSnapshot): Boolean =
    BudgetPolicy.activeUnlock(state, now)?.packageName == packageName
