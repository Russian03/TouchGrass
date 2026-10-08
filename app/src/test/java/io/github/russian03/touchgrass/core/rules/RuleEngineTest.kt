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

    private val engine = RuleEngine(listOf(RuleSet(1, IG, listOf(reelsTab, viewerOutsideTabs))))

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
    fun `una regla sin condiciones no se puede crear`() {
        assertThrows(IllegalArgumentException::class.java) { Match(allOf = emptyList()) }
    }

    private companion object {
        const val IG = "com.instagram.android"
    }
}
