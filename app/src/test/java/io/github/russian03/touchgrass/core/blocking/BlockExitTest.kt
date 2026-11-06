package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.rules.Action
import org.junit.Test

class BlockExitTest {

    /** Vista falsa: `AccessibilityNodeInfo` no se puede crear en tests JVM. */
    private data class FakeNode(val clickable: Boolean, val parent: FakeNode? = null)

    private val messages = Action.Click(text = listOf("Mensajes", "Bandeja de entrada"))

    private fun climb(node: FakeNode) = BlockExit.clickableSelfOrParent(node, { it.parent }, { it.clickable })

    /** Cadena de [depth] vistas sin pulsar sobre [top]; devuelve la de más abajo. */
    private fun below(top: FakeNode, depth: Int): FakeNode =
        (1..depth).fold(top) { parent, _ -> FakeNode(clickable = false, parent = parent) }

    /** Lo que llama [BlockExit.perform] para [action], en orden, si pulsar da [clickWorks]. */
    private fun exit(action: Action, clickWorks: Boolean): List<String> {
        val calls = mutableListOf<String>()
        BlockExit.perform(
            action,
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

    // Salida

    @Test
    fun `atrás hace atrás global sin intentar pulsar`() {
        assertThat(exit(Action.Back, clickWorks = true)).containsExactly("back")
    }

    @Test
    fun `pulsar que lo consigue no hace atrás`() {
        assertThat(exit(messages, clickWorks = true)).containsExactly("click")
    }

    @Test
    fun `pulsar que no lo consigue cae a atrás global`() {
        assertThat(exit(messages, clickWorks = false)).containsExactly("click", "back").inOrder()
    }

    @Test
    fun `pulsar recibe la acción de la regla`() {
        val pressed = mutableListOf<Action.Click>()
        BlockExit.perform(messages, click = { pressed.add(it) }, back = { true })
        assertThat(pressed).containsExactly(messages)
    }

    // Búsqueda por identificador

    @Test
    fun `por identificador sin textos vale cualquier vista`() {
        val feed = Action.Click(target = "feed_tab")
        assertThat(BlockExit.matchesById(feed, "Inicio")).isTrue()
        assertThat(BlockExit.matchesById(feed, null)).isTrue()
    }

    @Test
    fun `por identificador con textos solo vale uno exacto`() {
        val tab = Action.Click(target = "tab", text = listOf("Mensajes"))
        assertThat(BlockExit.matchesById(tab, "Mensajes")).isTrue()
        assertThat(BlockExit.matchesById(tab, "Mensajes nuevos")).isFalse()
        assertThat(BlockExit.matchesById(tab, null)).isFalse()
    }

    // Búsqueda por texto

    @Test
    fun `por texto casa con el texto o la descripción exactos`() {
        assertThat(BlockExit.matchesText("Mensajes", "Mensajes", null)).isTrue()
        assertThat(BlockExit.matchesText("Mensajes", null, "Mensajes")).isTrue()
    }

    @Test
    fun `por texto no basta con contenerlo ni sin texto`() {
        assertThat(BlockExit.matchesText("Mensajes", "Mensajes nuevos", "Mensajes nuevos")).isFalse()
        assertThat(BlockExit.matchesText("Mensajes", null, null)).isFalse()
    }

    // Vista que se puede pulsar

    @Test
    fun `la propia vista si se puede pulsar`() {
        val node = FakeNode(clickable = true, parent = FakeNode(clickable = true))
        assertThat(climb(node)).isSameInstanceAs(node)
    }

    @Test
    fun `encuentra el tercer padre`() {
        val tab = FakeNode(clickable = true)
        assertThat(climb(below(tab, depth = 3))).isSameInstanceAs(tab)
    }

    @Test
    fun `no llega al cuarto padre`() {
        // Comportamiento actual: se mira la vista y 3 padres (4 vistas en total), no «hasta 4 padres».
        val tab = FakeNode(clickable = true)
        assertThat(climb(below(tab, depth = 4))).isNull()
    }

    @Test
    fun `sin nada que pulsar en una cadena corta`() {
        assertThat(climb(below(FakeNode(clickable = false), depth = 1))).isNull()
    }
}
