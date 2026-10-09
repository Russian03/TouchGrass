package io.github.russian03.touchgrass.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.BuildConfig
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.data.rules.RuleRepository
import io.github.russian03.touchgrass.overlay.BlockOverlay
import io.github.russian03.touchgrass.overlay.CoverOverlay
import javax.inject.Inject

@AndroidEntryPoint
class TouchGrassAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleRepository: RuleRepository

    private lateinit var engine: RuleEngine
    private lateinit var overlay: BlockOverlay
    private lateinit var cover: CoverOverlay
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    private var lastActionAt = 0L
    private var lastDumpedIds: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = RuleEngine(ruleRepository.load())
        overlay = BlockOverlay(this)
        cover = CoverOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::engine.isInitialized || event.packageName?.toString() !in engine.packages) return
        // Instagram emite decenas de eventos por segundo: se evalúa solo cuando la pantalla se calma.
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, DEBOUNCE_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(evaluateRunnable)
        if (::overlay.isInitialized) overlay.hide()
        if (::cover.isInitialized) cover.hide()
        super.onDestroy()
    }

    private fun evaluateCurrentScreen() {
        if (overlay.isShowing) return
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in engine.packages) {
            // Solo recibimos eventos de Instagram: si el usuario se ha ido, la tapa no debe quedarse.
            cover.hide()
            return
        }

        val snapshot = SnapshotBuilder.build(root)
        if (BuildConfig.DEBUG) dump(snapshot)

        val rule = engine.evaluate(snapshot)
        val action = rule?.action
        if (action is Action.Cover) {
            showCover(action, root)
        } else {
            cover.hide()
            if (rule != null) applyRedirect(rule, root)
        }

        // Mientras hay tapa se sigue mirando la pantalla: así se quita al salir de Instagram,
        // aunque el sistema no nos avise con ningún evento.
        if (cover.isShowing) handler.postDelayed(evaluateRunnable, COVER_POLL_MS)
    }

    private fun showCover(action: Action.Cover, root: AccessibilityNodeInfo) {
        val area = root.boundsOf(action.target) ?: return cover.hide()
        action.below?.let { root.boundsOf(it) }?.let { above -> area.top = maxOf(area.top, above.bottom) }
        if (area.isEmpty) cover.hide() else cover.show(area)
    }

    /** Saca al usuario de la zona bloqueada y muestra la pantalla "Aquí no hay Reels". */
    private fun applyRedirect(rule: Rule, root: AccessibilityNodeInfo) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return
        lastActionAt = now

        val done =
            when (val action = rule.action) {
                Action.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
                is Action.Click ->
                    root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/${action.target}")
                        .firstOrNull()
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
                        performGlobalAction(GLOBAL_ACTION_BACK)
                is Action.Cover -> false
            }
        if (done) overlay.show(reason = rule.description)
        Log.i(TAG, "Regla '${rule.id}' aplicada (ok=$done)")
    }

    /** Zona en pantalla de la vista visible más grande con ese identificador. */
    private fun AccessibilityNodeInfo.boundsOf(viewId: String): Rect? =
        findAccessibilityNodeInfosByViewId("$packageName:id/$viewId")
            .filter { it.isVisibleToUser }
            .map { node -> Rect().also(node::getBoundsInScreen) }
            .maxByOrNull { it.width() * it.height() }

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
        const val ACTION_COOLDOWN_MS = 1_000L
        const val COVER_POLL_MS = 500L
    }
}
