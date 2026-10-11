package io.github.russian03.touchgrass.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Los índices imitan lo capturado en el móvil: al abrir el visor llega un scroll con la página inicial. */
class ViewerSessionTest {

    private val session = ViewerSession()

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
    fun `la página en la que se abre el visor no cuenta como scroll`() {
        session.onPlace("chat")
        session.onPagerScrolled(0)
        session.onPagerScrolled(0)
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `pasar a otra página cuenta como scroll`() {
        session.onPlace("chat")
        session.onPagerScrolled(0)
        session.onPagerScrolled(1)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `el visor puede abrirse en una página que no es la primera`() {
        session.onPlace("explore")
        session.onPagerScrolled(25)
        assertThat(session.scrolled).isFalse()
        session.onPagerScrolled(26)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `volver a la página inicial no deshace el scroll`() {
        session.onPagerScrolled(0)
        session.onPagerScrolled(1)
        session.onPagerScrolled(0)
        assertThat(session.scrolled).isTrue()
    }

    @Test
    fun `volver a un lugar cierra el visor y empieza de cero`() {
        session.onPlace("chat")
        session.onPagerScrolled(0)
        session.onPagerScrolled(1)
        session.onPlace("chat")
        assertThat(session.scrolled).isFalse()
        session.onPagerScrolled(3)
        assertThat(session.scrolled).isFalse()
    }

    @Test
    fun `índices desconocidos se ignoran`() {
        session.onPagerScrolled(-1)
        session.onPagerScrolled(0)
        assertThat(session.scrolled).isFalse()
    }
}
