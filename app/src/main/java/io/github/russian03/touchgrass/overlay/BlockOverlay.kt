package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.russian03.touchgrass.MainActivity

/**
 * Pantalla completa dibujada por encima de Instagram. Usa una ventana de tipo
 * TYPE_ACCESSIBILITY_OVERLAY, que solo puede crear un servicio de accesibilidad,
 * así que no requiere el permiso "mostrar sobre otras apps".
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(reason: String) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView =
            ComposeView(service).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent { BlockScreen(reason, onDismiss = ::hide, onOpenApp = ::openApp) }
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )

        windowManager.addView(composeView, params)
        view = composeView
        lifecycleOwner = owner
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        hide()
    }

    fun hide() {
        view?.let(windowManager::removeView)
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
    }
}
