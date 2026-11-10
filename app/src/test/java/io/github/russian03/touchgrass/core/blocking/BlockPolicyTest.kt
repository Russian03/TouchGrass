package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.blocking.BlockPolicy.LEAVE_COOLDOWN_MS
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.budget.TimeSnapshot
import io.github.russian03.touchgrass.core.budget.TimeSource
import io.github.russian03.touchgrass.core.budget.UnlockSession
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Allowance
import io.github.russian03.touchgrass.core.rules.Condition
import io.github.russian03.touchgrass.core.rules.Match
import io.github.russian03.touchgrass.core.rules.Place
import io.github.russian03.touchgrass.core.rules.PostMaskSpec
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.rules.RuleSet
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
import io.github.russian03.touchgrass.testing.FakeTime
import org.junit.Test

class BlockPolicyTest {

    private val tabRule =
        Rule(
            id = "tab",
            description = "Pestaña",
            match = Match(allOf = listOf(Condition("video_tab", selected = true))),
            action = Action.Click("home_tab"),
            coveredBy = Match(allOf = listOf(Condition("sheet"))),
        )

    private val viewerRule =
        Rule(
            id = "viewer",
            description = "Visor",
            match = Match(allOf = listOf(Condition("pager"))),
            action = Action.Back,
            allowFrom = listOf(Allowance("profile"), Allowance("chat", untilScroll = true)),
            pager = "pager",
        )

    private val otherRule =
        Rule(
            id = "feed",
            description = "Otra app",
            match = Match(allOf = listOf(Condition("feed"))),
            action = Action.Back,
            coveredBy = Match(allOf = listOf(Condition("sheet"))),
        )

    private val places =
        listOf(
            Place("chat", Match(allOf = listOf(Condition("thread")))),
            Place("profile", Match(allOf = listOf(Condition("profile_header")))),
        )

    private val engine =
        RuleEngine(listOf(RuleSet(1, APP, listOf(tabRule, viewerRule), places), RuleSet(1, OTHER, listOf(otherRule))))

    /** Reloj de pared que cuenta las veces que se consulta. */
    private class CountingTime(private val time: TimeSource = FakeTime()) : TimeSource {
        var calls = 0
            private set

        override fun now(): TimeSnapshot {
            calls++
            return time.now()
        }
    }

    private val time = CountingTime()

    private fun snapshot(vararg nodes: ViewNode, pkg: String = APP) = ScreenSnapshot(pkg, nodes.toList())

    private val tab = snapshot(ViewNode("home_tab"), ViewNode("video_tab", selected = true))
    private val viewer = snapshot(ViewNode("pager"))

    /** Desbloqueo de un minuto para [pkg] empezado hace [agoMs] según [FakeTime]. */
    private fun unlocked(pkg: String, agoMs: Long): BudgetState {
        val now = FakeTime().now()
        val session =
            UnlockSession(
                packageName = pkg,
                minutes = 1,
                startWallMs = now.wallMs - agoMs,
                startElapsedMs = now.elapsedMs - agoMs,
                bootCount = now.bootCount,
            )
        return BudgetState(limitMinutes = 10, usedMinutes = 1, unlock = session)
    }

    private fun decide(
        snapshot: ScreenSnapshot,
        origin: String? = null,
        scrolled: Boolean = false,
        budget: BudgetState? = BudgetState(),
        overlayShowing: Boolean = false,
        nowMs: Long = NOW,
        leftAtMs: Long = NOW - 10_000,
        held: Rule? = null,
    ) = BlockPolicy.decide(engine, snapshot, origin, scrolled, budget, time, overlayShowing, nowMs, leftAtMs, held)

    // Decisión

    @Test
    fun `sin regla que case queda libre y no mira el reloj`() {
        val decision = decide(snapshot(ViewNode("home_tab", selected = true)), budget = unlocked(APP, agoMs = 1_000))
        assertThat(decision).isEqualTo(BlockDecision.Free(null))
        assertThat(time.calls).isEqualTo(0)
    }

    @Test
    fun `regla que casa lejos de la salida bloquea con su clave`() {
        assertThat(decide(tab)).isEqualTo(BlockDecision.Block(tabRule, "$APP/tab"))
    }

    @Test
    fun `con la pantalla ya visible no cambia nada`() {
        assertThat(decide(tab, overlayShowing = true)).isEqualTo(BlockDecision.Keep)
    }

    // Enfriamiento

    @Test
    fun `justo antes de acabar el enfriamiento no cambia nada`() {
        assertThat(decide(tab, leftAtMs = NOW - (LEAVE_COOLDOWN_MS - 1))).isEqualTo(BlockDecision.Keep)
    }

