package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.blocking.CoverZone
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
import java.io.File
import org.junit.Test

/**
 * Reglas reales de Instagram contra pantallas capturadas en Instagram 450
 * (solo identificadores de vista).
 */
class InstagramRulesTest {

    private val engine =
        RuleEngine(listOf(RuleRepository.parse(File("src/main/assets/rules/instagram.json").readText())))

    private fun screen(vararg ids: String, selected: String? = null) =
        ScreenSnapshot(IG, ids.map { ViewNode(it, selected = it == selected) })

    private val tabs = arrayOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")

    private val explore =
        screen(
            *tabs,
            "action_bar_search_edit_text",
            "grid_card_layout_container",
            "recycler_view",
            selected = "search_tab",
        )

    private val viewer =
        screen("clips_viewer_view_pager", "clips_video_container", "clips_author_username", "gesture_manager")

    /**
     * Vista previa al mantener pulsado un reel en Buscar (Instagram 454.0.0.54.77): la cuadrícula
     * y las pestañas siguen en el árbol y se suman los ids propios de la vista previa.
     */
    private val peekIds =
        arrayOf(
            "media_group",
            "indicator_container",
            "music_attribution_label_marquee",
            "row_feed_profile_header",
            "secondary_label",
            "row_feed_photo_profile_name",
        )

    private val peekOverExplore =
        screen(
            *peekIds,
            *tabs,
            "explore_action_bar_right_button_stub",
            "explore_action_bar_secondary_button_stub",
            "action_bar_search_edit_text",
            "recycler_view",
            "grid_card_layout_container",
            "image_button",
            selected = "search_tab",
        )

    /** Igual, pero con una foto o post en la vista previa. */
    private val photoPeekOverExplore =
        screen(
            "media_group",
            "row_feed_profile_header",
            "row_feed_photo_profile_name",
            "row_feed_photo_imageview",
            *tabs,
            "explore_action_bar_right_button_stub",
            "recycler_view",
            "grid_card_layout_container",
            "image_button",
            selected = "search_tab",
        )

    /** Perfil abierto desde Buscar (la pestaña sigue seleccionada) con la vista previa encima. */
    private val peekOverProfile =
        screen(
            *peekIds,
            *tabs,
            "profile_viewpager",
            "profile_tab_layout",
            "clips_grid_recyclerview",
            selected = "search_tab",
        )

    private val peekOverSaved =
        screen(*peekIds, "action_bar_button_back", "saved_tab_layout", "saved_view_pager", "recycler_view")

    /** Hoja de comentarios (Instagram 454.0.0.54.77): lo único que se ve; el reel de debajo no aparece. */
    private val commentsSheet =
        screen(
            "bottom_sheet_container",
            "comment_composer_parent_updated",
            "layout_comment_thread_edittext_multiline",
            "main_list_view",
            "sticky_header_list",
        )

    /** Ids reales de Inicio (Instagram 454.0.0.54.77); `*` marca el seleccionado. */
    private fun captured(ids: String) = ScreenSnapshot(
        IG,
        ids.split(",").map { ViewNode(it.removeSuffix("*"), selected = it.endsWith("*")) },
    )

    private val screenBounds = Bounds(0, 0, 1220, 2712)

    /** Posiciones de Instagram 454.0.0.54.77, Android 15 (1220x2712): cabecera, lista y barra de navegación. */
    private fun ScreenSnapshot.withPositions(headerBottom: Int? = 300) = copy(
        screen = screenBounds,
        headerBottom = headerBottom,
        listTop = 118,
        navLeft = 52,
        navTop = 2478,
        navBottom = 2660,
        navRounded = true,
        tabsLeft = 78,
        tabsTop = 2491,
        tabsBottom = 2647,
        titleLeft = 156,
        titleRight = 1064,
    )

    private fun zone(snapshot: ScreenSnapshot) = CoverZone.of(snapshot)

    private val homeTop = "profile_tab,search_tab,direct_tab,notification,clips_tab,feed_tab*,swipeable_tab_view_pager"

    /** Inicio en «Para ti» (Instagram 454.0.0.54.77, caso S1): cabecera clicable `action_bar_title_view`, historias y posts. */
    private val homeParaTi =
        captured(
            "$homeTop,action_bar_left_button,action_bar_title_view,title_text,android:id/list,outer_container," +
                "avatar_image_view,row_feed_profile_header,row_feed_photo_profile_name,media_option_button," +
                "row_feed_view_group_buttons,row_feed_button_like,row_feed_button_comment,row_feed_button_share",
        ).withPositions()

    /**
     * Inicio recién abierto (Instagram 454.0.0.54.77, Android 15): ninguna pestaña figura como seleccionada
     * hasta que el usuario pulsa una; sin ese estado la regla sigue cubriendo.
     */
    private val homeParaTiCold =
        captured(
            "profile_tab,search_tab,direct_tab,notification,clips_tab,feed_tab,swipeable_tab_view_pager," +
                "action_bar_left_button,action_bar_title_view,title_text,android:id/list,outer_container",
        ).withPositions()

