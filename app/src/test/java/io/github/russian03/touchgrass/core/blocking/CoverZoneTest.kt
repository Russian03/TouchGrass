package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.rules.PillSpec
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
        navRounded: Boolean = false,
        tabsLeft: Int? = null,
        tabsTop: Int? = null,
        tabsBottom: Int? = null,
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
        navRounded = navRounded,
        tabsLeft = tabsLeft,
        tabsTop = tabsTop,
        tabsBottom = tabsBottom,
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
    fun `sin pastilla tres franjas tapan lo que la barra deja al aire`() {
        assertThat(CoverZone.edges(snapshot()))
            .containsExactly(
                Bounds(0, 2491, 78, 2647),
                Bounds(1142, 2491, 1220, 2647),
                Bounds(0, 2647, 1220, 2712),
            )
            .inOrder()
    }

    @Test
    fun `con pastilla las franjas tapan todo menos las pestañas`() {
        // Pastilla 52-1168 x 2478-2660; pestañas 78-1142 x 2491-2647 (Instagram 454, densidad 3,25).
        val edges =
            CoverZone.edges(
                snapshot(
                    navLeft = 52,
                    navTop = 2478,
                    navBottom = 2660,
                    navRounded = true,
                    tabsLeft = 78,
                    tabsTop = 2491,
                    tabsBottom = 2647,
                ),
            )

        assertThat(edges)
            .containsExactly(
                // Borde de arriba de la pastilla, a todo el ancho: ahí se podía empezar a deslizar por las esquinas.
                Bounds(0, 2478, 1220, 2491),
                // Márgenes y puntas de la pastilla a cada lado de las pestañas.
                Bounds(0, 2491, 78, 2647),
                Bounds(1142, 2491, 1220, 2647),
                // Debajo de las pestañas, con el borde de abajo de la pastilla.
                Bounds(0, 2647, 1220, 2712),
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
    fun `los escudos tapan la barra de estado y la cabecera entre sus botones`() {
        assertThat(CoverZone.shields(snapshot()))
            .containsExactly(Bounds(0, 0, 1220, 118), Bounds(156, 118, 1064, 300))
            .inOrder()
    }

    @Test
    fun `sin cabecera o sin los lados del titulo solo queda el escudo de la barra de estado`() {
        assertThat(CoverZone.shields(snapshot(headerBottom = null))).containsExactly(Bounds(0, 0, 1220, 118))
        assertThat(CoverZone.shields(snapshot(titleLeft = null))).containsExactly(Bounds(0, 0, 1220, 118))
    }

    @Test
    fun `sin saber la pantalla o la lista no hay escudos`() {
        assertThat(CoverZone.shields(snapshot(screen = null))).isEmpty()
        assertThat(CoverZone.shields(snapshot(listTop = null))).isEmpty()
    }

    @Test
    fun `la pastilla es las pestañas mas lo que sobresale su fondo en dp`() {
        // Instagram 454.0.0.54.77 en un móvil de densidad 3,25 (520 dpi): 8 dp a los lados y 4 dp arriba y abajo.
        val tabs = Bounds(78, 2491, 1142, 2647)

        assertThat(CoverZone.expandNav(tabs, PillSpec(horizontalDp = 8, verticalDp = 4), density = 3.25f))
            .isEqualTo(Bounds(52, 2478, 1168, 2660))
    }

    @Test
    fun `la misma pastilla en otra densidad sale con otras medidas en pixeles pero las mismas en dp`() {
        // Con densidad 2 las pestañas miden 96 px de alto (48 dp): 8 dp = 16 px y 4 dp = 8 px.
        val tabs = Bounds(48, 1500, 672, 1596)

        assertThat(CoverZone.expandNav(tabs, PillSpec(8, 4), density = 2f)).isEqualTo(Bounds(32, 1492, 688, 1604))
    }

    @Test
    fun `sin medidas de pastilla la barra son las propias pestañas`() {
        val tabs = Bounds(78, 2491, 1142, 2647)

        assertThat(CoverZone.expandNav(tabs, pill = null, density = 3.25f)).isEqualTo(tabs)
    }

    @Test
    fun `el marco tiene el hueco de la pastilla y su radio es la mitad del alto`() {
        val frame = CoverZone.frame(snapshot(navLeft = 52, navTop = 2478, navBottom = 2660, navRounded = true))

        assertThat(frame)
            .isEqualTo(NavFrame(Bounds(0, 2478, 1220, 2712), Bounds(52, 2478, 1168, 2660), radius = 91))
    }

    @Test
    fun `sin pastilla o sin medidas no hay marco`() {
        assertThat(CoverZone.frame(snapshot(navRounded = false))).isNull()
        assertThat(CoverZone.frame(snapshot(navLeft = null, navRounded = true))).isNull()
        assertThat(CoverZone.frame(snapshot(screen = null, navRounded = true))).isNull()
    }
}
