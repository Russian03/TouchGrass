package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.blocking.BlockDecision
import io.github.russian03.touchgrass.core.blocking.BlockExit
import io.github.russian03.touchgrass.core.blocking.BlockPolicy
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.budget.UnlockSession
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
import io.github.russian03.touchgrass.service.ShowConfirmation
import io.github.russian03.touchgrass.testing.FakeTime
import java.io.File
import org.junit.Test

/**
 * Cuándo salta la pausa y adónde lleva «Volver atrás» con las reglas reales de Instagram y
 * TikTok, encadenando las piezas como el servicio. Describe lo que pasa hoy: lo que parece un
 * fallo está marcado como «fallo conocido» y se corrige en un hito posterior.
 */
class BlockingFlowTest {

    private val engine =
        RuleEngine(listOf("instagram", "tiktok").map { RuleRepository.parse(File("$RULES/$it.json").readText()) })

    private val session = ViewerSession()
    private val time = FakeTime()

    /** Último saldo leído; por defecto, sin nada desbloqueado. */
    private var budget = BudgetState()

    /**
     * Una mirada del servicio a [snapshot] a las [at]: apunta lugar y visor, cuenta el scroll que
     * llegue a las [scrollAt] (si lo hay) y decide sin pantalla visible y lejos de cualquier salida.
     */
    private fun look(snapshot: ScreenSnapshot, at: Long, scrollAt: Long? = null): BlockDecision {
        BlockPolicy.track(engine, session, snapshot, at)
        scrollAt?.let(session::onPagerScrolled)
        val now = scrollAt ?: at
        return BlockPolicy.decide(
            engine,
            snapshot,
            session.origin,
            session.scrolled,
            budget,
            time,
            overlayShowing = false,
            nowMs = now,
            leftAtMs = Long.MIN_VALUE / 2,
        )
    }

    private fun ruleOf(decision: BlockDecision) = (decision as BlockDecision.Block).rule.id

    private fun exitOf(decision: BlockDecision) = (decision as BlockDecision.Block).rule.action

    // Pantallas de Instagram (solo identificadores de vista)

    private fun screen(vararg ids: String, selected: String? = null) =
        ScreenSnapshot(IG, ids.map { ViewNode(it, selected = it == selected) })

    private val tabs = arrayOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")

    private val reelsTab = screen(*tabs, "clips_video_container", selected = "clips_tab")

    private val explore = screen(*tabs, "grid_card_layout_container", "recycler_view", selected = "search_tab")

    private val feed = screen(*tabs, "row_feed_profile_header", "reels_tray_container", selected = "feed_tab")

    private val inbox = screen(*tabs, "direct_inbox_container", selected = "direct_tab")

    private val profile = screen("profile_viewpager", "profile_tab_layout", "clips_grid_recyclerview")

    private val chat = screen("message_list", "row_thread_composer_edittext", "thread_fragment_container")

    private val saved = screen("action_bar_button_back", "saved_tab_layout", "saved_view_pager", "recycler_view")

    private val viewer =
        screen("clips_viewer_view_pager", "clips_video_container", "clips_author_username", "gesture_manager")

    // Pantallas de TikTok (identificadores y el texto de las pestañas de arriba)

    private val spanishTabs = listOf("Comunidad", "Siguiendo", "Tienda", "Para ti")

    /** Inicio de TikTok con las pestañas [names] y [selected] elegida. */
    private fun home(selected: String, names: List<String> = spanishTabs): ScreenSnapshot {
        val wanted = engine.texts(TT)[TAB].orEmpty()
        val tabNodes = names.map { ViewNode(TAB, selected = it == selected, text = it.takeIf { t -> t in wanted }) }
        return ScreenSnapshot(TT, tabNodes + ViewNode("viewpager"))
    }

    private val followingExit = Action.Click(target = TAB, text = listOf("Siguiendo", "Following"))

    // Instagram, pestaña Reels

    @Test
    fun `la pestaña Reels salta y vuelve a Inicio`() {
        val decision = look(reelsTab, at = 0)
        assertThat(ruleOf(decision)).isEqualTo("reels_tab")
        assertThat(exitOf(decision)).isEqualTo(Action.Click(target = "feed_tab", text = emptyList()))
    }

    @Test
    fun `con el visor también en pantalla gana la pestaña Reels`() {
        val decision = look(screen(*tabs, "clips_viewer_view_pager", selected = "clips_tab"), at = 0)
        assertThat(ruleOf(decision)).isEqualTo("reels_tab")
    }

    // Instagram, visor