    @Test
    fun `lo que queda de enfriamiento`() {
        assertThat(BlockPolicy.cooldownRemainingMs(nowMs = 5_000, leftAtMs = 5_000)).isEqualTo(LEAVE_COOLDOWN_MS)
        assertThat(BlockPolicy.cooldownRemainingMs(nowMs = 5_999, leftAtMs = 5_000)).isEqualTo(1)
        assertThat(BlockPolicy.cooldownRemainingMs(nowMs = 6_000, leftAtMs = 5_000)).isEqualTo(0)
        assertThat(BlockPolicy.cooldownRemainingMs(nowMs = 0, leftAtMs = Long.MIN_VALUE / 2)).isEqualTo(0)
    }

    @Test
    fun `al acabar el enfriamiento bloquea`() {
        assertThat(decide(tab, leftAtMs = NOW - LEAVE_COOLDOWN_MS)).isInstanceOf(BlockDecision.Block::class.java)
    }

    @Test
    fun `sin haber salido nunca bloquea sin desbordar`() {
        val decision = decide(tab, nowMs = 0, leftAtMs = Long.MIN_VALUE / 2)
        assertThat(decision).isInstanceOf(BlockDecision.Block::class.java)
    }

    // Hoja encima

    private val sheet = snapshot(ViewNode("sheet"))

    @Test
    fun `la hoja sobre una regla que casaba bloquea`() {
        assertThat(decide(sheet, held = tabRule)).isEqualTo(BlockDecision.Block(tabRule, "$APP/tab"))
    }

