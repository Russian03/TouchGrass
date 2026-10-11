package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.rules.RuleEngine
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
