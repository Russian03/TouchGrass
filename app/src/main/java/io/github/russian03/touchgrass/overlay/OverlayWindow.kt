package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Ventana Compose de tipo TYPE_ACCESSIBILITY_OVERLAY a pantalla completa. Solo puede crearla
 * un servicio de accesibilidad, así que no requiere el permiso "mostrar sobre otras apps".
 *
 * Se traga todos los toques desde que aparece, pero no coge el foco: el botón atrás del
 * sistema sigue llegando a la app de debajo.
 */
internal class OverlayWindow(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var blurAnimator: ValueAnimator? = null

    val isShowing: Boolean get() = view != null

    /** Si el sistema puede desenfocar en vivo lo que hay detrás de la ventana (Android 12+). */
    val blursBehind: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled

    /** Muestra [content]; si se puede, lo de detrás se va desenfocando durante [blurInMs]. */
    fun show(blurInMs: Long, content: @Composable () -> Unit) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView =
            ComposeView(service).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent(content)
            }

        val params = layoutParams()
        windowManager.addView(composeView, params)
        view = composeView
        lifecycleOwner = owner
        if (blursBehind) animateBlur(composeView, params, blurInMs)
    }

    fun hide() {
        blurAnimator?.cancel()
        blurAnimator = null
        view?.let(windowManager::removeView)
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
    }

    private fun animateBlur(target: ComposeView, params: WindowManager.LayoutParams, durationMs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radius = (BLUR_RADIUS_DP * service.resources.displayMetrics.density).toInt()
        blurAnimator =
            ValueAnimator.ofInt(0, radius).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    if (view !== target) return@addUpdateListener
                    params.blurBehindRadius = animator.animatedValue as Int
                    windowManager.updateViewLayout(target, params)
                }
                start()
            }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        var flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (blursBehind) flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private companion object {
        const val BLUR_RADIUS_DP = 30
    }
}