    @Test
    fun `la hoja sin regla anterior queda libre`() {
        assertThat(decide(sheet, held = null)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `la hoja con un desbloqueo queda libre`() {
        val decision = decide(sheet, held = tabRule, budget = unlocked(APP, agoMs = 1_000))
        assertThat(decision).isInstanceOf(BlockDecision.Free::class.java)
    }

    @Test
    fun `la hoja de otra app no hereda la regla`() {
        assertThat(decide(snapshot(ViewNode("sheet"), pkg = OTHER), held = tabRule)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `una hoja que no es la de la regla no la hereda`() {
        assertThat(decide(snapshot(ViewNode("otra_hoja")), held = tabRule)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `la hoja con la pausa visible no cambia nada`() {
        assertThat(decide(sheet, held = tabRule, overlayShowing = true)).isEqualTo(BlockDecision.Keep)
    }

    @Test
    fun `solo se ve la hoja cuando no casa ninguna regla y la hoja cubre la retenida`() {
        assertThat(BlockPolicy.isOverSheet(engine, sheet, null, false, held = tabRule)).isTrue()
        assertThat(BlockPolicy.isOverSheet(engine, tab, null, false, held = tabRule)).isFalse()
        assertThat(BlockPolicy.isOverSheet(engine, sheet, null, false, held = null)).isFalse()
        assertThat(BlockPolicy.isOverSheet(engine, snapshot(ViewNode("otra")), null, false, held = tabRule)).isFalse()
    }

    // Desbloqueo

    @Test
    fun `un desbloqueo de la misma app deja libre con lo que le queda`() {
        val budget = unlocked(APP, agoMs = 20_000)
        val remaining = BudgetPolicy.unlockRemainingMs(budget.unlock!!, FakeTime().now())
        assertThat(remaining).isEqualTo(40_000)
        assertThat(decide(tab, budget = budget)).isEqualTo(BlockDecision.Free(remaining))
        assertThat(time.calls).isEqualTo(1)
    }

    @Test
    fun `un desbloqueo de otra app no anula la regla`() {
        assertThat(decide(tab, budget = unlocked(OTHER, agoMs = 1_000))).isInstanceOf(BlockDecision.Block::class.java)
    }

    @Test
    fun `un desbloqueo caducado no anula la regla`() {
        assertThat(decide(tab, budget = unlocked(APP, agoMs = 60_000))).isInstanceOf(BlockDecision.Block::class.java)
    }

    @Test
    fun `sin saldo leído bloquea y no mira el reloj`() {
        assertThat(decide(tab, budget = null)).isInstanceOf(BlockDecision.Block::class.java)
        assertThat(time.calls).isEqualTo(0)
    }

    @Test
    fun `el desbloqueo gana a la pantalla visible y al enfriamiento`() {
        val budget = unlocked(APP, agoMs = 1_000)
        assertThat(decide(tab, budget = budget, overlayShowing = true)).isInstanceOf(BlockDecision.Free::class.java)
        assertThat(decide(tab, budget = budget, leftAtMs = NOW)).isInstanceOf(BlockDecision.Free::class.java)
    }

    // Origen

    @Test
    fun `desde un lugar permitido hasta pasar de página queda libre`() {
        assertThat(decide(viewer, origin = "chat", scrolled = false)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `desde un lugar permitido tras pasar de página bloquea`() {
        val decision = decide(viewer, origin = "chat", scrolled = true)
        assertThat(decision).isEqualTo(BlockDecision.Block(viewerRule, "$APP/viewer"))
    }

    // Sesión

    private val chat = snapshot(ViewNode("thread"))
    private val profile = snapshot(ViewNode("profile_header"))

    @Test
    fun `un lugar en pantalla pasa a ser el origen y olvida el scroll`() {
        val session = ViewerSession()
        BlockPolicy.track(engine, session, chat, nowMs = 0)
        BlockPolicy.track(engine, session, viewer, nowMs = 0)
        session.onPagerScrolled(SETTLE_MS)
        BlockPolicy.track(engine, session, profile, nowMs = SETTLE_MS)
        assertThat(session.origin).isEqualTo("profile")
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `una pantalla sin lugar no cambia el origen`() {
        val session = ViewerSession()
        BlockPolicy.track(engine, session, chat, nowMs = 0)
        BlockPolicy.track(engine, session, tab, nowMs = 0)
        assertThat(session.origin).isEqualTo("chat")
    }

    @Test
    fun `el visor en pantalla empieza a contar el scroll desde esa mirada`() {
        val session = ViewerSession()
        BlockPolicy.track(engine, session, viewer, nowMs = 1_000)
        session.onPagerScrolled(1_000 + SETTLE_MS - 1)
        assertThat(session.scrolled).isFalse()
        session.onPagerScrolled(1_000 + SETTLE_MS)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `sin el visor en pantalla el scroll no cuenta`() {
        val session = ViewerSession()
        BlockPolicy.track(engine, session, tab, nowMs = 0)
        session.onPagerScrolled(10_000)
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `la sesión es una sola para todas las apps`() {
        // Fallo conocido: mirar otra app (sin lugares) conserva el origen de la anterior.
        val session = ViewerSession()
        BlockPolicy.track(engine, session, profile, nowMs = 0)
        BlockPolicy.track(engine, session, snapshot(ViewNode("feed"), pkg = OTHER), nowMs = 100)
        assertThat(session.origin).isEqualTo("profile")
        assertThat(decide(viewer, origin = session.origin, scrolled = true)).isEqualTo(BlockDecision.Free(null))
    }

    // Regla con mask (tapar posts uno a uno, sin pausa)

    private val maskRule =
        Rule(
            id = "suggested",
            description = "Posts sugeridos",
            match = Match(allOf = listOf(Condition("home_tab", selected = true), Condition("list"))),
            action = Action.Click("home_tab"),
            mask = PostMaskSpec(list = "list", header = "header", anchor = "follow"),
        )

    private val maskEngine = RuleEngine(listOf(RuleSet(1, APP, listOf(maskRule))))
    private val feedList = Bounds(0, 118, 1220, 2504)
    private val suggestedRows = listOf(ListRow(Bounds(0, 300, 1220, 1300), header = true, marked = true))
    private val followedRows = listOf(ListRow(Bounds(0, 300, 1220, 1300), header = true, marked = false))

    private fun homeWith(rows: List<ListRow>) =
        ScreenSnapshot(APP, listOf(ViewNode("home_tab", selected = true), ViewNode("list")), feedList, rows)

    private fun decideMask(
        snapshot: ScreenSnapshot,
        budget: BudgetState? = BudgetState(),
        overlayShowing: Boolean = false,
        leftAtMs: Long = NOW - 10_000,
        leadingMarked: Boolean = false,
    ) = BlockPolicy.decide(
        maskEngine, snapshot, null, false, budget, time, overlayShowing, NOW, leftAtMs, leadingMarked = leadingMarked,
    )

    @Test
    fun `una regla con mask da Mask con sus zonas`() {
        val decision = decideMask(homeWith(suggestedRows))

        assertThat(decision).isEqualTo(BlockDecision.Mask(maskRule, listOf(Bounds(0, 300, 1220, 1300))))
    }

    @Test
    fun `una regla con mask sin zonas deja libre`() {
        assertThat(decideMask(homeWith(followedRows))).isEqualTo(BlockDecision.Free(null))
        assertThat(decideMask(homeWith(emptyList()))).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `con un desbloqueo de la app una regla con mask deja libre con lo que queda`() {
        val budget = unlocked(APP, agoMs = 20_000)

        assertThat(decideMask(homeWith(suggestedRows), budget = budget)).isEqualTo(BlockDecision.Free(40_000))
    }

    @Test
    fun `una regla con mask no espera al enfriamiento ni a la pausa visible`() {
        val decision = decideMask(homeWith(suggestedRows), overlayShowing = true, leftAtMs = NOW - 10)

        assertThat(decision).isInstanceOf(BlockDecision.Mask::class.java)
    }

    @Test
    fun `con el post de arriba sugerido la parte que queda a la vista se tapa`() {
        val rows = listOf(ListRow(Bounds(0, 118, 1220, 480), header = false, marked = false))

        assertThat(decideMask(homeWith(rows), leadingMarked = true))
            .isEqualTo(BlockDecision.Mask(maskRule, listOf(Bounds(0, 118, 1220, 480))))
    }

    private companion object {
        const val SETTLE_MS = 500L
        const val APP = "com.example.videos"
        const val OTHER = "com.example.other"
        const val NOW = 100_000L
    }
}
