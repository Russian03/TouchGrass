package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import org.junit.Test

/** Zona de la capa de Inicio. Posiciones de Instagram 454.0.0.54.77, Android 15 (pantalla 1220x2712). */
class CoverZoneTest {

    private val screen = Bounds(0, 0, 1220, 2712)

    private fun snapshot(
        headerBottom: Int? = 300,
        listTop: Int? = 118,
        navLeft: Int? = 78,
        navTop: Int? = 2491,
        navBottom: Int? = 2647,
        screen: Bounds? = this.screen,
        titleLeft: Int? = 156,
        titleRight: Int? = 1064,
    ) = ScreenSnapshot(
        packageName = "pkg",
        nodes = emptyList(),
        screen = screen,
        headerBottom = headerBottom,
        listTop = listTop,
        navLeft = navLeft,
        navTop = navTop,
        navBottom = navBottom,
        titleLeft = titleLeft,
        titleRight = titleRight,
    )

    @Test
    fun `la zona va del borde de abajo de la cabecera al borde de arriba de la barra`() {
        assertThat(CoverZone.of(snapshot())).isEqualTo(Bounds(0, 300, 1220, 2491))
    }

    @Test
    fun `con el feed bajado la cabecera no se ve y la zona empieza donde la lista`() {
        assertThat(CoverZone.of(snapshot(headerBottom = null))).isEqualTo(Bounds(0, 118, 1220, 2491))
    }

    @Test
    fun `se recorta a la pantalla`() {
        assertThat(CoverZone.of(snapshot(navTop = 3000))).isEqualTo(Bounds(0, 300, 1220, 2712))
    }

    @Test
    fun `sin barra de navegacion no hay zona`() {
        assertThat(CoverZone.of(snapshot(navTop = null))).isNull()
    }

    @Test
    fun `sin cabecera ni lista no hay zona`() {
        assertThat(CoverZone.of(snapshot(headerBottom = null, listTop = null))).isNull()
    }

    @Test
    fun `sin saber el tamano de la pantalla no hay zona`() {
        assertThat(CoverZone.of(snapshot(screen = null))).isNull()
    }

    @Test
    fun `una zona de menos del 30 por ciento de la pantalla se descarta`() {
        // Un teclado o una hoja encogen la lista: 491 px de 2712.
        assertThat(CoverZone.of(snapshot(headerBottom = 2000))).isNull()
    }

    @Test
    fun `una zona de mas del 90 por ciento de la pantalla se descarta`() {
        // Nunca se tapa la pantalla entera por un id mal leido.
        assertThat(CoverZone.of(snapshot(headerBottom = 0, navTop = 2712))).isNull()
    }

    @Test
    fun `una zona al reves no existe`() {
        assertThat(CoverZone.of(snapshot(headerBottom = 2600, navTop = 2491))).isNull()
    }

    @Test
    fun `tres franjas tapan lo que la barra deja al aire`() {
        assertThat(CoverZone.edges(snapshot()))
            .containsExactly(
                Bounds(0, 2647, 1220, 2712),
                Bounds(0, 2491, 78, 2647),
                Bounds(1142, 2491, 1220, 2647),
            )
            .inOrder()
    }

    @Test
    fun `sin las medidas de la barra no hay franjas`() {
        assertThat(CoverZone.edges(snapshot(navLeft = null))).isEmpty()
        assertThat(CoverZone.edges(snapshot(navBottom = null))).isEmpty()
        assertThat(CoverZone.edges(snapshot(screen = null))).isEmpty()
    }

    @Test
    fun `una barra pegada a los bordes no necesita franjas`() {
        assertThat(CoverZone.edges(snapshot(navLeft = 0, navBottom = 2712))).isEmpty()
    }

    @Test
    fun `un escudo transparente cubre la cabecera entre sus botones`() {
        assertThat(CoverZone.shields(snapshot())).containsExactly(Bounds(156, 118, 1064, 300))
    }

    @Test
    fun `sin cabecera o sin los lados del titulo no hay escudo`() {
        assertThat(CoverZone.shields(snapshot(headerBottom = null))).isEmpty()
        assertThat(CoverZone.shields(snapshot(titleLeft = null))).isEmpty()
        assertThat(CoverZone.shields(snapshot(screen = null))).isEmpty()
    }
}
