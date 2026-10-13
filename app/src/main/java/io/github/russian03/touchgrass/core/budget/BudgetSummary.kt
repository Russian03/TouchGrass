package io.github.russian03.touchgrass.core.budget

import java.time.YearMonth

/** Lo que la interfaz muestra del saldo en un instante, ya puesto al día. */
data class BudgetSummary(
    val limitMinutes: Int,
    /** Minutos gastados hoy, como mucho el límite (si se ha bajado el límite a media jornada). */
    val usedMinutes: Int,
    val remainingMinutes: Int,
    val pendingLimitMinutes: Int?,
    val pendingFrom: YearMonth?,
    val autoTime: Boolean,
    /** Momento (epoch ms) en que se renueva el saldo. */
    val nextResetMs: Long,
    val onboardingDone: Boolean,
    /** Desbloqueo en marcha, o `null`. */
    val unlock: UnlockSession?,
    val unlockRemainingMs: Long,
    /** Minutos que se devolverían si se terminara ya el desbloqueo. */
    val refundMinutes: Int,
) {
    companion object {
        fun of(state: BudgetState, now: TimeSnapshot): BudgetSummary {
            val current = BudgetPolicy.rollover(state, now)
            val unlock = BudgetPolicy.activeUnlock(current, now)
            return BudgetSummary(
                limitMinutes = current.limitMinutes,
                usedMinutes = current.usedMinutes.coerceAtMost(current.limitMinutes),
                remainingMinutes = BudgetPolicy.remainingMinutes(current, now),
                pendingLimitMinutes = current.pendingLimitMinutes,
                pendingFrom = current.pendingFrom?.let(YearMonth::parse),
                autoTime = now.autoTime,
                nextResetMs = BudgetPolicy.nextResetMs(now),
                onboardingDone = current.onboardingDone,
                unlock = unlock,
                unlockRemainingMs = unlock?.let { BudgetPolicy.unlockRemainingMs(it, now) } ?: 0,
                refundMinutes = BudgetPolicy.refundMinutes(current, now),
            )
        }
    }
}
