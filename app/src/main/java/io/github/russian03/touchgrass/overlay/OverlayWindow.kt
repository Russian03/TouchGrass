package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Ventana Compose de tipo TYPE_ACCESSIBILITY_OVERLAY. Solo puede crearla un servicio de
 * accesibilidad, así que no requiere el permiso "mostrar sobre otras apps".
 */
internal class OverlayWindow(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    /**
     * Muestra [content] en [bounds] (coordenadas de pantalla) o a pantalla completa si es `null`.
     * Con [focusable] a false, el teclado y el botón atrás siguen llegando a la app de debajo.
     */
    fun show(bounds: Rect? = null, focusable: Boolean = true, content: @Composable () -> Unit) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView =
            ComposeView(service).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent(content)
            }

        windowManager.addView(composeView, layoutParams(bounds, focusable))
        view = composeView
        lifecycleOwner = owner
    }

    /** Recoloca la ventana si la zona a tapar se ha movido (p. ej. al girar la pantalla). */
    fun move(bounds: Rect, focusable: Boolean = true) {
        view?.let { windowManager.updateViewLayout(it, layoutParams(bounds, focusable)) }
    }

    fun hide() {
        view?.let(windowManager::removeView)
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
    }

    private fun layoutParams(bounds: Rect?, focusable: Boolean): WindowManager.LayoutParams {
        var flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) {
            flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return WindowManager.LayoutParams(
            bounds?.width() ?: WindowManager.LayoutParams.MATCH_PARENT,
            bounds?.height() ?: WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (bounds != null) {
                x = bounds.left
                y = bounds.top
            }
        }
    }
}