    @Test
    fun `el visor abierto desde Explorar salta y hace atrás`() {
        look(explore, at = 0)
        val decision = look(viewer, at = 100)
        assertThat(ruleOf(decision)).isEqualTo("reels_viewer")
        assertThat(exitOf(decision)).isEqualTo(Action.Back)
    }

    @Test
    fun `el visor abierto desde Mensajes salta`() {
        look(inbox, at = 0)
        assertThat(ruleOf(look(viewer, at = 100))).isEqualTo("reels_viewer")
    }

    @Test
    fun `el visor sin origen conocido salta`() {
        // Servicio recién arrancado o reel abierto desde un enlace.
        assertThat(ruleOf(look(viewer, at = 0))).isEqualTo("reels_viewer")
    }

    @Test
    fun `el visor abierto desde un perfil queda libre aunque se pase de reel`() {
        look(profile, at = 0)
        assertThat(look(viewer, at = 100)).isEqualTo(BlockDecision.Free(null))
        assertThat(look(viewer, at = 200, scrollAt = 5_000)).isEqualTo(BlockDecision.Free(null))
    }

    /** El visor abierto desde [origin] queda libre hasta que se pasa de reel. */
    private fun assertFreeUntilScroll(origin: ScreenSnapshot) {
        look(origin, at = 0)
        assertThat(look(viewer, at = 100)).isEqualTo(BlockDecision.Free(null))
        // Un scroll a menos de 500 ms de ver el visor es el propio visor al abrirse.
        assertThat(look(viewer, at = 200, scrollAt = 100 + SETTLE_MS - 1)).isEqualTo(BlockDecision.Free(null))
        assertThat(ruleOf(look(viewer, at = 700, scrollAt = 100 + SETTLE_MS))).isEqualTo("reels_viewer")
    }

    @Test
    fun `el visor abierto desde un chat queda libre hasta pasar de reel`() = assertFreeUntilScroll(chat)

    @Test
    fun `el visor abierto desde Inicio queda libre hasta pasar de reel`() = assertFreeUntilScroll(feed)

    @Test
    fun `el visor abierto desde Guardados queda libre hasta pasar de reel`() = assertFreeUntilScroll(saved)

