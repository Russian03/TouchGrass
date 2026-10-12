package io.github.russian03.touchgrass.core.budget

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Por qué no se ha podido hacer un cambio en el saldo. */
enum class Denial {
    /** La fecha, la hora o la zona horaria no son automáticas. */
    ManualTime,

    /** Ya hay un desbloqueo en marcha. */
    AlreadyUnlocked,

    /** No quedan suficientes minutos hoy. */
    NotEnoughBudget,

    /** Minutos fuera de rango. */
    InvalidMinutes,

    /** La app no es una de las que TouchGrass controla. */
    UnknownApp,

    /** Los primeros pasos ya se completaron: el límite solo se cambia desde Ajustes. */
    OnboardingDone,

    /** No se ha podido guardar el cambio. */
    Storage,
}

sealed interface BudgetResult {
    data class Ok(val state: BudgetState) : BudgetResult

    data class Denied(val reason: Denial) : BudgetResult
}

/**
 * Reglas del saldo diario, sin nada de Android para poder probarlas con JUnit.
 *
 * - El día va de 8:00 a 8:00 (hora local) y cada día empieza con el límite completo; lo que
 *   no se gasta se pierde.
 * - Un desbloqueo se resta entero al pedirlo y dura esos minutos de reloj, se use o no la app.
 * - Bajar el límite se aplica al momento; subirlo, a partir del mes siguiente.
 * - Nada que dependa de la hora de pared se hace sin hora automática: así no se puede saltar
 *   al día o al mes siguiente cambiando la hora del móvil.
 */
object BudgetPolicy {

    val DAY_START: LocalTime = LocalTime.of(8, 0)
    const val MAX_LIMIT_MINUTES = 180
    private const val MS_PER_MINUTE = 60_000L

