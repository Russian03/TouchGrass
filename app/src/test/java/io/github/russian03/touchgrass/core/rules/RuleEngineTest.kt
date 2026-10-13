package io.github.russian03.touchgrass.core.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
import org.junit.Assert.assertThrows
import org.junit.Test

class RuleEngineTest {

    private val reelsTab =
        Rule(
            id = "reels_tab",
            description = "Reels",
            match = Match(allOf = listOf(Condition("clips_tab", selected = true))),
            action = Action.Click("feed_tab"),
        )

    private val viewerOutsideTabs =
        Rule(
            id = "viewer",
            description = "Visor",
            match =
            Match(
                allOf = listOf(Condition("clips_viewer")),
                noneOf = listOf(Condition("tab_bar")),
            ),
            action = Action.Back,
        )

    private val viewerFromPlaces =
        Rule(
            id = "pager_viewer",
            description = "Visor paginado",
            match = Match(allOf = listOf(Condition("pager"))),
            action = Action.Back,
            allowFrom = listOf(Allowance("profile"), Allowance("chat", untilScroll = true)),
            pager = "pager",
        )

    private val places =
        listOf(
            Place("chat", Match(allOf = listOf(Condition("thread")))),
            Place("profile", Match(allOf = listOf(Condition("profile_header")))),
        )

    private val engine =
        RuleEngine(listOf(RuleSet(1, IG, listOf(reelsTab, viewerOutsideTabs, viewerFromPlaces), places)))

    private fun snapshot(vararg nodes: ViewNode, pkg: String = IG) = ScreenSnapshot(pkg, nodes.toList())

    @Test
    fun `pestaña de Reels seleccionada dispara la regla`() {
        val s = snapshot(ViewNode("feed_tab"), ViewNode("clips_tab", selected = true))
        assertThat(engine.evaluate(s)).isEqualTo(reelsTab)
    }

    @Test
    fun `pestaña de Reels visible pero no seleccionada no dispara nada`() {
        val s = snapshot(ViewNode("feed_tab", selected = true), ViewNode("clips_tab"))
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `noneOf excluye la regla cuando la condición está presente`() {
        val s = snapshot(ViewNode("clips_viewer"), ViewNode("tab_bar"))
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `allOf sin noneOf presente dispara la regla`() {
        val s = snapshot(ViewNode("clips_viewer"))
        assertThat(engine.evaluate(s)).isEqualTo(viewerOutsideTabs)
    }

    @Test
    fun `otra app no se evalúa`() {
        val s = snapshot(ViewNode("clips_tab", selected = true), pkg = "com.example.other")
        assertThat(engine.evaluate(s)).isNull()
    }

    @Test
    fun `se reconoce el primer lugar que se cumple`() {
        assertThat(engine.place(snapshot(ViewNode("profile_header")))).isEqualTo("profile")
        assertThat(engine.place(snapshot(ViewNode("thread"), ViewNode("profile_header")))).isEqualTo("chat")
        assertThat(engine.place(snapshot(ViewNode("feed")))).isNull()
    }

    @Test
    fun `una regla se permite si se llega desde un lugar permitido`() {
        val s = snapshot(ViewNode("pager"))
        assertThat(engine.evaluate(s, origin = "profile", scrolled = true)).isNull()
        assertThat(engine.evaluate(s, origin = "explore")).isEqualTo(viewerFromPlaces)
        assertThat(engine.evaluate(s, origin = null)).isEqualTo(viewerFromPlaces)
    }

    @Test
    fun `untilScroll solo permite la regla hasta pasar de página`() {
        val s = snapshot(ViewNode("pager"))
        assertThat(engine.evaluate(s, origin = "chat", scrolled = false)).isNull()
        assertThat(engine.evaluate(s, origin = "chat", scrolled = true)).isEqualTo(viewerFromPlaces)
    }

    @Test
    fun `los pagers se buscan por app`() {
        assertThat(engine.pagers(IG)).containsExactly("pager")
        assertThat(engine.pagers("com.example.other")).isEmpty()
    }

    @Test
    fun `una condición con texto solo se cumple con uno de esos textos`() {
        val tab = Condition("android:id/text1", selected = true, text = listOf("Para ti", "For You"))
        val rule = Rule("for_you", "Para ti", Match(allOf = listOf(tab)), Action.Back)
        val tiktok = RuleEngine(listOf(RuleSet(1, TT, listOf(rule))))
        fun tabs(selected: String) = ScreenSnapshot(
            TT,
            listOf("Siguiendo", "Para ti").map { ViewNode("android:id/text1", it == selected, text = it) },
        )

        assertThat(tiktok.evaluate(tabs("Para ti"))).isEqualTo(rule)
        assertThat(tiktok.evaluate(tabs("Siguiendo"))).isNull()
        // Si el texto no se ha leído (no es uno de los buscados), no se cumple.
        assertThat(tiktok.evaluate(ScreenSnapshot(TT, listOf(ViewNode("android:id/text1", selected = true))))).isNull()
    }

    @Test
    fun `los textos que se leen son solo los que buscan las reglas y lugares`() {
        val rules =
            listOf(
                Rule("a", "A", Match(allOf = listOf(Condition("tab", text = listOf("Para ti")))), Action.Back),
                Rule("b", "B", Match(allOf = listOf(Condition("tab", text = listOf("Comunidad")))), Action.Back),
            )
        val places = listOf(Place("p", Match(allOf = listOf(Condition("title", text = listOf("Mensajes"))))))
        val tiktok = RuleEngine(listOf(RuleSet(1, TT, rules, places)))
        assertThat(tiktok.texts(TT))
            .containsExactly("tab", setOf("Para ti", "Comunidad"), "title", setOf("Mensajes"))
        assertThat(engine.texts(IG)).isEmpty()
    }

    @Test
    fun `una regla sin condiciones no se puede crear`() {
        assertThrows(IllegalArgumentException::class.java) { Match(allOf = emptyList()) }
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val TT = "com.zhiliaoapp.musically"
    }
}
