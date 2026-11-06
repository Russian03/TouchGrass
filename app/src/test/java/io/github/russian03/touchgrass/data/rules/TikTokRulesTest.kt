package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
import java.io.File
import org.junit.Test

/**
 * Reglas reales de TikTok contra pantallas capturadas en TikTok 47.1.4, en español
 * (identificadores de vista y el texto de las pestañas de arriba).
 */
class TikTokRulesTest {

    private val ruleSet = RuleRepository.parse(File("src/main/assets/rules/tiktok.json").readText())

    private val engine = RuleEngine(listOf(ruleSet))

    /** Inicio con las pestañas de arriba y [selected] elegida; el resto, identificadores sueltos. */
    private fun home(
        selected: String,
        vararg ids: String,
        names: List<String> = listOf("Comunidad", "Siguiendo", "Tienda", "Para ti"),
    ): ScreenSnapshot {
        val wanted = engine.texts(TT)[TAB].orEmpty()
        val tabs =
            names.map {
                ViewNode(TAB, selected = it == selected, text = it.takeIf { t -> t in wanted })
            }
        return ScreenSnapshot(TT, tabs + (ids.toList() + bottomBar).map { ViewNode(it) })
    }

    private fun screen(vararg ids: String) = ScreenSnapshot(TT, ids.map { ViewNode(it) })

    private val bottomBar = listOf("opp", "oph", "opg", "opd", "opi", "opj")

    @Test
    fun `Para ti bloqueado`() {
        val s = home("Para ti", "viewpager", "long_press_layout", "user_avatar", "title", "desc")
        assertThat(engine.evaluate(s)?.id).isEqualTo("for_you_tab")
    }

    @Test
    fun `Comunidad bloqueada`() {
        val s = home("Comunidad", "viewpager", "tvw_desc", "tvw_nick", "tvw_like_counts")
        assertThat(engine.evaluate(s)?.id).isEqualTo("community_tab")
    }

    @Test
    fun `Siguiendo permitido`() {
        val s = home("Siguiendo", "viewpager", "long_press_layout", "user_avatar", "title", "desc")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `Following permitido`() {
        val s = home("Following", "viewpager", "long_press_layout", names = listOf("Following", "For You"))
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `Volver atrás lleva a Siguiendo`() {
        listOf("Para ti", "Comunidad").forEach { tab ->
            val action = engine.evaluate(home(tab, "viewpager"))?.action
            assertThat(action).isInstanceOf(Action.Click::class.java)
            // La pestaña de arriba se busca por su identificador y su texto exacto.
            assertThat((action as Action.Click).target).isEqualTo(TAB)
            assertThat(action.text).containsExactly("Siguiendo", "Following")
        }
    }

    @Test
    fun `el destino de cada salida de TikTok no está bloqueado`() {
        val destinos =
            ruleSet.rules.flatMap { rule ->
                val action = rule.action as? Action.Click
                if (action?.target == TAB) action.text else emptyList()
            }
        assertThat(destinos).isNotEmpty()
        destinos.forEach { destino ->
            val tabs = listOf("Comunidad", "Siguiendo", "Tienda", "Para ti", "Following", "For You")
            val nodes = tabs.map { ViewNode(TAB, selected = it == destino, text = it) } + ViewNode("viewpager")
            assertThat(engine.evaluate(ScreenSnapshot(TT, nodes))).isNull()
        }
    }

    @Test
    fun `vídeo abierto desde Buscar o un perfil permitido`() {
        val s = screen("viewpager", "long_press_layout", "user_avatar", "title", "desc", "tv_bar_search")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `vídeo abierto desde un chat permitido`() {
        val s = screen("viewpager", "long_press_layout", "user_avatar", "title", "desc", "feed_multi_tag_layout")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `perfil y resultados de búsqueda permitidos`() {
        assertThat(engine.evaluate(screen("viewpager", "tv_play_count", *bottomBar.toTypedArray()))).isNull()
        assertThat(engine.evaluate(screen("viewpager_search", "tv_tab_tako_entrance"))).isNull()
    }

    private companion object {
        const val TT = "com.zhiliaoapp.musically"
        const val TAB = "android:id/text1"
    }
}
