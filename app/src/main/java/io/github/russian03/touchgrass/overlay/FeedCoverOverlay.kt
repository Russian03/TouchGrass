package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.ui.theme.Lime

/**
 * Pantalla verde sobre el feed de Inicio de Instagram: una ventana `TYPE_ACCESSIBILITY_OVERLAY` del tamaño
 * de la zona (cabecera y barra de navegación quedan fuera y se pueden tocar) y, alrededor de la barra de
 * navegación, unas franjas lisas que tapan lo que esta deja al aire para que el feed no se pueda mover por
 * ahí. Se tragan los toques de su zona, no cogen el foco (atrás y teclado siguen yendo a Instagram) y,
 * mientras se ve, la app de debajo no suena. No cuenta en las estadísticas.
 */
internal class FeedCoverOverlay(private val service: AccessibilityService, private val silencer: AudioSilencer) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var main: Panel? = null
    private var edges: List<Panel> = emptyList()
    private var shields: List<Panel> = emptyList()
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var silent = false

    /** Una ventana de la capa y dónde está. */
    private class Panel(val view: View, val params: WindowManager.LayoutParams, var zone: Bounds)

    val isShowing: Boolean get() = main != null

    /**
     * Pone la capa sobre [zone], las franjas sobre [edgeZones] y los escudos transparentes sobre [shieldZones]
     * (o los mueve si ya estaban). [onFollowing], [onAskTime] y [onExit] son los botones de la capa principal.
     */
    fun show(
        zone: Bounds,
        edgeZones: List<Bounds>,
        shieldZones: List<Bounds>,
        onFollowing: () -> Unit,
        onAskTime: () -> Unit,
        onExit: () -> Unit,
    ) {
        val current = main
        if (current != null) {
            move(current, zone)
            syncPanels(edgeZones, shieldZones)
            return
        }
        val owner = OverlayLifecycleOwner().also { it.start() }
        val root = FrameLayout(service).apply {
            // Lima desde el primer instante: Compose tarda un poco en dibujar y no debe verse un hueco.
            setBackgroundColor(Lime.toArgb())
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(service).apply { setContent { FeedCoverContent(onFollowing, onAskTime, onExit) } })
        }
        main = add(root, zone)
        lifecycleOwner = owner
        syncPanels(edgeZones, shieldZones)
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
        val panels = listOfNotNull(main) + edges + shields
        val owner = lifecycleOwner
        main = null
        edges = emptyList()
        shields = emptyList()
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

    private fun syncPanels(edgeZones: List<Bounds>, shieldZones: List<Bounds>) {
        edges = sync(edges, edgeZones, transparent = false)
        shields = sync(shields, shieldZones, transparent = true)
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

    private fun add(view: View, zone: Bounds, transparent: Boolean = false): Panel {
        val params = layoutParams(if (transparent) PixelFormat.TRANSPARENT else PixelFormat.OPAQUE).also {
            place(it, zone)
        }
        windowManager.addView(view, params)
        return Panel(view, params, zone)
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

    private fun layoutParams(format: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        format,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Las coordenadas son de pantalla: que coincidan también con un recorte (notch).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
