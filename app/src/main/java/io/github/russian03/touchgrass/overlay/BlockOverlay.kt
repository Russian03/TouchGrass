package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService

/**
 * Pantalla «El scroll está bloqueado» que aparece encima de una zona bloqueada, sin sacar al usuario
 * de ella. Mientras se ve, la app de debajo no suena y, cuando acaba de aparecer, tampoco recibe
 * toques. [show] recibe `onLeave`, que saca al usuario de la zona bloqueada cuando elige uno de
 * los botones, y `onSettled`, que avisa cuando la pantalla ya ha aparecido del todo.
 */
class BlockOverlay(private val service: AccessibilityService, private val silencer: AudioSilencer) {

    private val window = OverlayWindow(service)

    val isShowing: Boolean get() = window.isShowing

    fun show(onLeave: () -> Unit, onSettled: () -> Unit) {
        if (window.isShowing) return
        silencer.silence(this)
        // El botón atrás hace lo mismo que «Volver atrás».
        val back = {
            onLeave()
            hide()
        }
        window.show(blurInMs = FADE_IN_MS, onBack = back, onSettled = onSettled) {
            BlockScreen(
                fadeInMs = FADE_IN_MS,
                onBack = back,
                onOpenApp = {
                    onLeave()
                    openApp()
                },
            )
        }
    }

    /**
     * Silencia la app ya, antes de que la pausa aparezca: entre que el usuario entra en la zona bloqueada y la
     * pausa se ve pasa un momento en el que, si no, el reel se oiría. Si la pausa no llega a ponerse,
     * [hide] devuelve el sonido.
     */
    fun silenceEarly() = silencer.silence(this)

    fun hide() {
        window.hide()
        silencer.restore(this)
    }

    /**
     * Quita la pantalla pero mantiene el silencio: al apagar el móvil no debe tapar la pantalla
     * de bloqueo, y al desbloquear el reel no debe sonar antes de que la pantalla vuelva.
     */
    fun hideKeepingSilence() = window.hide(animated = false)

    /** Quita la pantalla y devuelve el sonido al momento (p. ej. si el servicio se apaga). */
    fun release() {
        window.hide(animated = false)
        silencer.restoreNow(this)
    }

    private fun openApp() {
        openTouchGrass(service)
        hide()
    }

    private companion object {
        const val FADE_IN_MS = 1_000L
    }
}
