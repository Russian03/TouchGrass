package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.animation.doOnEnd
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
    private var params: WindowManager.LayoutParams? = null
    private var blurAnimator: ValueAnimator? = null

    val isShowing: Boolean get() = view != null

    /** Si el sistema puede desenfocar en vivo lo que hay detrás de la ventana (Android 12+). */
    private val blursBehind: Boolean
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
        this.params = params
        if (blursBehind) animateBlur(composeView, params, blurInMs)
    }

    /**
     * Quita la ventana. Con [animated], se desvanece en [FADE_OUT_MS] y deja pasar los toques
     * desde el primer momento, para no estorbar lo que el usuario abra después.
     */
    fun hide(animated: Boolean = true) {
        val target = view ?: return
        val targetParams = params
        val owner = lifecycleOwner
        blurAnimator?.cancel()
        blurAnimator = null
        view = null
        params = null
        lifecycleOwner = null

        // Se retira siempre, aunque aún no esté colocada: si se acaba de añadir, isAttachedToWindow
        // todavía es false y saltársela dejaría una ventana invisible tragándose los toques.
        val remove: () -> Unit = {
            try {
                windowManager.removeViewImmediate(target)
            } catch (_: IllegalArgumentException) {
                // Ya no estaba en el WindowManager.
            }
            owner?.destroy()
        }
        if (!animated || targetParams == null || !target.isAttachedToWindow) {
            remove()
            return
        }

        targetParams.flags = targetParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(target, targetParams)
        val startBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetParams.blurBehindRadius else 0
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = FADE_OUT_MS
            addUpdateListener { animator ->
                if (!target.isAttachedToWindow) return@addUpdateListener
                val fraction = animator.animatedValue as Float
                target.alpha = fraction
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && startBlur > 0) {
                    targetParams.blurBehindRadius = (startBlur * fraction).toInt()
                    windowManager.updateViewLayout(target, targetParams)
                }
            }
            doOnEnd { remove() }
            start()
        }
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
        const val FADE_OUT_MS = 150L
    }
}
