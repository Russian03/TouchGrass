package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import org.junit.Test

/** Zona de la capa de Inicio. Posiciones de Instagram 454.0.0.54.77, caso S1 (pantalla 1220x2712). */
class CoverZoneTest {

    private val screen = Bounds(0, 0, 1220, 2712)

    private fun snapshot(
        storiesBottom: Int? = 695,
        headerBottom: Int? = 300,
        navTop: Int? = 2491,
        screen: Bounds? = this.screen,
    ) = ScreenSnapshot("pkg", emptyList(), screen, storiesBottom, headerBottom, navTop)

    @Test
    fun `la zona va del borde de abajo de las historias al borde de arriba de la barra`() {
        assertThat(CoverZone.of(snapshot())).isEqualTo(Bounds(0, 695, 1220, 2491))
    }

    @Test
    fun `sin historias a la vista la zona empieza bajo la cabecera`() {
        assertThat(CoverZone.of(snapshot(storiesBottom = null))).isEqualTo(Bounds(0, 300, 1220, 2491))
    }

    @Test
    fun `se recorta a la pantalla`() {
        assertThat(CoverZone.of(snapshot(navTop = 3000))).isEqualTo(Bounds(0, 695, 1220, 2712))
    }

    @Test
    fun `sin barra de navegacion no hay zona`() {
        assertThat(CoverZone.of(snapshot(navTop = null))).isNull()
    }

    @Test
    fun `sin historias ni cabecera no hay zona`() {
        assertThat(CoverZone.of(snapshot(storiesBottom = null, headerBottom = null))).isNull()
    }

    @Test
    fun `sin saber el tamano de la pantalla no hay zona`() {
        assertThat(CoverZone.of(snapshot(screen = null))).isNull()
    }

    @Test
    fun `una zona de menos del 30 por ciento de la pantalla se descarta`() {
        // Un teclado o una hoja encogen la lista: 491 px de 2712.
        assertThat(CoverZone.of(snapshot(storiesBottom = 2000))).isNull()
    }

    @Test
    fun `una zona de mas del 90 por ciento de la pantalla se descarta`() {
        // Nunca se tapa la pantalla entera por un id mal leido.
        assertThat(CoverZone.of(snapshot(storiesBottom = null, headerBottom = 0, navTop = 2712))).isNull()
    }

    @Test
    fun `una zona al reves no existe`() {
        assertThat(CoverZone.of(snapshot(storiesBottom = 2600, navTop = 2491))).isNull()
    }
}
