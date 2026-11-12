package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.russian03.touchgrass.core.blocking.NavFrame
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.ui.theme.Lime

/**
 * Pantalla verde sobre el feed de Inicio de Instagram: una ventana `TYPE_ACCESSIBILITY_OVERLAY` del tamaño
 * de la zona (cabecera y barra de navegación quedan fuera y se pueden tocar) y, alrededor de la barra de
 * navegación, unas franjas lisas que tapan lo que esta deja al aire para que el feed no se pueda mover por
 * ahí. Se tragan los toques de su zona, no cogen el foco (atrás y teclado siguen yendo a Instagram) y,
 * mientras se ve, la app de debajo no suena. No cuenta en las estadísticas.
 *
 * La ventana principal sí coge el foco del teclado (las demás no): así los botones de volumen le llegan a ella y
 * no hacen nada, y el vídeo de debajo no se oye por mucho que se mantengan pulsados. Los toques de fuera de
 * su zona siguen llegando a Instagram. El botón atrás se pasa a [onBack].
 *
 * Entra con un fundido de 1 s y la misma curva que la pausa de Reels (y, si el sistema lo permite, un desenfoque
 * de lo que hay detrás) para que no sea un salto. Mientras entra ya traga los toques. Se quita al momento.
 */
internal class FeedCoverOverlay(private val service: AccessibilityService, private val silencer: AudioSilencer) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var main: Panel? = null
    private var edges: List<Panel> = emptyList()
    private var shields: List<Panel> = emptyList()
    private var framePanel: Panel? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var silent = false
    private var backCallback: Any? = null
    private val fades = mutableListOf<ValueAnimator>()

    /** Una ventana de la capa y dónde está. */
    private class Panel(val view: View, val params: WindowManager.LayoutParams, var zone: Bounds)

    val isShowing: Boolean get() = main != null

    /**
     * Pone la capa sobre [zone], las franjas sobre [edgeZones] y los escudos transparentes sobre [shieldZones]
     * (o los mueve si ya estaban). Con [frame] (la barra de navegación es una pastilla), las franjas solo tragan toques y
     * el marco liso con hueco redondeado rodea la pastilla. [onFollowing], [onAskTime] y [onExit] son los botones de la capa principal.
     */
    fun show(
        zone: Bounds,
        edgeZones: List<Bounds>,
        shieldZones: List<Bounds>,
        frame: NavFrame?,
        onFollowing: () -> Unit,
        onAskTime: () -> Unit,
        onExit: () -> Unit,
        onBack: () -> Unit,
    ) {
        val current = main
        if (current != null) {
            move(current, zone)
            syncPanels(edgeZones, shieldZones, frame)
            return
        }
        val owner = OverlayLifecycleOwner().also { it.start() }
        val root = KeyTrap(onBack).apply {
            // Lima desde el primer instante: Compose tarda un poco en dibujar y no debe verse un hueco.
            setBackgroundColor(Lime.toArgb())
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(service).apply { setContent { FeedCoverContent(onFollowing, onAskTime, onExit) } })
        }
        main = add(root, zone, focusable = true)
        registerBack(root, onBack)
        lifecycleOwner = owner
        syncPanels(edgeZones, shieldZones, frame)
        silencer.silence(this)
        silent = true
    }

    /** Vuelve a silenciar la app si el usuario ha tocado el volumen con la capa puesta. */
    fun enforceSilence() = silencer.enforce()

    /** Quita la capa y devuelve el sonido. */
    fun hide() {
        remove()
        if (silent) {
            silent = false
            silencer.restore(this)
        }
    }

    /** Quita la capa pero mantiene el silencio (pantalla apagada): el sonido vuelve con el siguiente [hide]. */
    fun hideKeepingSilence() = remove()

    /** Quita la capa y devuelve el sonido al momento (el servicio se apaga). */
    fun release() {
        remove()
        silent = false
        silencer.restoreNow(this)
    }

    private fun remove() {
        val panels = listOfNotNull(main, framePanel) + edges + shields
        main?.let { unregisterBack(it.view) }
        fades.forEach { it.cancel() }
        fades.clear()
        val owner = lifecycleOwner
        main = null
        edges = emptyList()
        shields = emptyList()
        framePanel = null
        lifecycleOwner = null
        // Se retiran siempre, aunque aún no estén colocadas: dejarían una ventana invisible tragando toques.
        panels.forEach { panel ->
            try {
                windowManager.removeViewImmediate(panel.view)
            } catch (_: IllegalArgumentException) {
                // Ya no estaba en el WindowManager.
            }
        }
        owner?.destroy()
    }

    private fun syncPanels(edgeZones: List<Bounds>, shieldZones: List<Bounds>, frame: NavFrame?) {
        // Con marco, las franjas ya no se ven (lo liso lo pone el marco): solo tragan toques.
        edges = sync(edges, edgeZones, transparent = frame != null)
        shields = sync(shields, shieldZones, transparent = true)
        syncFrame(frame)
    }

    /** Pone, mueve o quita el marco liso de la pastilla de navegación; no coge toques. */
    private fun syncFrame(frame: NavFrame?) {
        val current = framePanel
        if (frame == null) {
            current?.let {
                try {
                    windowManager.removeViewImmediate(it.view)
                } catch (_: IllegalArgumentException) {
                    // Ya no estaba en el WindowManager.
                }
            }
            framePanel = null
            return
        }
        if (current == null) {
            framePanel = add(PillFrameView(frame), frame.area, touchable = false)
            return
        }
        move(current, frame.area)
        (current.view as PillFrameView).frame = frame
    }

    /** Lima con un hueco redondeado: la pastilla de Instagram se ve entera dentro. Solo dibuja. */
    private inner class PillFrameView(frame: NavFrame) : View(service) {
        var frame: NavFrame = frame
            set(value) {
                field = value
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Lime.toArgb() }
        private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }

        override fun onDraw(canvas: Canvas) {
            val area = frame.area
            val hole = frame.hole
            path.reset()
            path.addRect(0f, 0f, (area.right - area.left).toFloat(), area.height.toFloat(), Path.Direction.CW)
            path.addRoundRect(
                RectF(
                    (hole.left - area.left).toFloat(),
                    (hole.top - area.top).toFloat(),
                    (hole.right - area.left).toFloat(),
                    (hole.bottom - area.top).toFloat(),
                ),
                frame.radius.toFloat(),
                frame.radius.toFloat(),
                Path.Direction.CW,
            )
            canvas.drawPath(path, paint)
        }
    }

    /** Deja un panel por cada zona de [zones]: crea los que faltan, mueve los que hay y quita los que sobran. */
    private fun sync(current: List<Panel>, zones: List<Bounds>, transparent: Boolean): List<Panel> {
        val kept = current.take(zones.size)
        current.drop(zones.size).forEach { panel ->
            try {
                windowManager.removeViewImmediate(panel.view)
            } catch (_: IllegalArgumentException) {
                // Ya no estaba en el WindowManager.
            }
        }
        return zones.mapIndexed { i, zone ->
            kept.getOrNull(i)?.also { move(it, zone) } ?: add(plainView(transparent), zone, transparent)
        }
    }

    /** Vista lisa: lima para las franjas, sin nada para los escudos (solo tragan toques). */
    private fun plainView(transparent: Boolean) = View(service).apply {
        if (!transparent) setBackgroundColor(Lime.toArgb())
    }

    private fun add(
        view: View,
        zone: Bounds,
        transparent: Boolean = false,
        focusable: Boolean = false,
        touchable: Boolean = true,
    ): Panel {
        // Translúcida (no opaca) para poder entrar con un fundido; los escudos no se ven nunca.
        val params = layoutParams(PixelFormat.TRANSLUCENT, focusable, touchable).also { place(it, zone) }
        val blurs =
            focusable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled
        if (blurs) params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        if (!transparent) view.alpha = 0f
        windowManager.addView(view, params)
        val panel = Panel(view, params, zone)
        if (!transparent) fadeIn(panel, blurs)
        return panel
    }

    /** Sube la opacidad de 0 a 1 y, en la ventana principal, el desenfoque de detrás; al acabar quita el desenfoque. */
    private fun fadeIn(panel: Panel, blurs: Boolean) {
        val maxBlur = (BLUR_RADIUS_DP * service.resources.displayMetrics.density).toInt()
        val animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = FADE_IN_MS
                // Igual que la pausa de Reels: empieza muy suave y acelera al final.
                interpolator = PathInterpolator(0.32f, 0f, 0.67f, 0f)
                addUpdateListener { animation ->
                    if (!panel.view.isAttachedToWindow) return@addUpdateListener
                    val fraction = animation.animatedValue as Float
                    panel.view.alpha = fraction
                    if (blurs && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val done = fraction >= 1f
                        panel.params.blurBehindRadius = if (done) 0 else (maxBlur * fraction).toInt()
                        if (done) {
                            panel.params.flags =
                                panel.params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                        }
                        windowManager.updateViewLayout(panel.view, panel.params)
                    }
                }
            }
        fades += animator
        animator.start()
    }

    private fun move(panel: Panel, zone: Bounds) {
        if (zone == panel.zone) return
        place(panel.params, zone)
        windowManager.updateViewLayout(panel.view, panel.params)
        panel.zone = zone
    }

    private fun place(layout: WindowManager.LayoutParams, zone: Bounds) {
        layout.x = zone.left
        layout.y = zone.top
        layout.width = zone.right - zone.left
        layout.height = zone.height
    }

    private fun layoutParams(format: Int, focusable: Boolean, touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Con foco, los toques de fuera de la ventana deben seguir llegando a Instagram (NOT_TOUCH_MODAL).
        (
            if (focusable) {
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            ) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        format,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Las coordenadas son de pantalla: que coincidan también con un recorte (notch).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /**
     * Atrás, con el gesto o el botón. Desde Android 13 llega por [OnBackInvokedDispatcher] (si la app usa el
     * atrás predictivo); si no, como tecla en [KeyTrap].
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

    /** Raíz de la capa: se queda los botones de volumen (no hacen nada) y convierte la tecla atrás en `onBack`. */
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
        /** Lo que tarda la capa en aparecer del todo, y lo que se desenfoca lo de detrás mientras tanto. */
        const val FADE_IN_MS = 1_000L
        const val BLUR_RADIUS_DP = 30
    }
}