    /** Inicio en «Para ti» ya bajado: la cabecera se esconde y la zona empieza donde la lista. */
    private val homeParaTiScrolled =
        captured(
            "$homeTop,action_bar_left_button,action_bar_title_view,title_text,android:id/list,video_container," +
                "media_group,row_feed_view_group_buttons,inline_follow_button",
        ).withPositions(headerBottom = null)

    /** «Siguiendo» (y «Favoritos»): pantalla aparte con flecha de volver y sin barra de navegación. */
    private val followingScreen =
        captured(
            "swipeable_tab_view_pager,android:id/list,action_bar_new_title_container,action_bar_button_back," +
                "action_bar_title,video_container,row_feed_profile_header",
        )

    /** Menú del selector de Inicio: ventana propia con «Siguiendo» y «Favoritos». */
    private val feedMenu = captured("context_menu_options_list,context_menu_item,context_menu_item_label")

    /** Historia abierta desde Inicio: `reel_viewer_root`, sin barra de navegación. */
    private val storyViewer =
        captured("swipeable_tab_view_pager,reel_viewer_root,gesture_manager,message_composer_container")

    /** Perfil abierto desde Inicio: sin `feed_tab` seleccionado. */
    private val profileScreen =
        captured(
            "swipeable_tab_view_pager,action_bar_username_container,action_bar_title,profile_viewpager," +
                "profile_tab_layout,profile_header_follow_button",
        )

    /** Perfil con Inicio seleccionado debajo y la cabecera de Inicio (por si el árbol conserva las dos). */
    private val profileFromHome =
        captured("$homeTop,action_bar_title_view,profile_viewpager,profile_tab_layout,inline_follow_button")

    @Test
    fun `Inicio en Para ti se cubre`() {
        assertThat(engine.evaluate(homeParaTi, origin = "feed")?.id).isEqualTo("feed_cover")
        assertThat(zone(homeParaTi)).isEqualTo(Bounds(0, 300, 1220, 2478))
    }

    @Test
    fun `Inicio en Para ti con el feed bajado se cubre desde la lista`() {
        assertThat(engine.evaluate(homeParaTiScrolled, origin = "feed")?.id).isEqualTo("feed_cover")
        assertThat(zone(homeParaTiScrolled)).isEqualTo(Bounds(0, 118, 1220, 2478))
    }

    @Test
    fun `Inicio recien abierto sin pestaña marcada se cubre`() {
        assertThat(engine.evaluate(homeParaTiCold, origin = null)?.id).isEqualTo("feed_cover")
        assertThat(zone(homeParaTiCold)).isEqualTo(Bounds(0, 300, 1220, 2478))
    }

    @Test
    fun `otra pestaña marcada con una lista debajo no se cubre`() {
        val inbox =
            captured("profile_tab,search_tab,direct_tab*,clips_tab,feed_tab,swipeable_tab_view_pager,android:id/list")
        assertThat(engine.evaluate(inbox, origin = "inbox")).isNull()
    }

    @Test
    fun `Siguiendo y Favoritos quedan libres`() {
        assertThat(engine.evaluate(followingScreen, origin = "feed")).isNull()
    }

    @Test
    fun `el menu del selector queda libre`() {
        assertThat(engine.evaluate(feedMenu, origin = "feed")).isNull()
    }

    @Test
    fun `una historia abierta desde Inicio queda libre`() {
        assertThat(engine.evaluate(storyViewer, origin = "feed")).isNull()
    }

    @Test
    fun `el perfil abierto desde Inicio queda libre`() {
        assertThat(engine.evaluate(profileScreen, origin = "feed")).isNull()
        assertThat(engine.evaluate(profileFromHome, origin = "feed")).isNull()
    }

    @Test
    fun `un reel abierto desde Inicio no lo cubre la regla de Inicio`() {
        val viewerOverHome =
            captured("$homeTop,action_bar_title_view,clips_viewer_view_pager,clips_video_container")
        assertThat(engine.evaluate(viewerOverHome, origin = "feed", scrolled = false)).isNull()
    }

    @Test
    fun `Inicio sigue siendo el lugar feed`() {
        assertThat(engine.place(homeParaTi)).isEqualTo("feed")
    }

    @Test
    fun `el boton Seguir fuera de Inicio no bloquea`() {
        val followOutsideHome = captured("profile_tab,search_tab*,direct_tab,clips_tab,feed_tab,inline_follow_button")
        assertThat(engine.evaluate(followOutsideHome, origin = "explore")).isNull()
    }