    /** Día lógico al que pertenece [now]: antes de las 8:00 aún cuenta como el día anterior. */
    fun logicalDay(now: TimeSnapshot): LocalDate {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now.wallMs), now.zone)
        return if (local.toLocalTime() < DAY_START) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** Momento (epoch ms) en que se renueva el saldo: las 8:00 del día siguiente. */
    fun nextResetMs(now: TimeSnapshot): Long =
        logicalDay(now).plusDays(1).atTime(DAY_START).atZone(now.zone).toInstant().toEpochMilli()

    /**
     * Pone el estado al día: si ha empezado otro día se renueva el saldo y, si ha empezado otro
     * mes, se aplica el límite pendiente. Sin hora automática no se toca nada. Si el reloj va
     * hacia atrás (p. ej. al viajar al oeste), tampoco.
     */
    fun rollover(state: BudgetState, now: TimeSnapshot): BudgetState {
        if (!now.autoTime) return state
        val today = logicalDay(now)
        val stored = state.day?.let(LocalDate::parse)
        var next =
            when {
                stored == null -> state.copy(day = today.toString(), usedMinutes = 0)
                today > stored -> state.copy(day = today.toString(), usedMinutes = 0)
                else -> state
            }
        val pending = next.pendingLimitMinutes
        val from = next.pendingFrom?.let(YearMonth::parse)
        if (pending != null && from != null && YearMonth.from(today) >= from) {
            next = next.copy(limitMinutes = pending, pendingLimitMinutes = null, pendingFrom = null)
        }
        return next
    }

    /** Minutos que quedan hoy, sin contar el desbloqueo en marcha (ya está restado). */
    fun remainingMinutes(state: BudgetState, now: TimeSnapshot): Int {
        val current = rollover(state, now)
        return (current.limitMinutes - current.usedMinutes).coerceAtLeast(0)
    }

    /** Lo máximo que se puede pedir ahora mismo, o 0 si no se puede pedir nada. */
    fun maxUnlockMinutes(state: BudgetState, now: TimeSnapshot): Int = when {
        !now.autoTime -> 0
        activeUnlock(state, now) != null -> 0
        else -> remainingMinutes(state, now)
    }

    /** Desbloqueo en marcha (para cualquier app), o `null` si no hay o ya ha caducado. */
    fun activeUnlock(state: BudgetState, now: TimeSnapshot): UnlockSession? =
        state.unlock?.takeIf { unlockRemainingMs(it, now) > 0 }

    /** Milisegundos que le quedan a [session]. */
    fun unlockRemainingMs(session: UnlockSession, now: TimeSnapshot): Long {
        val duration = session.minutes * MS_PER_MINUTE
        // Mismo arranque: el reloj monotónico no se puede tocar. Si el contador de arranques no
        // está disponible, que el monotónico vaya hacia atrás también delata un reinicio.
        val sameBoot = now.bootCount == session.bootCount && now.elapsedMs >= session.startElapsedMs
        val remaining =
            when {
                sameBoot -> session.startElapsedMs + duration - now.elapsedMs
                // Tras reiniciar solo queda el reloj de pared, y solo vale si es automático.
                now.autoTime -> session.startWallMs + duration - now.wallMs
                else -> 0
            }
        return remaining.coerceIn(0, duration)
    }

    /** Si [packageName] está desbloqueada ahora mismo. */
    fun isUnlocked(state: BudgetState, packageName: String, now: TimeSnapshot): Boolean =
        activeUnlock(state, now)?.packageName == packageName

    /** Concede [minutes] minutos de [packageName] y los resta del saldo de hoy. */
    fun requestUnlock(
        state: BudgetState,
        packageName: String,
        minutes: Int,
        allowedPackages: Set<String>,
        now: TimeSnapshot,
    ): BudgetResult {
        if (!now.autoTime) return BudgetResult.Denied(Denial.ManualTime)
        if (packageName !in allowedPackages) return BudgetResult.Denied(Denial.UnknownApp)
        if (minutes <= 0) return BudgetResult.Denied(Denial.InvalidMinutes)
        val current = rollover(state, now)
        if (activeUnlock(current, now) != null) return BudgetResult.Denied(Denial.AlreadyUnlocked)
        if (minutes > remainingMinutes(current, now)) return BudgetResult.Denied(Denial.NotEnoughBudget)
        val session =
            UnlockSession(
                packageName = packageName,
                minutes = minutes,
                startWallMs = now.wallMs,
                startElapsedMs = now.elapsedMs,
                bootCount = now.bootCount,
            )
        return BudgetResult.Ok(current.copy(usedMinutes = current.usedMinutes + minutes, unlock = session))
    }

    /** Primer límite, elegido en los primeros pasos. Después solo se cambia con [changeLimit]. */
    fun setInitialLimit(state: BudgetState, minutes: Int, now: TimeSnapshot): BudgetResult {
        if (state.onboardingDone) return BudgetResult.Denied(Denial.OnboardingDone)
        if (minutes !in 0..MAX_LIMIT_MINUTES) return BudgetResult.Denied(Denial.InvalidMinutes)
        return BudgetResult.Ok(
            rollover(state, now).copy(limitMinutes = minutes, pendingLimitMinutes = null, pendingFrom = null),
        )
    }

    /**
     * Cambia el límite desde Ajustes. Bajarlo es más estricto y se aplica al momento; subirlo
     * queda pendiente hasta el día 1 del mes que viene. Volver al límite actual cancela el cambio
     * pendiente.
     */
    fun changeLimit(state: BudgetState, minutes: Int, now: TimeSnapshot): BudgetResult {
        if (minutes !in 0..MAX_LIMIT_MINUTES) return BudgetResult.Denied(Denial.InvalidMinutes)
        val current = rollover(state, now)
        return when {
            minutes <= current.limitMinutes ->
                BudgetResult.Ok(current.copy(limitMinutes = minutes, pendingLimitMinutes = null, pendingFrom = null))
            // Con la hora manual se podría fechar el cambio en un mes ya pasado.
            !now.autoTime -> BudgetResult.Denied(Denial.ManualTime)
            else -> {
                val nextMonth = YearMonth.from(logicalDay(now)).plusMonths(1)
                BudgetResult.Ok(current.copy(pendingLimitMinutes = minutes, pendingFrom = nextMonth.toString()))
            }
        }
    }

    fun completeOnboarding(state: BudgetState): BudgetState = state.copy(onboardingDone = true)
}
