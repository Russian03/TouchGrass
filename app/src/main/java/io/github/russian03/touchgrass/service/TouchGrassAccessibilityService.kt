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
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    private var lastActionAt = 0L
    private var lastDumpedIds: Set<String> = emptySet()

    // Sesión de uso: el césped crece la primera vez que se bloquea algo en cada sesión.
    private var lastEventAt = 0L
    private var grassGrownThisSession = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = RuleEngine(ruleRepository.load())
        overlay = BlockOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::engine.isInitialized || event.packageName?.toString() !in engine.packages) return
        trackSession()
        // Instagram emite decenas de eventos por segundo: se evalúa solo cuando la pantalla se calma.
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, DEBOUNCE_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(evaluateRunnable)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    /**
     * Android no avisa de cuándo se cierra otra app. Si Instagram lleva [SESSION_GAP_MS]
     * sin emitir eventos (cerrada o en segundo plano), lo tratamos como una sesión nueva.
     */
    private fun trackSession() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventAt > SESSION_GAP_MS) grassGrownThisSession = false
        lastEventAt = now
    }

    private fun evaluateCurrentScreen() {
        if (overlay.isShowing) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in engine.packages) return

        val snapshot = SnapshotBuilder.build(root)
        if (BuildConfig.DEBUG) dump(snapshot)

        val rule = engine.evaluate(snapshot) ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return
        lastActionAt = now

        apply(rule, root)
    }

    private fun apply(rule: Rule, root: AccessibilityNodeInfo) {
        val done =
            when (val action = rule.action) {
                Action.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
                is Action.Click ->
                    root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/${action.target}")
                        .firstOrNull()
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
                        performGlobalAction(GLOBAL_ACTION_BACK)
            }
        if (done) {
            overlay.show(reason = rule.description, animateGrass = !grassGrownThisSession)
            grassGrownThisSession = true
        }
        Log.i(TAG, "Regla '${rule.id}' aplicada (ok=$done)")
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
        const val ACTION_COOLDOWN_MS = 1_000L
        const val SESSION_GAP_MS = 5 * 60 * 1_000L
    }
}
