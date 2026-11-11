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
import io.github.russian03.touchgrass.core.blocking.BlockDecision
import io.github.russian03.touchgrass.core.blocking.BlockExit
import io.github.russian03.touchgrass.core.blocking.BlockPolicy
import io.github.russian03.touchgrass.core.blocking.FeedSwitch
import io.github.russian03.touchgrass.core.blocking.SilentBackGuard
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.rules.RuleRepository
import io.github.russian03.touchgrass.data.stats.StatsRepository
import io.github.russian03.touchgrass.overlay.AudioSilencer
import io.github.russian03.touchgrass.overlay.BlockOverlay
import io.github.russian03.touchgrass.overlay.FeedCoverOverlay
import io.github.russian03.touchgrass.overlay.openTouchGrass
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
    private lateinit var feedCover: FeedCoverOverlay
    private val session = ViewerSession()
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val confirmation = ShowConfirmation()
    private val handler = Handler(Looper.getMainLooper())
    private val evaluateRunnable = Runnable { evaluateCurrentScreen() }

    // Tras un cambio de pantalla el árbol de Instagram tarda un poco en actualizarse: se vuelve a mirar.
    private val followUpRunnable = Runnable { evaluateCurrentScreen() }
    private val followUpLateRunnable = Runnable { evaluateCurrentScreen() }

    // Al caducar un desbloqueo se vuelve a mirar la pantalla aunque Instagram no mande eventos.
    private val expiryRunnable = Runnable { scheduleEvaluation(0) }
    private val scope = MainScope()

    /** Último saldo leído; mientras no se ha leído, no hay nada desbloqueado. */
    private var budgetState: BudgetState? = null

    private var pendingSince: Long? = null
    private var launcher: String? = null
    private var leftAt = Long.MIN_VALUE / 2

    /**
     * Regla del último `Block`. Al abrir los comentarios el reel desaparece de lo que se lee y solo
     * queda la hoja: con esta regla se sigue bloqueando. Se olvida con `Free`, al salir y al apagar.
     */
    private var heldRule: Rule? = null

    /** Si en la última mirada solo se veía la hoja de comentarios sobre una zona bloqueada. */
    private var sheetShown = false

    /** Hay una salida pedida que aún no se ha hecho (ver [leave]). */
    private var leavePending = false

    /** Límite del atrás silencioso (ver [silentBack]). */
    private val silentBackGuard = SilentBackGuard()

    /** «Ir a Siguiendo» en dos pasos (ver [advanceFeedSwitch]). */
    private val feedSwitch = FeedSwitch()
    private val feedSwitchRunnable = Runnable { advanceFeedSwitch() }

    /** Miradas seguidas sin ventana activa mientras hay capa (ver [evaluateCurrentScreen]). */
    private var nullRoots = 0
    private var lastScrollBackAt = 0L
    private var lastTabClickAt = 0L
    private var feedBackTries = 0

    private var lastEvalAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = RuleEngine(ruleRepository.load())
        val silencer = AudioSilencer(this)
        overlay = BlockOverlay(this, silencer)
        feedCover = FeedCoverOverlay(this, silencer)
        launcher = defaultLauncher()
        // Además de Instagram se escucha al launcher, solo para saber al momento que el usuario
        // ha salido a Inicio o a Recientes: de su pantalla no se lee nada.
        serviceInfo =
            serviceInfo.apply { packageNames = (engine.packages + listOfNotNull(launcher, SYSTEM_UI)).toTypedArray() }
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
                        heldRule = null
                        overlay.hideKeepingSilence()
                        hideCover(keepSilence = true)
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
        if (packageName == SYSTEM_UI) {
            // Panel de notificaciones o ajustes rápidos: de su pantalla no se lee nada, solo se mira de nuevo.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                ::feedCover.isInitialized &&
                feedCover.isShowing
            ) {
                scheduleEvaluation(0)
            }
            return
        }
        if (!::engine.isInitialized || packageName !in engine.packages) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            !overlay.isShowing &&
            isPauseTab(event, packageName!!)
        ) {
            // El usuario acaba de tocar una pestaña bloqueada (p. ej. Reels): el vídeo empieza a sonar ya, y
            // la pausa tarda un momento. Se silencia al instante; si no resulta bloqueada, vuelve el sonido.
            overlay.silenceEarly()
        }
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
        if (feedCover.isShowing) {
            // Con la capa puesta no se espera a que se calme: debe irse al momento al cambiar de pantalla.
            when (event.eventType) {
                // Un toque fuera de la capa (cabecera u otra pestaña): casi seguro se sale de «Para ti».
                // Se quita ya y se mira otra vez cuando la pantalla haya cambiado. Tocar la pestaña de
                // Inicio no sale de ahí: no se quita.
                AccessibilityEvent.TYPE_VIEW_CLICKED ->
                    if (!isCoverNav(event, packageName!!)) {
                        hideCover()
                        scheduleEvaluation(COVER_RECHECK_MS)
                        return
                    }
            }
            val sinceLast = SystemClock.elapsedRealtime() - lastEvalAt
            scheduleEvaluation((COVER_EVAL_MIN_MS - sinceLast).coerceAtLeast(0))
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED && engine.coverSpecs(packageName!!).isNotEmpty()) {
            // «Para ti» debe taparse al momento, sin esperar a que la pantalla se calme: si no, hay unos
            // instantes en los que el feed se puede mover. La primera mirada tras un rato sin eventos es
            // inmediata; con eventos seguidos, como mucho una cada LEADING_EVAL_MIN_MS.
            val sinceLast = SystemClock.elapsedRealtime() - lastEvalAt
            scheduleEvaluation((LEADING_EVAL_MIN_MS - sinceLast).coerceAtLeast(0))
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            ) {
                scheduleFollowUps()
            }
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
        heldRule = null
        leftAt = SystemClock.elapsedRealtime()
        overlay.hide()
        hideCover()
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
        handler.removeCallbacks(feedSwitchRunnable)
        handler.removeCallbacks(followUpRunnable)
        handler.removeCallbacks(followUpLateRunnable)
        if (::overlay.isInitialized) {
            unregisterReceiver(screenReceiver)
            overlay.release()
            feedCover.release()
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
        lastEvalAt = SystemClock.elapsedRealtime()
        // Con la pantalla apagada o el móvil bloqueado no se pone nada (taparía la pantalla de
        // bloqueo del móvil) y, si estaba puesta, se quita sin devolver el sonido. La mirada
        // periódica lo detecta incluso antes de que llegue el aviso de pantalla apagada.
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            confirmation.reset()
            heldRule = null
            if (overlay.isShowing) overlay.hideKeepingSilence()
            hideCover(keepSilence = true)
            return
        }
        // A veces la ventana activa no está disponible un instante: entonces no se cambia nada.
        val root = rootInActiveWindow
        if (root == null) {
            // Con la capa puesta no puede quedarse congelada: se sigue mirando y, si la ventana no
            // vuelve en un momento, se quita.
            if (feedCover.isShowing) {
                if (++nullRoots >= MAX_NULL_ROOTS) hideCover() else scheduleEvaluation(COVER_POLL_MS)
            }
            return
        }
        nullRoots = 0
        if (root.packageName?.toString() !in engine.packages) {
            // Solo recibimos eventos de Instagram: si el usuario se ha ido, la pantalla no debe quedarse.
            confirmation.reset()
            heldRule = null
            overlay.hide()
            hideCover()
            return
        }

        val packageName = root.packageName.toString()
        val snapshot = SnapshotBuilder.build(root, engine.texts(packageName), engine.coverSpecs(packageName))
        val now = SystemClock.elapsedRealtime()
        BlockPolicy.track(engine, session, snapshot, now)

        val decision =
            BlockPolicy.decide(
                engine,
                snapshot,
                session.origin,
                session.scrolled,
                budgetState,
                budget::now,
                overlay.isShowing,
                now,
                leftAt,
                heldRule,
            )
        sheetShown = BlockPolicy.isOverSheet(engine, snapshot, session.origin, session.scrolled, heldRule)
        if (decision !is BlockDecision.Cover) hideCover()
        when (decision) {
            is BlockDecision.Free -> {
                // Con un desbloqueo en marcha se sigue la navegación, pero no se bloquea nada.
                decision.unlockRemainingMs?.let(::scheduleExpiry)
                confirmation.reset()
                heldRule = null
                silentBackGuard.reset()
                overlay.hide()
            }
            is BlockDecision.Cover -> {
                // Sin pausa, sin caducidad y sin estadísticas: solo la capa sobre el feed.
                confirmation.reset()
                heldRule = null
                silentBackGuard.reset()
                overlay.hide()
                feedCover.show(
                    decision.zone,
                    decision.edges,
                    decision.shields,
                    onFollowing = ::goFollowing,
                    onAskTime = {
                        openTouchGrass(this)
                        hideCover()
                    },
                    onExit = ::exitApp,
                )
                // «Para ti» se queda arriba del todo: con la cabecera escondida el feed se ha movido (p. ej.
                // desde un hueco que la capa no cubre) y se devuelve arriba, sin que se vea bajo la capa.
                if (snapshot.headerBottom == null) bringFeedToTop(root, packageName) else feedBackTries = 0
            }
            BlockDecision.Keep -> {
                // Tras «Volver atrás» con los comentarios abiertos el reel sigue delante y la app puede
                // no mandar eventos: se vuelve a mirar cuando acabe el enfriamiento.
                if (!overlay.isShowing) scheduleEvaluation(BlockPolicy.cooldownRemainingMs(now, leftAt))
            }
            is BlockDecision.Block -> {
                heldRule = decision.rule
                // El reel no debe oírse mientras se confirma: se silencia ya (si no se confirma, vuelve el sonido).
                overlay.silenceEarly()
                // Se vuelve a mirar en un momento: si sigue igual, entonces se pone.
                if (!confirmation.onMatch(decision.confirmationKey, now)) {
                    scheduleEvaluation(confirmation.remainingMs(now))
                } else {
                    confirmation.reset()
                    overlay.show(
                        onLeave = { leave(decision.rule, packageName) },
                        // Solo cuenta si ha llegado a aparecer del todo (no si se ha ido mientras aparecía).
                        onSettled = { scope.launch { stats.recordBlock(packageName) } },
                    )
                }
            }
            is BlockDecision.SilentBack -> silentBack(decision, now)
        }

        // Mientras se ve la pantalla se sigue mirando la de debajo: así se quita al salir de
        // Instagram o al hacer atrás, aunque el sistema no nos avise con ningún evento.
        if (overlay.isShowing) {
            handler.postDelayed(evaluateRunnable, OVERLAY_POLL_MS)
        } else if (feedCover.isShowing) {
            // Si el usuario toca el volumen, el vídeo de debajo no debe volver a oírse.
            feedCover.enforceSilence()
            handler.postDelayed(evaluateRunnable, COVER_POLL_MS)
        }
    }

    /** Programa dos miradas más tras un cambio de pantalla, por si la primera ve aún el árbol viejo. */
    private fun scheduleFollowUps() {
        handler.removeCallbacks(followUpRunnable)
        handler.removeCallbacks(followUpLateRunnable)
        handler.postDelayed(followUpRunnable, FOLLOW_UP_MS)
        handler.postDelayed(followUpLateRunnable, FOLLOW_UP_LATE_MS)
    }

    /**
     * Devuelve el feed arriba del todo: primero desplazando la lista hacia atrás y, si tras varios
     * intentos sigue abajo, tocando la pestaña de Inicio (que sube de golpe y recarga, por eso con un
     * respiro largo entre toques).
     */
    private fun bringFeedToTop(root: AccessibilityNodeInfo, packageName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrollBackAt < SCROLL_BACK_GAP_MS) return
        val spec = engine.coverSpecs(packageName).firstOrNull() ?: return
        lastScrollBackAt = now
        if (++feedBackTries > SCROLL_BACK_TRIES &&
            now - lastTabClickAt >= TAB_CLICK_GAP_MS &&
            clickNode(root, Action.Click(target = spec.nav))
        ) {
            lastTabClickAt = now
            feedBackTries = 0
            return
        }
        val list =
            spec.list
                ?.let { SnapshotBuilder.resourceName(packageName, it) }
                ?.let(root::findAccessibilityNodeInfosByViewId)
                ?.firstOrNull { it.isScrollable } ?: return
        list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /** Si [event] es el toque de una pestaña que bloquea con pausa (la que mira alguna regla marcada como seleccionada). */
    private fun isPauseTab(event: AccessibilityEvent, packageName: String): Boolean {
        val id = event.source?.viewIdResourceName ?: return false
        return engine.pauseTabIds(packageName).any { id.endsWith(":id/$it") }
    }

    /** Si [event] viene de la pestaña de Inicio (la barra de navegación de la capa). */
    private fun isCoverNav(event: AccessibilityEvent, packageName: String): Boolean {
        val nav = engine.coverSpecs(packageName).firstOrNull()?.nav ?: return false
        return event.source?.viewIdResourceName?.endsWith(":id/$nav") == true
    }

    /** Quita la capa de Inicio. Con [keepSilence], el sonido vuelve al siguiente quitar. */
    private fun hideCover(keepSilence: Boolean = false) {
        if (keepSilence) feedCover.hideKeepingSilence() else feedCover.hide()
    }

    /** «Salir de la app»: lleva al usuario a la pantalla de inicio del móvil. No cuenta en las estadísticas. */
    private fun exitApp() {
        hideCover()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** «Ir a Siguiendo»: empieza la salida en dos pasos desde la capa (ver [advanceFeedSwitch]). */
    private fun goFollowing() {
        feedSwitch.start(SystemClock.elapsedRealtime())
        advanceFeedSwitch()
    }

    /**
     * Cambia Inicio de «Para ti» a «Siguiendo» (Instagram 454): pulsa el selector de la cabecera y,
     * cuando sale el menú (otra ventana, que pasa a ser la activa), pulsa «Siguiendo». Se mira cada
     * [FEED_SWITCH_POLL_MS] mientras haya salida en marcha. No cuenta en las estadísticas.
     */
    private fun advanceFeedSwitch() {
        handler.removeCallbacks(feedSwitchRunnable)
        if (!feedSwitch.isActive) return
        val root = rootInActiveWindow
        if (root != null) {
            val pkg = root.packageName.toString()
            val menuShown = root.findAccessibilityNodeInfosByViewId(
                SnapshotBuilder.resourceName(pkg, FEED_MENU_ID),
            ).isNotEmpty()
            when (feedSwitch.next(SystemClock.elapsedRealtime(), menuShown)) {
                FeedSwitch.Step.OpenMenu -> clickNode(root, Action.Click(target = FEED_SELECTOR_ID))
                FeedSwitch.Step.PickFollowing -> pickFollowing(root, pkg)
                FeedSwitch.Step.None -> Unit
            }
        }
        if (feedSwitch.isActive) handler.postDelayed(feedSwitchRunnable, FEED_SWITCH_POLL_MS)
    }

    /** Pulsa «Siguiendo» en el menú del selector (texto exacto, no «contiene»). */
    private fun pickFollowing(root: AccessibilityNodeInfo, packageName: String) {
        root.findAccessibilityNodeInfosByViewId(SnapshotBuilder.resourceName(packageName, FEED_MENU_ITEM_ID))
            .firstOrNull { item ->
                FOLLOWING_LABELS.any {
                    BlockExit.matchesText(it, item.text?.toString(), item.contentDescription?.toString())
                }
            }
            ?.let(::clickableSelfOrParent)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Regla `silentBack` (vista previa o menú de Buscar): sin pausa, un atrás que cierra lo que
     * haya abierto, tanto si el dedo sigue abajo como si ya soltó. Se hace solo si la regla sigue
     * casando en una segunda mirada (la confirmación: si el usuario soltó y ya se ve la
     * cuadrícula, no hay atrás, que lo sacaría de Buscar). [silentBackGuard] evita el bucle si la
     * app no se cierra. No cuenta en las estadísticas.
     */
    private fun silentBack(decision: BlockDecision.SilentBack, now: Long) {
        // Otra pausa o una salida pendiente tienen prioridad: no se mezcla un atrás con ellas.
        if (overlay.isShowing || leavePending) return
        if (!confirmation.onMatch(decision.confirmationKey, now)) {
            scheduleEvaluation(confirmation.remainingMs(now))
            return
        }
        confirmation.reset()
        if (silentBackGuard.tryAcquire(now)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        // Si sigue igual tras el atrás, se vuelve a mirar cuando acabe el enfriamiento (hasta el tope).
        if (!silentBackGuard.isExhausted) scheduleEvaluation(silentBackGuard.remainingMs(now))
    }

    /** Deja programada otra evaluación para cuando caduque el desbloqueo al que le quedan [remainingMs]. */
    private fun scheduleExpiry(remainingMs: Long) {
        handler.removeCallbacks(expiryRunnable)
        handler.postDelayed(expiryRunnable, remainingMs + EXPIRY_MARGIN_MS)
    }

    /**
     * Saca al usuario de la zona bloqueada cuando lo pide desde la pantalla de bloqueo. Con los
     * comentarios abiertos, el primer atrás solo los cierra y el reel sigue delante: se cierran y,
     * un momento después, se hace la salida normal sobre lo que queda (un solo toque basta).
     *
     * La acción se hace un instante DESPUÉS: la pausa aún tiene el foco y un atrás lo recibiría
     * ella misma (su botón atrás vuelve a llamar aquí) en lugar de la app. Se ignora una salida
     * mientras hay otra pendiente.
     */
    private fun leave(rule: Rule, packageName: String) {
        if (leavePending) return
        leavePending = true
        leftAt = SystemClock.elapsedRealtime()
        heldRule = null
        // Se usa lo visto en la última mirada: leer la pantalla aquí retrasaría quitar la pausa.
        val sheetOpen = sheetShown
        sheetShown = false
        handler.postDelayed(
            {
                leavePending = false
                leftAt = SystemClock.elapsedRealtime()
                if (sheetOpen) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    exitWhenSheetClosed(rule, packageName, startedAt = SystemClock.elapsedRealtime())
                } else {
                    exit(rule)
                }
            },
            FOCUS_RELEASE_MS,
        )
        scope.launch { stats.recordLeave(packageName) }
    }

    /**
     * Hace la salida normal cuando la hoja ya se ha cerrado, no a ciegas: un atrás durante su
     * cierre se perdería y el usuario se quedaría en el reel. Mira cada [SHEET_POLL_MS] tras un
     * mínimo de [SHEET_CLOSE_MS] y, como tope, sale pasados [SHEET_CLOSE_MAX_MS].
     */
    private fun exitWhenSheetClosed(rule: Rule, packageName: String, startedAt: Long, first: Boolean = true) {
        val delay = if (first) SHEET_CLOSE_MS else SHEET_POLL_MS
        handler.postDelayed(
            {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val closed =
                    rootInActiveWindow
                        ?.let { !engine.covers(SnapshotBuilder.build(it, engine.texts(packageName)), rule) } == true
                if (closed || elapsed >= SHEET_CLOSE_MAX_MS) {
                    leftAt = SystemClock.elapsedRealtime()
                    exit(rule)
                } else {
                    exitWhenSheetClosed(rule, packageName, startedAt, first = false)
                }
            },
            delay,
        )
    }

    private fun exit(rule: Rule) {
        val root = rootInActiveWindow
        // Si no encuentra a dónde ir, al menos vuelve atrás.
        BlockExit.perform(
            rule.action,
            click = { action -> root?.let { clickNode(it, action) } == true },
            back = { performGlobalAction(GLOBAL_ACTION_BACK) },
        )
    }

    /** Pulsa la vista que busca [action] (o su primer padre pulsable); `false` si no la encuentra. */
    private fun clickNode(root: AccessibilityNodeInfo, action: Action.Click): Boolean = clickTarget(root, action)
        ?.let(::clickableSelfOrParent)
        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

    private fun clickableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        BlockExit.clickableSelfOrParent(node, parent = { it.parent }, isClickable = { it.isClickable })

    /** Vista que pulsar para [action]: por identificador o, sin él, por texto o descripción exactos. */
    private fun clickTarget(root: AccessibilityNodeInfo, action: Action.Click): AccessibilityNodeInfo? {
        val target = action.target
        if (target != null) {
            return root.findAccessibilityNodeInfosByViewId(SnapshotBuilder.resourceName("${root.packageName}", target))
                .firstOrNull { BlockExit.matchesById(action, it.text?.toString()) }
        }
        // La búsqueda por texto también encuentra vistas que solo lo contienen: se exige que sea igual.
        return action.text.asSequence()
            .flatMap { text -> root.findAccessibilityNodeInfosByText(text).asSequence().map { text to it } }
            .firstOrNull { (text, node) ->
                BlockExit.matchesText(text, node.text?.toString(), node.contentDescription?.toString())
            }
            ?.second
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val MAX_WAIT_MS = 300L
        const val OVERLAY_POLL_MS = 200L

        /** Con la capa de Inicio se mira más a menudo: debe irse al momento al cambiar de pantalla. */
        const val COVER_POLL_MS = 100L
        const val COVER_EVAL_MIN_MS = 50L

        /** Tras quitar la capa por un toque o un cambio de ventana, cuándo se vuelve a mirar si sigue en «Para ti». */
        const val COVER_RECHECK_MS = 150L

        /** Entre miradas con eventos seguidos en una app con capa (la primera tras un rato es inmediata). */
        const val LEADING_EVAL_MIN_MS = 80L
        const val FOLLOW_UP_MS = 80L
        const val FOLLOW_UP_LATE_MS = 300L

        /** Entre dos desplazamientos hacia atrás para devolver el feed arriba, y cuántos antes de tocar Inicio. */
        const val SCROLL_BACK_GAP_MS = 120L
        const val SCROLL_BACK_TRIES = 3

        /** Lo mínimo entre dos toques de la pestaña de Inicio (recarga el feed). */
        const val TAB_CLICK_GAP_MS = 1_500L
        const val SYSTEM_UI = "com.android.systemui"

        /** Miradas seguidas sin ventana activa tras las que se quita la capa de Inicio (~2 s). */
        const val MAX_NULL_ROOTS = 2

        /** Cada cuánto se mira si ya salió el menú del selector de Inicio al ir a «Siguiendo». */
        const val FEED_SWITCH_POLL_MS = 100L

        // Instagram 454: cabecera clicable de Inicio y menú del selector (capturas del plan 07, T1).
        const val FEED_SELECTOR_ID = "action_bar_title_view"
        const val FEED_MENU_ID = "context_menu_options_list"
        const val FEED_MENU_ITEM_ID = "context_menu_item"
        val FOLLOWING_LABELS = listOf("Siguiendo", "Following")

        /** Mínimo que se espera tras el atrás que cierra la hoja de comentarios, antes de mirar si ya se cerró. */
        const val SHEET_CLOSE_MS = 200L

        /** Cada cuánto se mira si la hoja ya se cerró, y lo máximo que se espera. */
        const val SHEET_POLL_MS = 50L
        const val SHEET_CLOSE_MAX_MS = 600L

        /** Lo que tarda la pausa en soltar el foco al quitarse, antes de hacer la salida. */
        const val FOCUS_RELEASE_MS = 80L
        const val CLICK_SETTLE_MS = 50L
        const val EXPIRY_MARGIN_MS = 100L
        const val UNBIND_SAVE_TIMEOUT_MS = 1_000L

        // Lo que usaba el aviso del resumen del mes de versiones anteriores, para borrarlo.
        const val LEGACY_REPORT_RECEIVER = "io.github.russian03.touchgrass.notification.MonthlyReportReceiver"
        const val LEGACY_REPORT_REQUEST = 1
        const val LEGACY_REPORT_CHANNEL = "monthly_report"
    }
}
