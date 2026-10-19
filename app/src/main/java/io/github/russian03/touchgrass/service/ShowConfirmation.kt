package io.github.russian03.touchgrass.service

/**
 * Antes de poner la pantalla de bloqueo, la misma regla tiene que cumplirse en dos miradas
 * separadas al menos [confirmMs]. Al cambiar de pestaña muy rápido, Instagram tarda un instante en
 * actualizar lo que se lee de su pantalla: sin esta espera, la pausa aparecía un momento sobre la
 * pestaña nueva.
 */
class ShowConfirmation(private val confirmMs: Long = CONFIRM_MS) {

    private var candidate: String? = null
    private var sinceMs = 0L

    /**
     * La regla [key] se cumple a las [nowMs]. Devuelve `true` si ya se cumplía desde hace al menos
     * [confirmMs]; si no, empieza a contar (o sigue esperando).
     */
    fun onMatch(key: String, nowMs: Long): Boolean {
        if (candidate != key) {
            candidate = key
            sinceMs = nowMs
            return false
        }
        return nowMs - sinceMs >= confirmMs
    }

    /** Lo que falta, desde [nowMs], para poder confirmar la regla que se está esperando. */
    fun remainingMs(nowMs: Long): Long = (sinceMs + confirmMs - nowMs).coerceAtLeast(0)

    /** Ya no se cumple ninguna regla (o se ha puesto la pantalla): se olvida la espera. */
    fun reset() {
        candidate = null
    }

    companion object {
        const val CONFIRM_MS = 120L
    }
}
