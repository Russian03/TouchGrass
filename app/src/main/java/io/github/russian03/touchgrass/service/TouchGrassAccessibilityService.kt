package io.github.russian03.touchgrass.service

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.rules.RuleRepository
import io.github.russian03.touchgrass.data.stats.StatsRepository
import io.github.russian03.touchgrass.overlay.BlockOverlay
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class TouchGrassAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleRepository: RuleRepository

    @Inject lateinit var budget: BudgetRepository

    @Inject lateinit var stats: StatsRepository

    private lateinit var engine: RuleEngine
    private lateinit var overlay: BlockOverlay
    private val session = ViewerSession()
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val confirmation = ShowConfirmation()
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    // Al caducar un desbloqueo se vuelve a mirar la pantalla aunque Instagram no mande eventos.
    private val expiryRunnable = Runnable { scheduleEvaluation(0) }
    private val scope = MainScope()

    /** Último saldo leído; mientras no se ha leído, no hay nada desbloqueado. */
    private var budgetState: BudgetState? = null

    private var pendingSince: Long? = null
    private var launcher: String? = null
    private var leftAt = Long.MIN_VALUE / 2

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
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // El servicio está siempre en marcha (también tras reiniciar): desde aquí empiezan las
        // estadísticas y se programa el aviso del resumen mensual.
        scope.launch {
            stats.start()
            // Si se había quitado, los días sin protección quedan marcados.
            stats.markProtectionOn()
        }
        forgetMonthlyReport()
        // Los desbloqueos se piden desde la app, en otro proceso: llegan por el DataStore.
        scope.launch {
            budget.state.collect {
                budgetState = it
                scheduleEvaluation(0)
                // El límite de cada día, para enseñar el de cada mes en las estadísticas.
                if (it.onboardingDone) stats.recordLimit(it.limitMinutes)
            }
        }
    }

    /**
     * Al apagar la pantalla se quita la de bloqueo (si no, taparía el bloqueo del móvil) y se
     * cancela cualquier mirada pendiente, para que no vuelva a ponerla con el móvil apagado.
     */
    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(evaluateRunnable)
                        pendingSince = null
                        confirmation.reset()
                        overlay.hideKeepingSilence()
                    }
                    // Encendido sin pedir desbloqueo (p. ej. con retardo de bloqueo), o desbloqueado:
                    // si sigue el reel, la pantalla vuelve; si no, vuelve el sonido.
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> scheduleEvaluation(0)
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
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && overlay.isShowing) {
            // Solo llegan toques a la app mientras la pantalla aparece: el usuario se está yendo
            // (p. ej. a otra pestaña). Se mira enseguida para quitarla antes de que se vea más.
            scheduleEvaluation(CLICK_SETTLE_MS)
            return
        }
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
        confirmation.reset()
        leftAt = SystemClock.elapsedRealtime()
        overlay.hide()
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

    /**
     * Las versiones anteriores avisaban del resumen del mes con una alarma y un canal de avisos.
     * Ya no existen: se cancela la alarma que pudiera quedar y se borra el canal. Si no hay nada,
     * no hace nada.
     */
    private fun forgetMonthlyReport() {
        val receiver = Intent().setComponent(ComponentName(this, LEGACY_REPORT_RECEIVER))
        PendingIntent.getBroadcast(
            this,
            LEGACY_REPORT_REQUEST,
            receiver,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarm ->
            getSystemService(AlarmManager::class.java).cancel(alarm)
            alarm.cancel()
        }
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(LEGACY_REPORT_CHANNEL)
    }

    override fun onInterrupt() = Unit

    /**
     * Android lo llama al desactivar TouchGrass en Accesibilidad, pero también al desvincularlo por
     * su cuenta (al cerrar la app, al actualizarla…). Solo lo primero cuenta como quitar la
     * protección: entonces se apunta y se espera a que quede guardado (con tope), porque el proceso
     * puede acabar ya. Si sigue activado en el ajuste, es un corte y no se apunta nada.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        if (!AccessibilityStatus.isEnabledInSettings(this)) {
            runBlocking { withTimeoutOrNull(UNBIND_SAVE_TIMEOUT_MS) { stats.markProtectionOff() } }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(evaluateRunnable)
        handler.removeCallbacks(expiryRunnable)
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
        // Con la pantalla apagada o el móvil bloqueado no se pone nada (taparía la pantalla de
        // bloqueo del móvil) y, si estaba puesta, se quita sin devolver el sonido. La mirada
        // periódica lo detecta incluso antes de que llegue el aviso de pantalla apagada.
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            confirmation.reset()
            if (overlay.isShowing) overlay.hideKeepingSilence()
            return
        }
        // A veces la ventana activa no está disponible un instante: entonces no se cambia nada.
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in engine.packages) {
            // Solo recibimos eventos de Instagram: si el usuario se ha ido, la pantalla no debe quedarse.
            confirmation.reset()
            overlay.hide()
            return
        }

        val snapshot = SnapshotBuilder.build(root, engine.texts(root.packageName.toString()))
        engine.place(snapshot)?.let(session::onPlace)
        if (engine.pagers(snapshot.packageName).any { snapshot.has(it) }) {
            session.onViewerShown(SystemClock.elapsedRealtime())
        }

        // Con un desbloqueo en marcha se sigue la navegación, pero no se bloquea nada.
        val rule =
            engine.evaluate(snapshot, session.origin, session.scrolled)
                ?.takeUnless { unlocked(snapshot.packageName) }
        val now = SystemClock.elapsedRealtime()
        when {
            rule == null -> {
                confirmation.reset()
                overlay.hide()
            }
            overlay.isShowing || now - leftAt < LEAVE_COOLDOWN_MS -> Unit
            // Se vuelve a mirar en un momento: si sigue igual, entonces se pone.
            !confirmation.onMatch("${snapshot.packageName}/${rule.id}", now) ->
                scheduleEvaluation(confirmation.remainingMs(now))
            else -> {
                confirmation.reset()
                val packageName = snapshot.packageName
                overlay.show(
                    onLeave = { leave(rule, packageName) },
                    // Solo cuenta si ha llegado a aparecer del todo (no si se ha ido mientras aparecía).
                    onSettled = { scope.launch { stats.recordBlock(packageName) } },
                )
            }
        }

        // Mientras se ve la pantalla se sigue mirando la de debajo: así se quita al salir de
        // Instagram o al hacer atrás, aunque el sistema no nos avise con ningún evento.
        if (overlay.isShowing) handler.postDelayed(evaluateRunnable, OVERLAY_POLL_MS)
    }

    /**
     * Si [packageName] tiene un desbloqueo en marcha. Si lo tiene, deja programada otra
     * evaluación para el momento en que caduca.
     */
    private fun unlocked(packageName: String): Boolean {
        val state = budgetState ?: return false
        val now = budget.now()
        val unlock = BudgetPolicy.activeUnlock(state, now)?.takeIf { it.packageName == packageName } ?: return false
        handler.removeCallbacks(expiryRunnable)
        handler.postDelayed(expiryRunnable, BudgetPolicy.unlockRemainingMs(unlock, now) + EXPIRY_MARGIN_MS)
        return true
    }

    /** Saca al usuario de la zona bloqueada cuando lo pide desde la pantalla de bloqueo. */
    private fun leave(rule: Rule, packageName: String) {
        leftAt = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow
        when (val action = rule.action) {
            Action.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            // Si no encuentra a dónde ir, al menos vuelve atrás.
            is Action.Click ->
                root?.let { clickTarget(it, action) }
                    ?.clickableSelfOrParent()
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
                    performGlobalAction(GLOBAL_ACTION_BACK)
        }
        scope.launch { stats.recordLeave(packageName) }
    }

    /** Vista que pulsar para [action]: por identificador o, sin él, por texto o descripción exactos. */
    private fun clickTarget(root: AccessibilityNodeInfo, action: Action.Click): AccessibilityNodeInfo? {
        val target = action.target
        if (target != null) {
            return root.findAccessibilityNodeInfosByViewId(SnapshotBuilder.resourceName("${root.packageName}", target))
                .firstOrNull { action.text.isEmpty() || it.text?.toString() in action.text }
        }
        // La búsqueda por texto también encuentra vistas que solo lo contienen: se exige que sea igual.
        return action.text.asSequence()
            .flatMap { text -> root.findAccessibilityNodeInfosByText(text).asSequence().map { text to it } }
            .firstOrNull { (text, node) ->
                node.text?.toString() == text || node.contentDescription?.toString() == text
            }
            ?.second
    }

    /** La propia vista o el primer padre que se puede pulsar (en TikTok el texto de la pestaña no lo es). */
    private fun AccessibilityNodeInfo.clickableSelfOrParent(): AccessibilityNodeInfo? =
        generateSequence(this) { it.parent }.take(MAX_CLICK_ANCESTORS).firstOrNull { it.isClickable }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val MAX_WAIT_MS = 300L
        const val LEAVE_COOLDOWN_MS = 1_000L
        const val OVERLAY_POLL_MS = 200L
        const val CLICK_SETTLE_MS = 50L
        const val EXPIRY_MARGIN_MS = 100L
        const val MAX_CLICK_ANCESTORS = 4
        const val UNBIND_SAVE_TIMEOUT_MS = 1_000L

        // Lo que usaba el aviso del resumen del mes de versiones anteriores, para borrarlo.
        const val LEGACY_REPORT_RECEIVER = "io.github.russian03.touchgrass.notification.MonthlyReportReceiver"
        const val LEGACY_REPORT_REQUEST = 1
        const val LEGACY_REPORT_CHANNEL = "monthly_report"
    }
}
