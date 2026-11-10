package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.blocking.PostMask
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow
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

    private val homeList = Bounds(0, 118, 1220, 2504)

    private fun row(top: Int, bottom: Int, header: Boolean = false, marked: Boolean = false) =
        ListRow(Bounds(0, top, 1220, bottom), header, marked)

    /** Filas de la lista (caso R2, Instagram 454.0.0.54.77): solo la cabecera lleva `header`. */
    private fun ScreenSnapshot.withRows(vararg rows: ListRow) = copy(listBounds = homeList, rows = rows.toList())

    private fun zones(snapshot: ScreenSnapshot, leadingMarked: Boolean = false) =
        PostMask.zones(snapshot.rows, snapshot.listBounds, leadingMarked)

    private val homeTop = "profile_tab,search_tab,direct_tab,notification,clips_tab,feed_tab*,swipeable_tab_view_pager"

    /** Inicio, arriba del todo tras pulsar la pestaña (el feed se recarga): sin ningún post todavía. */
    private val homeLoading =
        captured("$homeTop,action_bar_title_view,title_text,action_bar_left_button,android:id/list,shimmer_content")

    /** Post de alguien que sigues: cabecera sin botón «Seguir». */
    private val homeFollowed =
        captured(
            "$homeTop,android:id/list,row_feed_view_group_buttons,carousel_media_group,carousel_viewpager," +
                "carousel_image_media_group,carousel_image,row_feed_profile_header,media_option_button," +
                "row_feed_photo_profile_name,row_feed_photo_profile_imageview",
        ).withRows(row(300, 469, header = true), row(469, 1300))

    /** Post sugerido mezclado entre seguidos: la cabecera lleva `inline_follow_button` («Seguir»). */
    private val homeSuggested =
        captured(
            "$homeTop,android:id/list,row_feed_profile_header,media_option_button,inline_follow_button," +
                "secondary_label,row_feed_photo_profile_name,row_feed_photo_profile_imageview,video_container," +
                "media_group,row_feed_view_group_buttons,row_feed_button_save,row_feed_button_share," +
                "reposts_ufi_icon,row_feed_button_comment,row_feed_button_like",
        ).withRows(row(300, 469, header = true), row(469, 800, header = true, marked = true), row(800, 1300))

    /** Mitad de un post sugerido: la cabecera ya no se ve, pero el botón «Seguir» sigue en pantalla. */
    private val homeSuggestedMiddle =
        captured(
            "$homeTop,android:id/list,inline_follow_button,row_feed_view_group_buttons,row_feed_button_save," +
                "row_feed_button_share,row_feed_button_comment,row_feed_button_like,video_container,media_group",
        ).withRows(row(118, 900), row(900, 1069, header = true))

    /** Anuncio (`secondary_label` «Publicidad»): lleva el mismo botón «Seguir». */
    private val homeAd =
        captured(
            "$homeTop,android:id/list,row_feed_profile_header,media_option_button,inline_follow_button," +
                "secondary_label,row_feed_photo_profile_name,row_feed_photo_profile_imageview,video_container," +
                "media_group,row_feed_view_group_buttons,row_feed_button_save,row_feed_button_share," +
                "reposts_ufi_icon,row_feed_button_comment,row_feed_button_like,carousel_media_group," +
                "carousel_viewpager,carousel_image_media_group,carousel_image",
        ).withRows(row(300, 600, header = true, marked = true), row(600, 1300), row(1300, 1469, header = true))

    /** Perfil (`profile_viewpager`, de otras capturas) con Inicio seleccionado debajo y el botón «Seguir». */
    private val profileFromHome =
        captured("$homeTop,profile_viewpager,profile_tab_layout,clips_grid_recyclerview,inline_follow_button")

    @Test
    fun `un post sugerido en Inicio se tapa`() {
        assertThat(engine.evaluate(homeSuggested, origin = "feed")?.id).isEqualTo("feed_suggested")
        assertThat(zones(homeSuggested)).containsExactly(Bounds(0, 469, 1220, 1300))
    }

    @Test
    fun `un post sugerido sin su cabecera en pantalla sigue tapado si se recuerda`() {
        assertThat(engine.evaluate(homeSuggestedMiddle, origin = "feed")?.id).isEqualTo("feed_suggested")
        assertThat(zones(homeSuggestedMiddle)).isEmpty()
        assertThat(zones(homeSuggestedMiddle, leadingMarked = true)).containsExactly(Bounds(0, 118, 1220, 900))
    }

    @Test
    fun `un anuncio en Inicio se tapa`() {
        assertThat(engine.evaluate(homeAd, origin = "feed")?.id).isEqualTo("feed_suggested")
        assertThat(zones(homeAd)).containsExactly(Bounds(0, 300, 1220, 1300))
    }

    @Test
    fun `Inicio con solo seguidos no tapa nada`() {
        assertThat(zones(homeFollowed)).isEmpty()
        assertThat(zones(homeLoading)).isEmpty()
    }

    @Test
    fun `el perfil de alguien que no sigues abierto desde Inicio permitido`() {
        assertThat(engine.evaluate(profileFromHome, origin = "feed")).isNull()
    }

    @Test
    fun `un reel abierto desde Inicio no lo bloquea la regla de sugeridos`() {
        val viewerOverHome = captured("$homeTop,clips_viewer_view_pager,clips_video_container,inline_follow_button")
        assertThat(engine.evaluate(viewerOverHome, origin = "feed", scrolled = false)).isNull()
    }

    @Test
    fun `Inicio sigue siendo el lugar feed`() {
        assertThat(engine.place(homeSuggested)).isEqualTo("feed")
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
