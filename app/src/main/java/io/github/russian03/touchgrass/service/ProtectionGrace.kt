package io.github.russian03.touchgrass.service

/**
 * Margen antes de avisar de un corte: si el servicio se detiene pero sigue activado en el ajuste
 * ([ProtectionState.Stopped]), lo normal es que el sistema lo vuelva a vincular en unos instantes.
 * Hasta que lleva [STOPPED_GRACE_MS] así, se enseña como si funcionara. Quitarlo en Accesibilidad
 * ([ProtectionState.Off]) se enseña al momento.
 */
object ProtectionGrace {
    const val STOPPED_GRACE_MS = 60_000L

    /** Lo que se enseña con [state], que lleva detenido desde [stoppedSinceMs] (o `null`), a las [nowMs]. */
    fun shown(state: ProtectionState, stoppedSinceMs: Long?, nowMs: Long): ProtectionState = when {
        state != ProtectionState.Stopped -> state
        stoppedSinceMs != null && nowMs - stoppedSinceMs < STOPPED_GRACE_MS -> ProtectionState.Running
        else -> ProtectionState.Stopped
    }
}
