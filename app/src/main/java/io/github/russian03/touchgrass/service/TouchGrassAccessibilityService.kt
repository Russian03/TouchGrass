package io.github.russian03.touchgrass.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
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
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    private var pendingSince: Long? = null
    private var launcher: String? = null
    private var leftAt = Long.MIN_VALUE / 2
    private var lastDumpedIds: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = RuleEngine(ruleRepository.load())
        overlay = BlockOverlay(this)
        launcher = defaultLauncher()
        // Además de Instagram se escucha al launcher, solo para saber al momento que el usuario
        // ha salido a Inicio o a Recientes: de su pantalla no se lee nada.
        serviceInfo = serviceInfo.apply { packageNames = (engine.packages + listOfNotNull(launcher)).toTypedArray() }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Al apagar la pantalla se quita la de bloqueo (si no, taparía el bloqueo del móvil). */
    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> overlay.hideKeepingSilence()
                    // Desbloqueado: si sigue el reel, la pantalla vuelve; si no, vuelve el sonido.
                    Intent.ACTION_USER_PRESENT -> scheduleEvaluation(0)
                }
            }
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (packageName != null && packageName == launcher) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onLeftApp()
            return
        }
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

    /**
     * El usuario ha salido a Inicio o a Recientes: la pantalla de bloqueo se quita al momento y,
     * mientras dura la animación de salida (en la que Instagram aún parece estar delante), no
     * vuelve a ponerse.
     */
    private fun onLeftApp() {
        handler.removeCallbacks(evaluateRunnable)
        pendingSince = null
        leftAt = SystemClock.elapsedRealtime()
        overlay.hide()
        if (BuildConfig.DEBUG) Log.d(TAG, "Fuera de Instagram: pantalla quitada")
    }

    /** App de inicio del móvil (la que también muestra Recientes), o `null` si no se encuentra. */
    private fun defaultLauncher(): String? = packageManager
        .resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        ?.activityInfo
        ?.packageName
        ?.takeIf { it != "android" }

    private fun scheduleEvaluation(delayMs: Long) {
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, delayMs)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(evaluateRunnable)
        if (::overlay.isInitialized) {
            unregisterReceiver(screenReceiver)
            overlay.release()
        }
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
        // Con el móvil bloqueado no se pone nada: taparía la pantalla de bloqueo.
        if (keyguard.isKeyguardLocked) return
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
        const val OVERLAY_POLL_MS = 200L
    }
}
