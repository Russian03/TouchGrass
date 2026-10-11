package io.github.russian03.touchgrass.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.BuildConfig
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.data.rules.RuleRepository
import io.github.russian03.touchgrass.overlay.BlockOverlay
import javax.inject.Inject

@AndroidEntryPoint
class TouchGrassAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleRepository: RuleRepository

    private lateinit var engine: RuleEngine
    private lateinit var overlay: BlockOverlay
    private val session = ViewerSession()
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    private var pendingSince: Long? = null
    private var leftAt = Long.MIN_VALUE / 2
    private var lastDumpedIds: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = RuleEngine(ruleRepository.load())
        overlay = BlockOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (!::engine.isInitialized || packageName !in engine.packages) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && trackPager(event, packageName!!)) {
            // El usuario acaba de pasar al siguiente reel: no se espera a que la pantalla se calme.
            scheduleEvaluation(0)
            return
        }
        // Instagram emite decenas de eventos por segundo: se evalúa cuando la pantalla se calma,
        // pero sin esperar más de MAX_WAIT_MS (con un vídeo en marcha nunca llega a calmarse).
        val now = SystemClock.elapsedRealtime()
        if (pendingSince == null) pendingSince = now
        val waited = now - pendingSince!!
        scheduleEvaluation(DEBOUNCE_MS.coerceAtMost(MAX_WAIT_MS - waited).coerceAtLeast(0))
    }

    private fun scheduleEvaluation(delayMs: Long) {
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, delayMs)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(evaluateRunnable)
        if (::overlay.isInitialized) overlay.release()
        super.onDestroy()
    }

    /**
     * Cuenta los cambios de página del visor para saber si el usuario ha pasado al siguiente reel.
     * Devuelve `true` justo cuando lo hace.
     */
    private fun trackPager(event: AccessibilityEvent, packageName: String): Boolean {
        val viewId = event.source?.viewIdResourceName?.substringAfter(":id/") ?: return false
        if (viewId !in engine.pagers(packageName)) return false
        val before = session.scrolled
        session.onPagerScrolled(SystemClock.elapsedRealtime())
        return !before && session.scrolled
    }

    private fun evaluateCurrentScreen() {
        pendingSince = null
        // A veces la ventana activa no está disponible un instante: entonces no se cambia nada.
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in engine.packages) {
            // Solo recibimos eventos de Instagram: si el usuario se ha ido, la pantalla no debe quedarse.
            overlay.hide()
            return
        }

        val snapshot = SnapshotBuilder.build(root)
        if (BuildConfig.DEBUG) dump(snapshot)
        engine.place(snapshot)?.let(session::onPlace)
        if (engine.pagers(snapshot.packageName).any { snapshot.has(it) }) {
            session.onViewerShown(SystemClock.elapsedRealtime())
        }

        val rule = engine.evaluate(snapshot, session.origin, session.scrolled)
        when {
            rule == null -> overlay.hide()
            !overlay.isShowing && SystemClock.elapsedRealtime() - leftAt >= LEAVE_COOLDOWN_MS -> {
                overlay.show(onLeave = { leave(rule) })
                Log.i(TAG, "Regla '${rule.id}' aplicada (origen=${session.origin}, scroll=${session.scrolled})")
            }
        }

        // Mientras se ve la pantalla se sigue mirando la de debajo: así se quita al salir de
        // Instagram o al hacer atrás, aunque el sistema no nos avise con ningún evento.
        if (overlay.isShowing) handler.postDelayed(evaluateRunnable, OVERLAY_POLL_MS)
    }

    /** Saca al usuario de la zona bloqueada cuando lo pide desde la pantalla de bloqueo. */
    private fun leave(rule: Rule) {
        leftAt = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow
        val done =
            when (val action = rule.action) {
                Action.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
                is Action.Click ->
                    root?.findAccessibilityNodeInfosByViewId("${root.packageName}:id/${action.target}")
                        ?.firstOrNull()
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
                        performGlobalAction(GLOBAL_ACTION_BACK)
            }
        Log.i(TAG, "Salida de '${rule.id}' (ok=$done)")
    }

    /** Solo en debug: vuelca los identificadores visibles para descubrir y mantener reglas. */
    private fun dump(snapshot: ScreenSnapshot) {
        val ids = snapshot.viewIds
        if (ids == lastDumpedIds) return
        lastDumpedIds = ids
        val selected = snapshot.nodes.filter { it.selected }.map { it.viewId }.distinct()
        Log.d(TAG, "DUMP pkg=${snapshot.packageName} selected=$selected ids=${ids.sorted()}")
    }

    private companion object {
        const val TAG = "TouchGrass"
        const val DEBOUNCE_MS = 150L
        const val MAX_WAIT_MS = 300L
        const val LEAVE_COOLDOWN_MS = 1_000L
        const val OVERLAY_POLL_MS = 500L
    }
}
