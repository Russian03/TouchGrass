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
    fun `reel abierto desde Explorar bloqueado`() {
        val s = screen("clips_viewer_view_pager", "clips_video_container", "clips_viewer_action_bar_title")
        assertThat(engine.evaluate(s)?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `reel enviado por un amigo en DM permitido`() {
        val s = screen("clips_viewer_view_pager", "sender_profile_pic", "reply_bar_edittext")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `reels sugeridos tras el de un amigo bloqueados`() {
        val s = screen("clips_viewer_view_pager", "suggested_title", "send_cta")
        assertThat(engine.evaluate(s)?.id).isEqualTo("reels_viewer")
    }

    @Test
    fun `mensajes directos permitidos`() {
        val s = screen("message_list", "row_thread_composer_edittext", "thread_fragment_container")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `cuadrícula de Explorar permitida por ahora`() {
        val s = screen(*tabs, "grid_card_layout_container", "action_bar_search_edit_text", selected = "search_tab")
        assertThat(engine.evaluate(s)).isNull()
    }

    private companion object {
        const val IG = "com.instagram.android"
    }
}
