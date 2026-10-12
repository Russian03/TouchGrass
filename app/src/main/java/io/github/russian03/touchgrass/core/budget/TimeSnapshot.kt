package io.github.russian03.touchgrass.core.budget

import java.time.ZoneId

/**
 * Lectura de los relojes en un instante. [autoTime] indica que la fecha, la hora y la zona
 * horaria son automáticas: si no lo son, el usuario podría adelantar el reloj para renovar el
 * saldo, así que nada que dependa de la hora de pared se fía de ella.
 */
data class TimeSnapshot(
    val wallMs: Long,
    val elapsedMs: Long,
    /** Número de arranques del móvil, o -1 si el sistema no lo da. */
    val bootCount: Int,
    val zone: ZoneId,
    val autoTime: Boolean,
)

fun interface TimeSource {
    fun now(): TimeSnapshot
}