    @Test
    fun `los comentarios por si solos no bloquean pero cubren la regla del reel`() {
        assertThat(engine.evaluate(commentsSheet)).isNull()
        val rule = engine.evaluate(viewer, origin = "explore")!!
        assertThat(engine.covers(commentsSheet, rule)).isTrue()
        assertThat(engine.covers(commentsSheet, engine.evaluate(screen(*tabs, selected = "clips_tab"))!!)).isTrue()
    }

    @Test
    fun `mantener pulsado un reel en Buscar bloqueado`() {
        assertThat(engine.evaluate(peekOverExplore, origin = "explore")?.id).isEqualTo("explore_peek")
    }

    @Test
    fun `mantener pulsado un post en Buscar bloqueado`() {
        assertThat(engine.evaluate(photoPeekOverExplore, origin = "explore")?.id).isEqualTo("explore_peek")
    }

    @Test
    fun `mantener pulsado en un perfil permitido`() {
        assertThat(engine.place(peekOverProfile)).isEqualTo("profile")
        assertThat(engine.evaluate(peekOverProfile, origin = "profile")).isNull()
    }

    @Test
    fun `mantener pulsado en Guardados permitido`() {
        assertThat(engine.place(peekOverSaved)).isEqualTo("saved")
        assertThat(engine.evaluate(peekOverSaved, origin = "saved")).isNull()
    }

    @Test
    fun `la vista previa no cambia el lugar`() {
        assertThat(engine.place(peekOverExplore)).isEqualTo(engine.place(explore))
    }

    @Test
    fun `un post normal de Inicio con ids parecidos no bloquea`() {
        val post = screen(*tabs, *peekIds, "row_feed_photo_imageview", selected = "feed_tab")
        assertThat(engine.evaluate(post, origin = "feed")).isNull()
    }

    @Test
    fun `feed de inicio permitido`() {
        val s = screen(*tabs, "row_feed_profile_header", "reels_tray_container", selected = "feed_tab")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `pestaña de Reels bloqueada`() {
        val s = screen(*tabs, "clips_viewer_view_pager", "clips_video_container", selected = "clips_tab")
        assertThat(engine.evaluate(s)?.id).isEqualTo("reels_tab")
    }

    @Test
    fun `reel o post abierto desde Explorar bloqueado`() {
        assertThat(engine.place(explore)).isEqualTo("explore")
        assertThat(engine.evaluate(viewer, origin = "explore")?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `reel abierto desde Inicio permitido hasta pasar al siguiente`() {
        assertThat(engine.place(screen(*tabs, "row_feed_profile_header", selected = "feed_tab"))).isEqualTo("feed")
        assertThat(engine.evaluate(viewer, origin = "feed", scrolled = false)).isNull()
        assertThat(engine.evaluate(viewer, origin = "feed", scrolled = true)?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `reels de un perfil permitidos aunque se haga scroll`() {
        val profile = screen("profile_viewpager", "profile_tab_layout", "clips_grid_recyclerview")
        assertThat(engine.place(profile)).isEqualTo("profile")
        assertThat(engine.evaluate(viewer, origin = "profile", scrolled = true)).isNull()
    }

    @Test
    fun `reel enviado por chat permitido hasta pasar al siguiente`() {
        val chat = screen("message_list", "row_thread_composer_edittext", "thread_fragment_container")
        assertThat(engine.place(chat)).isEqualTo("chat")
        assertThat(engine.evaluate(chat, origin = "chat")).isNull()
        assertThat(engine.evaluate(viewer, origin = "chat", scrolled = false)).isNull()
        assertThat(engine.evaluate(viewer, origin = "chat", scrolled = true)?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `reel guardado permitido hasta pasar al siguiente`() {
        // Guardados se abre desde el propio perfil, pero no debe heredar su permiso.
        val saved = screen("action_bar_button_back", "saved_tab_layout", "saved_view_pager", "recycler_view")
        assertThat(engine.place(saved)).isEqualTo("saved")
        assertThat(engine.evaluate(viewer, origin = "saved", scrolled = false)).isNull()
        assertThat(engine.evaluate(viewer, origin = "saved", scrolled = true)?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `cuadrícula de Explorar libre`() {
        assertThat(engine.evaluate(explore, origin = "explore")).isNull()
    }

    @Test
    fun `resultados de búsqueda permitidos y sin cambiar el origen`() {
        val s = screen("action_bar_search_edit_text", "row_search_user_container", "search_section_container")
        assertThat(engine.evaluate(s, origin = "explore")).isNull()
        assertThat(engine.place(s)).isNull()
    }

    @Test
    fun `posts de un perfil permitidos`() {
        val s = screen("action_bar_button_back", "row_feed_profile_header", "row_feed_photo_imageview")
        assertThat(engine.evaluate(s, origin = "profile")).isNull()
    }

    @Test
    fun `el visor no se confunde con ningún lugar`() {
        assertThat(engine.place(viewer)).isNull()
    }

    private companion object {
        const val IG = "com.instagram.android"
    }
}
