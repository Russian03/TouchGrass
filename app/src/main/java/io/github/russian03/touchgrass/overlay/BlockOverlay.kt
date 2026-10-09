package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import io.github.russian03.touchgrass.MainActivity

/** Pantalla completa "Aquí no hay Reels" que se muestra tras sacar al usuario de una zona bloqueada. */
class BlockOverlay(private val service: AccessibilityService) {

    private val window = OverlayWindow(service)

    val isShowing: Boolean get() = window.isShowing

    fun show(reason: String) {
        window.show { BlockScreen(reason, onDismiss = ::hide, onOpenApp = ::openApp) }
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        hide()
    }

    fun hide() = window.hide()
}
