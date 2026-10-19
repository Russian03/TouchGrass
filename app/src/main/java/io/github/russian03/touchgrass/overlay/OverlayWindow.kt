package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.animation.doOnEnd
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Ventana Compose de tipo TYPE_ACCESSIBILITY_OVERLAY a pantalla completa. Solo puede crearla
 * un servicio de accesibilidad, así que no requiere el permiso "mostrar sobre otras apps".
 *
 * Mientras aparece deja pasar los toques, para que el usuario pueda irse a otra pestaña y
 * cancelarla; después se los traga todos. Coge el foco desde el principio: así le llegan a ella
 * los botones de volumen (y no hacen nada) y el botón atrás, que llama a `onBack`.
 */
internal class OverlayWindow(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var blurAnimator: ValueAnimator? = null
    private var settleRunnable: Runnable? = null
    private var backCallback: Any? = null

    val isShowing: Boolean get() = view != null

    /** Si el sistema puede desenfocar en vivo lo que hay detrás de la ventana (Android 12+). */
    private val blursBehind: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled

    /**
     * Muestra [content]; si se puede, lo de detrás se va desenfocando durante [blurInMs]. Durante
     * ese tiempo los toques pasan a la app de debajo; al acabar, la ventana se los traga y avisa
     * con [onSettled]. Si se quita antes, [onSettled] no se llama.
     */
    fun show(blurInMs: Long, onBack: () -> Unit, onSettled: () -> Unit, content: @Composable () -> Unit) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().also { it.start() }
        val root = KeyTrap(onBack).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(service).apply { setContent(content) })
        }

        val params = layoutParams().apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
        windowManager.addView(root, params)
        view = root
        lifecycleOwner = owner
        this.params = params
        registerBack(root, onBack)
        if (blursBehind) animateBlur(root, params, blurInMs)

        val settle = Runnable {
            settleRunnable = null
            if (view !== root) return@Runnable
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager.updateViewLayout(root, params)
            onSettled()
        }
        settleRunnable = settle
        handler.postDelayed(settle, blurInMs)
    }

    /**
     * Quita la ventana. Con [animated], se desvanece en [FADE_OUT_MS] y deja pasar los toques
     * desde el primer momento, para no estorbar lo que el usuario abra después. Si aún estaba
     * apareciendo, se quita al momento: apenas se veía.
     */
    fun hide(animated: Boolean = true) {
        val target = view ?: return
        val targetParams = params
        val owner = lifecycleOwner
        val stillAppearing = settleRunnable != null
        blurAnimator?.cancel()
        blurAnimator = null
        settleRunnable?.let(handler::removeCallbacks)
        settleRunnable = null
        unregisterBack(target)
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
        if (!animated || stillAppearing || targetParams == null || !target.isAttachedToWindow) {
            remove()
            return
        }

        // Suelta también el foco, para que la app de debajo lo recupere al momento.
        targetParams.flags = targetParams.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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

    private fun animateBlur(target: View, params: WindowManager.LayoutParams, durationMs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radius = (BLUR_RADIUS_DP * service.resources.displayMetrics.density).toInt()
        blurAnimator =
            ValueAnimator.ofInt(0, radius).apply {
                duration = durationMs
                // Igual que el fundido: empieza muy suave y acelera al final.
                interpolator = PathInterpolator(0.32f, 0f, 0.67f, 0f)
                addUpdateListener { animator ->
                    if (view !== target) return@addUpdateListener
                    params.blurBehindRadius = animator.animatedValue as Int
                    windowManager.updateViewLayout(target, params)
                }
                start()
            }
    }

    /**
     * Atrás, con el gesto o el botón. Desde Android 13 llega por [OnBackInvokedDispatcher] (si la
     * app usa el atrás predictivo); si no, como tecla en [KeyTrap].
     */
    private fun registerBack(target: View, onBack: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { onBack() }
        target.findOnBackInvokedDispatcher()
            ?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    private fun unregisterBack(target: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = backCallback as? OnBackInvokedCallback ?: return
        target.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
        backCallback = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        var flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (blursBehind) flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /**
     * Raíz de la ventana: se queda los botones de volumen (no hacen nada: ni sale el panel ni
     * vuelve el sonido) y convierte la tecla atrás en `onBack`.
     */
    private inner class KeyTrap(private val onBack: () -> Unit) : FrameLayout(service) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val BLUR_RADIUS_DP = 30
        const val FADE_OUT_MS = 150L
    }
}
