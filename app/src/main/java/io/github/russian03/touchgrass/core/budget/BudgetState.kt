package io.github.russian03.touchgrass.core.budget

import kotlinx.serialization.Serializable

/**
 * Todo lo que se guarda del saldo diario. El estado por defecto no tiene límite, así que no
 * deja desbloquear nada: si el fichero se pierde o se corrompe, la app sigue bloqueando.
 *
 * Las fechas van en ISO (`2026-10-13`, `2026-11`) para que el fichero sea legible y estable.
 */
@Serializable
data class BudgetState(
    /** Minutos al día que se pueden desbloquear este mes. */
    val limitMinutes: Int = 0,
    /** Límite más alto pedido en Ajustes; se aplica a partir de [pendingFrom]. */
    val pendingLimitMinutes: Int? = null,
    /** Mes (`YearMonth`) desde el que se aplica [pendingLimitMinutes]. */
    val pendingFrom: String? = null,
    /** Día lógico (de 8:00 a 8:00) al que corresponde [usedMinutes], o `null` si aún no hay. */
    val day: String? = null,
    /** Minutos ya gastados en desbloqueos durante [day]. */
    val usedMinutes: Int = 0,
    /** Último desbloqueo concedido; puede estar ya caducado. */
    val unlock: UnlockSession? = null,
    val onboardingDone: Boolean = false,
)

/**
 * Desbloqueo de [minutes] minutos para la app [packageName]. Se guarda el inicio con los dos
 * relojes: el monotónico ([startElapsedMs], no se puede cambiar a mano pero vuelve a cero al
 * reiniciar el móvil) y el de pared ([startWallMs]), que solo se usa tras un reinicio.
 */
@Serializable
data class UnlockSession(
    val packageName: String,
    val minutes: Int,
    val startWallMs: Long,
    val startElapsedMs: Long,
    val bootCount: Int,
)