    @Test
    fun `fallo conocido - el visor con Inicio seleccionado debajo nunca cuenta el scroll`() {
        // Fallo conocido, pendiente de arreglar: cada mirada reconoce otra vez el lugar «feed» y reinicia el visor,
        // así que un scroll a 500 ms de la primera mirada pero cerca de la última no cuenta.
        val viewerOverFeed = screen(*tabs, "clips_viewer_view_pager", selected = "feed_tab")
        look(viewerOverFeed, at = 0)
        assertThat(look(viewerOverFeed, at = 400, scrollAt = SETTLE_MS + 100)).isEqualTo(BlockDecision.Free(null))
        assertThat(session.origin).isEqualTo("feed")
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `fallo conocido - el origen de un perfil sobrevive a pasar por TikTok`() {
        // Fallo conocido, pendiente de arreglar: la sesión es una sola para todas las apps.
        look(profile, at = 0)
        look(home("Para ti"), at = 100)
        assertThat(look(viewer, at = 200, scrollAt = 5_000)).isEqualTo(BlockDecision.Free(null))
    }

    // TikTok

    @Test
    fun `Para ti salta y lleva a Siguiendo`() {
        val decision = look(home("Para ti"), at = 0)
        assertThat(ruleOf(decision)).isEqualTo("for_you_tab")
        assertThat(exitOf(decision)).isEqualTo(followingExit)
    }

    @Test
    fun `For You en inglés salta`() {
        val decision = look(home("For You", names = listOf("Following", "For You")), at = 0)
        assertThat(ruleOf(decision)).isEqualTo("for_you_tab")
        assertThat(exitOf(decision)).isEqualTo(followingExit)
    }

    @Test
    fun `la salida de Para ti solo pulsa la pestaña Siguiendo`() {
        val exit = exitOf(look(home("Para ti"), at = 0)) as Action.Click
        listOf("Siguiendo", "Following").forEach { assertThat(BlockExit.matchesById(exit, it)).isTrue() }
        listOf("Para ti", "Comunidad", "Tienda", "For You", "Siguiendo 12", null).forEach {
            assertThat(BlockExit.matchesById(exit, it)).isFalse()
        }
    }

    @Test
    fun `Following en inglés queda libre`() {
        val s = home("Following", names = listOf("Following", "For You"))
        assertThat(look(s, at = 0)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `tras salir de Para ti a Siguiendo no vuelve a saltar`() {
        assertThat(look(home("Para ti"), at = 0)).isInstanceOf(BlockDecision.Block::class.java)
        assertThat(look(home("Siguiendo"), at = 1_500)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `Comunidad salta y lleva a Siguiendo`() {
        val decision = look(home("Comunidad"), at = 0)
        assertThat(ruleOf(decision)).isEqualTo("community_tab")
        assertThat(exitOf(decision)).isEqualTo(followingExit)
    }

    @Test
    fun `Siguiendo queda libre`() {
        assertThat(look(home("Siguiendo"), at = 0)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `fallo conocido - Community en inglés queda libre`() {
        // Fallo conocido, pendiente de arreglar: la regla solo reconoce «Comunidad».
        val decision = look(home("Community", names = listOf("Community", "Following", "For You")), at = 0)
        assertThat(decision).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `el origen de Instagram no deja libre nada en TikTok`() {
        look(profile, at = 0)
        assertThat(ruleOf(look(home("Para ti"), at = 100))).isEqualTo("for_you_tab")
    }

    // Desbloqueo

    @Test
    fun `un desbloqueo de Instagram la deja libre pero no a TikTok`() {
        val now = time.now()
        val unlock =
            UnlockSession(
                packageName = IG,
                minutes = 5,
                startWallMs = now.wallMs - 60_000,
                startElapsedMs = now.elapsedMs - 60_000,
                bootCount = now.bootCount,
            )
        budget = BudgetState(limitMinutes = 10, usedMinutes = 5, unlock = unlock)
        val remaining = BudgetPolicy.unlockRemainingMs(unlock, now)
        assertThat(remaining).isEqualTo(4 * 60_000)
        assertThat(look(reelsTab, at = 0)).isEqualTo(BlockDecision.Free(remaining))
        assertThat(ruleOf(look(home("Para ti"), at = 100))).isEqualTo("for_you_tab")
    }

    // Confirmación

    private val confirmation = ShowConfirmation()

    /** Si la pausa se pondría tras la mirada a [snapshot] a las [at]. */
    private fun confirmed(snapshot: ScreenSnapshot, at: Long): Boolean {
        val decision = look(snapshot, at) as BlockDecision.Block
        return confirmation.onMatch(decision.confirmationKey, at)
    }

    @Test
    fun `la misma regla en dos miradas no se confirma antes de tiempo`() {
        assertThat(confirmed(reelsTab, at = 0)).isFalse()
        assertThat(confirmed(reelsTab, at = ShowConfirmation.CONFIRM_MS - 1)).isFalse()
    }

    @Test
    fun `la misma regla en dos miradas se confirma a tiempo`() {
        assertThat(confirmed(reelsTab, at = 0)).isFalse()
        assertThat(confirmed(reelsTab, at = ShowConfirmation.CONFIRM_MS)).isTrue()
    }

    @Test
    fun `si cambia la regla entre miradas la espera vuelve a empezar`() {
        assertThat(confirmed(reelsTab, at = 0)).isFalse()
        assertThat(confirmed(home("Para ti"), at = 60)).isFalse()
        assertThat(confirmed(home("Para ti"), at = ShowConfirmation.CONFIRM_MS)).isFalse()
        assertThat(confirmed(home("Para ti"), at = 60 + ShowConfirmation.CONFIRM_MS)).isTrue()
    }

    // Salida

    /** Lo que hace «Volver atrás» con la acción de [decision], en orden, si pulsar da [clickWorks]. */
    private fun leave(decision: BlockDecision, clickWorks: Boolean): List<String> {
        val calls = mutableListOf<String>()
        BlockExit.perform(
            exitOf(decision),
            click = {
                calls += "click"
                clickWorks
            },
            back = {
                calls += "back"
                true
            },
        )
        return calls
    }

    @Test
    fun `desde el visor se hace atrás sin intentar pulsar`() {
        assertThat(leave(look(viewer, at = 0), clickWorks = true)).containsExactly("back")
    }

    @Test
    fun `desde la pestaña Reels y TikTok se pulsa el destino`() {
        listOf(reelsTab, home("Para ti"), home("Comunidad")).forEach {
            assertThat(leave(look(it, at = 0), clickWorks = true)).containsExactly("click")
        }
    }

    @Test
    fun `si no se encuentra el destino se hace atrás`() {
        listOf(reelsTab, home("Para ti"), home("Comunidad")).forEach {
            assertThat(leave(look(it, at = 0), clickWorks = false)).containsExactly("click", "back").inOrder()
        }
    }

    private companion object {
        const val RULES = "src/main/assets/rules"
        const val IG = "com.instagram.android"
        const val TT = "com.zhiliaoapp.musically"
        const val TAB = "android:id/text1"
        const val SETTLE_MS = 500L
    }
}
