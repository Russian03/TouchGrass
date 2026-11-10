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
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow
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

    /** Regla del último `Block`, como la guarda el servicio para las hojas que tapan la zona. */
    private var held: Rule? = null

    /**
     * Una mirada del servicio a [snapshot] a las [at]: apunta lugar y visor, cuenta el scroll que
     * llegue a las [scrollAt] (si lo hay) y decide sin pantalla visible y lejos de cualquier salida.
     */
    private fun look(snapshot: ScreenSnapshot, at: Long, scrollAt: Long? = null): BlockDecision {
        BlockPolicy.track(engine, session, snapshot, at)
        scrollAt?.let(session::onPagerScrolled)
        val now = scrollAt ?: at
        val decision =
            BlockPolicy.decide(
                engine,
                snapshot,
                session.origin,
                session.scrolled,
                budget,
                time,
                overlayShowing = false,
                nowMs = now,
                leftAtMs = Long.MIN_VALUE / 2,
                held = held,
            )
        when (decision) {
            is BlockDecision.Block -> held = decision.rule
            is BlockDecision.Free, is BlockDecision.Mask -> held = null
            BlockDecision.Keep, is BlockDecision.SilentBack -> Unit
        }
        return decision
    }

    private fun ruleOf(decision: BlockDecision) = (decision as BlockDecision.Block).rule.id

    private fun silentRuleOf(decision: BlockDecision) = (decision as BlockDecision.SilentBack).rule.id

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

    // Instagram, mantener pulsado en Buscar

    /** Vista previa de un reel en Buscar (Instagram 454.0.0.54.77): cuadrícula, pestañas y ids propios. */
    private val peek =
        screen(
            *tabs,
            "media_group",
            "music_attribution_label_marquee",
            "row_feed_profile_header",
            "row_feed_photo_profile_name",
            "explore_action_bar_right_button_stub",
            "grid_card_layout_container",
            "recycler_view",
            selected = "search_tab",
        )

    private val peekOverProfile =
        screen("media_group", "row_feed_profile_header", "profile_viewpager", "profile_tab_layout")

    @Test
    fun `mantener pulsado un reel en Buscar pide un atras silencioso sin pausa`() {
        look(explore, at = 0)
        val decision = look(peek, at = 100)
        assertThat(decision).isNotInstanceOf(BlockDecision.Block::class.java)
        assertThat(silentRuleOf(decision)).isEqualTo("explore_peek")
    }

    @Test
    fun `al soltar la vista previa la cuadricula queda libre`() {
        look(explore, at = 0)
        look(peek, at = 100)
        assertThat(look(explore, at = 200)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `tocar el reel tras la vista previa sigue bloqueado`() {
        look(explore, at = 0)
        look(peek, at = 100)
        look(explore, at = 200)
        assertThat(ruleOf(look(viewer, at = 300))).isEqualTo("reels_viewer")
    }

    @Test
    fun `mantener pulsado en un perfil queda libre`() {
        look(profile, at = 0)
        assertThat(look(peekOverProfile, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `con un desbloqueo la vista previa queda libre`() {
        look(explore, at = 0)
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
        assertThat(look(peek, at = 100)).isEqualTo(BlockDecision.Free(BudgetPolicy.unlockRemainingMs(unlock, now)))
    }

    /** Ids reales capturados en el móvil (Instagram 454.0.0.54.77); `*` marca el seleccionado. */
    private fun captured(ids: String) = ScreenSnapshot(
        IG,
        ids.split(",").map { ViewNode(it.removeSuffix("*"), selected = it.endsWith("*")) },
    )

    private val realGrid = captured(
        "profile_tab,search_tab*,direct_tab,clips_tab,feed_tab,swipeable_tab_view_pager,recycler_view," +
            "layout_container," +
            "image_preview,grid_card_layout_container,image_button,grid_card_layout_container,image_button",
    )

    private val realPeek = captured(
        "media_group,indicator_container,music_attribution_label_marquee,row_feed_profile_header,secondary_label," +
            "row_feed_photo_profile_name,profile_tab,search_tab*,direct_tab,clips_tab,feed_tab," +
            "swipeable_tab_view_pager," +
            "recycler_view,layout_container,image_preview,grid_card_layout_container,image_button",
    )

    /** Menú contextual que queda al soltar (snapshot real de accesibilidad: sin `context_menu` ni pestañas). */
    private val realMenu =
        captured(
            "context_menu_options_list," +
                "context_menu_item,context_menu_item_label,context_menu_item,context_menu_item_label," +
                "context_menu_item,context_menu_item_label,context_menu_item,context_menu_item_label," +
                "context_menu_item,context_menu_item_label",
        )

    @Test
    fun `la secuencia real cuadricula vista previa menu bloquea la vista previa`() {
        look(realGrid, at = 0)
        assertThat(silentRuleOf(look(realPeek, at = 100))).isEqualTo("explore_peek")
        assertThat(silentRuleOf(look(realPeek, at = 700))).isEqualTo("explore_peek")
        assertThat(silentRuleOf(look(realMenu, at = 800))).isEqualTo("explore_menu")
    }

    @Test
    fun `la vista previa y el menu tambien piden atras silencioso con el dedo o sin el`() {
        look(realGrid, at = 0)
        assertThat(look(realPeek, at = 100)).isInstanceOf(BlockDecision.SilentBack::class.java)
        assertThat(look(realMenu, at = 800)).isInstanceOf(BlockDecision.SilentBack::class.java)
    }

    @Test
    fun `la pausa del visor sigue siendo una pausa normal`() {
        val decision = look(viewer, at = 100)
        assertThat(ruleOf(decision)).isEqualTo("reels_viewer")
        assertThat((decision as BlockDecision.Block).rule.silentBack).isFalse()
    }

    @Test
    fun `el atras silencioso se pide aunque ya hubiera una pausa o una salida reciente`() {
        look(realGrid, at = 0)
        val decision =
            BlockPolicy.decide(
                engine, realPeek, session.origin, session.scrolled, budget, time,
                overlayShowing = true, nowMs = 100, leftAtMs = Long.MIN_VALUE / 2,
            )
        assertThat(decision).isInstanceOf(BlockDecision.SilentBack::class.java)
    }

    @Test
    fun `tras volver atras desde una pausa no se pide un atras silencioso durante el enfriamiento`() {
        look(realGrid, at = 0)
        val decision =
            BlockPolicy.decide(
                engine, realPeek, session.origin, session.scrolled, budget, time,
                overlayShowing = false, nowMs = 100, leftAtMs = 0,
            )
        assertThat(decision).isEqualTo(BlockDecision.Keep)
    }

    @Test
    fun `el menu contextual fuera de Buscar queda libre`() {
        listOf(feed, profile, saved, chat, inbox).forEach { place ->
            look(place, at = 0)
            assertThat(look(realMenu, at = 100)).isEqualTo(BlockDecision.Free(null))
        }
    }

    @Test
    fun `el atras de la vista previa se confirma como el resto`() {
        look(explore, at = 0)
        assertThat(confirmed(peek, at = 0)).isFalse()
        assertThat(confirmed(peek, at = ShowConfirmation.CONFIRM_MS)).isTrue()
    }

    // Instagram, Inicio: solo seguidos (ids reales, Instagram 454.0.0.54.77)

    private val homeTop = "profile_tab,search_tab,direct_tab,notification,clips_tab,feed_tab*,swipeable_tab_view_pager"

    private val homeLoading =
        captured("$homeTop,action_bar_title_view,title_text,action_bar_left_button,android:id/list,shimmer_content")

    /** Filas de la lista de Inicio (caso R2, Instagram 454.0.0.54.77): solo la cabecera lleva `header`. */
    private fun rows(vararg rows: ListRow) = rows.toList()

    private val homeList = Bounds(0, 118, 1220, 2504)

    private fun row(top: Int, bottom: Int, header: Boolean = false, marked: Boolean = false) =
        ListRow(Bounds(0, top, 1220, bottom), header, marked)

    private fun ScreenSnapshot.withRows(rows: List<ListRow>) = copy(listBounds = homeList, rows = rows)

    private fun zonesOf(decision: BlockDecision) = (decision as BlockDecision.Mask).zones

    private val homeFollowed =
        captured(
            "$homeTop,android:id/list,row_feed_view_group_buttons,carousel_media_group,carousel_viewpager," +
                "carousel_image_media_group,carousel_image,row_feed_profile_header,media_option_button," +
                "row_feed_photo_profile_name,row_feed_photo_profile_imageview",
        ).withRows(rows(row(300, 469, header = true), row(469, 1300)))

    private val homeSuggested =
        captured(
            "$homeTop,android:id/list,row_feed_profile_header,media_option_button,inline_follow_button," +
                "secondary_label,row_feed_photo_profile_name,row_feed_photo_profile_imageview,video_container," +
                "media_group,row_feed_view_group_buttons,row_feed_button_save,row_feed_button_share," +
                "reposts_ufi_icon,row_feed_button_comment,row_feed_button_like",
        ).withRows(rows(row(300, 469, header = true), row(469, 1300, header = true, marked = true), row(1300, 2000)))

    @Test
    fun `un post sugerido en Inicio se tapa sin pausa`() {
        val decision = look(homeSuggested, at = 0)

        assertThat(decision).isInstanceOf(BlockDecision.Mask::class.java)
        assertThat(zonesOf(decision)).containsExactly(Bounds(0, 469, 1220, 2000))
    }

    @Test
    fun `bajar de un post de seguido a uno sugerido lo tapa`() {
        assertThat(look(homeFollowed, at = 0)).isEqualTo(BlockDecision.Free(null))
        assertThat(look(homeSuggested, at = 100)).isInstanceOf(BlockDecision.Mask::class.java)
    }

    @Test
    fun `el destino de la salida de Inicio no esta bloqueado`() {
        assertThat(look(homeLoading, at = 0)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `con un desbloqueo Inicio con sugeridos queda libre`() {
        look(homeFollowed, at = 0)
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
        assertThat(look(homeSuggested, at = 100))
            .isEqualTo(BlockDecision.Free(BudgetPolicy.unlockRemainingMs(unlock, now)))
    }

    @Test
    fun `los comentarios abiertos desde Inicio quedan libres`() {
        look(homeSuggested, at = 0)
        assertThat(look(commentsIg, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de un post de seguido quedan libres`() {
        look(homeFollowed, at = 0)
        assertThat(look(commentsIg, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `un reel abierto desde Inicio sigue libre hasta pasar de reel`() = assertFreeUntilScroll(homeFollowed)

    @Test
    fun `el tapado de Inicio no espera confirmacion`() {
        assertThat(look(homeSuggested, at = 0)).isInstanceOf(BlockDecision.Mask::class.java)
    }

    @Test
    fun `la pestaña Reels con sugeridos arriba no entra en bucle con la salida`() {
        assertThat(ruleOf(look(reelsTab, at = 0))).isEqualTo("reels_tab")
        assertThat(look(homeSuggested, at = 100)).isInstanceOf(BlockDecision.Mask::class.java)
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

    // Comentarios (Instagram 454 y TikTok 47.7.3: al abrirlos, lo único que se ve es la hoja)

    private val commentsIg =
        screen(
            "bottom_sheet_container",
            "comment_composer_parent_updated",
            "layout_comment_thread_edittext_multiline",
            "main_list_view",
            "sticky_header_list",
        )

    private val commentsTt =

        ScreenSnapshot(TT, listOf("v3k", "pyh", "ek4", "btm", "env", "ly5", "m5h", "el7", "n13", "uac").map(::ViewNode))

    @Test
    fun `los comentarios de un reel de Explorar siguen bloqueados`() {
        look(explore, at = 0)
        look(viewer, at = 100)
        assertThat(ruleOf(look(commentsIg, at = 200))).isEqualTo("reels_viewer")
    }

    @Test
    fun `los comentarios en la pestaña Reels siguen bloqueados`() {
        look(reelsTab, at = 0)
        assertThat(ruleOf(look(commentsIg, at = 100))).isEqualTo("reels_tab")
    }

    @Test
    fun `los comentarios de un post de Inicio quedan libres`() {
        look(feed, at = 0)
        assertThat(look(commentsIg, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios sin haber visto nada antes quedan libres`() {
        assertThat(look(commentsIg, at = 0)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de un reel de un perfil quedan libres aunque se pase de reel`() {
        look(profile, at = 0)
        look(viewer, at = 100)
        assertThat(look(commentsIg, at = 200)).isEqualTo(BlockDecision.Free(null))
        assertThat(look(viewer, at = 300, scrollAt = 5_000)).isEqualTo(BlockDecision.Free(null))
        assertThat(look(commentsIg, at = 5_100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de un reel de Inicio quedan libres antes de pasar de reel`() {
        look(feed, at = 0)
        look(viewer, at = 100)
        assertThat(look(commentsIg, at = 200)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de Para ti siguen bloqueados y salen a Siguiendo`() {
        look(home("Para ti"), at = 0)
        val decision = look(commentsTt, at = 100)
        assertThat(ruleOf(decision)).isEqualTo("for_you_tab")
        assertThat(exitOf(decision)).isEqualTo(followingExit)
    }

    @Test
    fun `una pantalla vacia mientras se abren los comentarios no quita la regla`() {
        look(home("Para ti"), at = 0)
        assertThat(ruleOf(look(ScreenSnapshot(TT, emptyList()), at = 100))).isEqualTo("for_you_tab")
        assertThat(ruleOf(look(commentsTt, at = 200))).isEqualTo("for_you_tab")
    }

    @Test
    fun `una pantalla vacia sin regla anterior queda libre`() {
        assertThat(look(ScreenSnapshot(TT, emptyList()), at = 0)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de Comunidad siguen bloqueados`() {
        look(home("Comunidad"), at = 0)
        assertThat(ruleOf(look(commentsTt, at = 100))).isEqualTo("community_tab")
    }

    @Test
    fun `los comentarios de Siguiendo quedan libres`() {
        look(home("Siguiendo"), at = 0)
        assertThat(look(commentsTt, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `los comentarios de una app no heredan la regla de la otra`() {
        look(reelsTab, at = 0)
        assertThat(look(commentsTt, at = 100)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `al cerrar los comentarios sobre una zona libre no se bloquea`() {
        look(home("Para ti"), at = 0)
        look(commentsTt, at = 100)
        assertThat(look(home("Siguiendo"), at = 200)).isEqualTo(BlockDecision.Free(null))
        assertThat(look(commentsTt, at = 300)).isEqualTo(BlockDecision.Free(null))
    }

    @Test
    fun `con un desbloqueo los comentarios quedan libres`() {
        look(viewer, at = 0)
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
        assertThat(
            look(commentsIg, at = 100),
        ).isEqualTo(BlockDecision.Free(BudgetPolicy.unlockRemainingMs(unlock, now)))
    }

    @Test
    fun `abrir los comentarios no reinicia la confirmacion`() {
        assertThat(confirmed(viewer, at = 0)).isFalse()
        assertThat(confirmed(commentsIg, at = ShowConfirmation.CONFIRM_MS)).isTrue()
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
        val key =
            when (val decision = look(snapshot, at)) {
                is BlockDecision.Block -> decision.confirmationKey
                is BlockDecision.SilentBack -> decision.confirmationKey
                else -> error("no hay nada que confirmar: $decision")
            }
        return confirmation.onMatch(key, at)
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
