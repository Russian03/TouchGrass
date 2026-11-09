package io.github.russian03.touchgrass.core.blocking

/**
 * Límite del atrás silencioso de las reglas `silentBack`: uno cada [COOLDOWN_MS] y, como mucho,
 * [MAX_IN_BURST] seguidos. Si la app no cierra la vista previa, no hay bucle de atrás: se deja
 * de intentar hasta que la pantalla quede libre ([reset]). El tiempo (elapsedRealtime) entra como `Long`.
 */
class SilentBackGuard {
    private var count = 0
    private var lastAtMs = Long.MIN_VALUE / 2

    /** Se han gastado todos los intentos de la racha. */
    val isExhausted: Boolean get() = count >= MAX_IN_BURST

    /** Lo que falta para poder volver a intentarlo (0 si ya se puede). */
    fun remainingMs(nowMs: Long): Long = (lastAtMs + COOLDOWN_MS - nowMs).coerceAtLeast(0)

    /** Pide permiso para un atrás: si lo da, lo apunta. */
    fun tryAcquire(nowMs: Long): Boolean {
        if (isExhausted || remainingMs(nowMs) > 0) return false
        count++
        lastAtMs = nowMs
        return true
    }

    /** La pantalla ya no es la de la regla: la racha empieza de cero. */
    fun reset() {
        count = 0
        lastAtMs = Long.MIN_VALUE / 2
    }

    companion object {
        const val COOLDOWN_MS = 1_000L
        const val MAX_IN_BURST = 3
    }
}
