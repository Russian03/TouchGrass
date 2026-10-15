package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import io.github.russian03.touchgrass.MainActivity

/**
 * Pantalla "No hay scroll" que aparece encima de una zona bloqueada, sin sacar al usuario
 * de ella. Mientras se ve, la app de debajo no recibe toques ni suena. [show] recibe la app
 * bloqueada (para pedir tiempo para ella) y `onLeave`, que saca al usuario de la zona
 * bloqueada cuando elige uno de los botones.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val window = OverlayWindow(service)
    private val silencer = AudioSilencer(service)

    val isShowing: Boolean get() = window.isShowing

    fun show(packageName: String, onLeave: () -> Unit) {
        if (window.isShowing) return
        silencer.silence()
        val blurred = window.blursBehind
        window.show(blurInMs = FADE_IN_MS) {
            BlockScreen(
                blurredBehind = blurred,
                fadeInMs = FADE_IN_MS,
                onBack = {
                    onLeave()
                    hide()
                },
                onOpenApp = {
                    onLeave()
                    openApp()
                },
            )
        }
    }

    fun hide() {
        window.hide()
        silencer.restore()
    }

    /**
     * Quita la pantalla pero mantiene el silencio: al apagar el móvil no debe tapar la pantalla
     * de bloqueo, y al desbloquear el reel no debe sonar antes de que la pantalla vuelva.
     */
    fun hideKeepingSilence() = window.hide(animated = false)

    /** Quita la pantalla y devuelve el sonido al momento (p. ej. si el servicio se apaga). */
    fun release() {
        window.hide(animated = false)
        silencer.restoreNow()
    }

    /**
     * Abre TouchGrass en Inicio: desde ahí se pide tiempo y, la primera vez del día, se ve la
     * celebración del día anterior.
     */
    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        hide()
    }

    private companion object {
        const val FADE_IN_MS = 1_000L
    }
}
