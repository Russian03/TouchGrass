package io.github.russian03.touchgrass.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Los tiempos imitan lo capturado en el móvil con Instagram 450. */
class ViewerSessionTest {

    private val session = ViewerSession(settleMs = 500)

    @Test
    fun `sin pasar por ningún lugar no hay origen`() {
        assertThat(session.origin).isNull()
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `el origen es el último lugar visto`() {
        session.onPlace("explore")
        session.onPlace("profile")
        assertThat(session.origin).isEqualTo("profile")
    }

    @Test
    fun `arrastrar el visor un rato después de abrirlo cuenta como pasar de reel`() {
        // Inicio: el visor no avisa al abrirse; el primer scroll es ya el arrastre del usuario.
        session.onPlace("feed")
        session.onViewerShown(now = 39_400)
        session.onPagerScrolled(now = 44_369)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `el scroll que llega al abrir el visor no cuenta`() {
        // Chat: al abrirse llega un scroll antes de que la pantalla se evalúe.
        session.onPlace("chat")
        session.onPagerScrolled(now = 15_630)
        session.onViewerShown(now = 15_800)
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `los scrolls justo después de ver el visor abierto no cuentan`() {
        session.onPlace("feed")
        session.onViewerShown(now = 1_000)
        session.onPagerScrolled(now = 1_017)
        session.onPagerScrolled(now = 1_300)
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `seguir viendo el visor no cambia cuándo se abrió`() {
        session.onPlace("chat")
        session.onViewerShown(now = 1_000)
        session.onViewerShown(now = 5_000)
        session.onPagerScrolled(now = 5_100)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `volver a un lugar cierra el visor y empieza de cero`() {
        session.onPlace("chat")
        session.onViewerShown(now = 1_000)
        session.onPagerScrolled(now = 3_000)
        session.onPlace("chat")
        assertThat(session.scrolled).isFalse()
        session.onPagerScrolled(now = 4_000)
        assertThat(session.scrolled).isFalse()
    }
}
