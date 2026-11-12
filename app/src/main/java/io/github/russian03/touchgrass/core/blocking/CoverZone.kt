package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.rules.PillSpec
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import kotlin.math.roundToInt

/** Dónde va la capa verde de Inicio: del borde de abajo de la cabecera al borde de arriba de la barra (incluye las historias). */
object CoverZone {
    /** Menos de esta parte de la pantalla es que algo (teclado, hoja) la ha encogido: no se cubre. */
    private const val MIN_SHARE = 0.30

    /** Más de esta parte sería tapar casi toda la pantalla por un id mal leído: no se cubre. */
    private const val MAX_SHARE = 0.90

    /**
     * Zona a cubrir en [snapshot], recortada a la pantalla, o `null` si falta alguna posición o la zona
     * no es creíble. Con el feed bajado la cabecera no se ve: empieza donde empieza la lista.
     */
    fun of(snapshot: ScreenSnapshot): Bounds? {
        val screen = snapshot.screen ?: return null
        val navTop = snapshot.navTop ?: return null
        val top = snapshot.headerBottom ?: snapshot.listTop ?: return null
        val zone = Bounds(screen.left, top, screen.right, navTop).intersect(screen) ?: return null
        val share = zone.height.toDouble() / screen.height
        return zone.takeIf { share in MIN_SHARE..MAX_SHARE }
    }

    /**
     * Franjas que tragan los toques de todo lo que rodea a las pestañas de la barra de navegación (los márgenes, lo de
     * debajo y, si la barra es una pastilla, también su borde de arriba y de abajo y sus puntas), salvo las propias
     * pestañas: por ahí el feed se podría seguir moviendo. No se ven: lo visible lo ponen la capa y, con pastilla, el
     * [frame]. Vacío si no se conocen las medidas de la barra.
     */
    fun edges(snapshot: ScreenSnapshot): List<Bounds> {
        val screen = snapshot.screen ?: return emptyList()
        val left = snapshot.navLeft ?: return emptyList()
        val top = snapshot.navTop ?: return emptyList()
        val bottom = snapshot.navBottom ?: return emptyList()
        // Sin pastilla las pestañas son la barra entera.
        val tabsLeft = snapshot.tabsLeft ?: left
        val tabsTop = snapshot.tabsTop ?: top
        val tabsBottom = snapshot.tabsBottom ?: bottom
        val tabsRight = screen.right - (tabsLeft - screen.left)
        return listOfNotNull(
            // Entre lo alto de la barra y el de las pestañas (el borde de arriba de la pastilla), a todo el ancho.
            Bounds(screen.left, top, screen.right, tabsTop).takeIf { tabsTop > top },
            // A los lados de las pestañas, con las puntas de la pastilla.
            Bounds(screen.left, tabsTop, tabsLeft, tabsBottom).takeIf {
                tabsLeft > screen.left && tabsBottom > tabsTop
            },
            Bounds(tabsRight, tabsTop, screen.right, tabsBottom).takeIf {
                tabsRight < screen.right &&
                    tabsBottom > tabsTop
            },
            // Debajo de las pestañas, a todo el ancho (incluye el borde de abajo de la pastilla).
            Bounds(screen.left, tabsBottom, screen.right, screen.bottom).takeIf { tabsBottom < screen.bottom },
        )
    }

    /**
     * Escudos transparentes, que no se ven pero tragan los toques:
     * - Sobre la barra de estado, para que no se pueda bajar el panel de notificaciones con la capa puesta.
     * - Sobre la cabecera, la cabecera se deja tocable solo en sus botones: el título (el selector de «Para ti»)
     *   y los huecos entre ellos no se pueden tocar ni deslizar.
     * Vacío si no se conoce la pantalla; sin cabecera a la vista (feed bajado) o sin los lados del título solo
     * queda el de la barra de estado.
     */
    fun shields(snapshot: ScreenSnapshot): List<Bounds> {
        val screen = snapshot.screen ?: return emptyList()
        val top = snapshot.listTop ?: return emptyList()
        val statusBar = Bounds(screen.left, screen.top, screen.right, top).takeIf { top > screen.top }
        val bottom = snapshot.headerBottom
        val left = snapshot.titleLeft
        val right = snapshot.titleRight
        val title =
            if (bottom != null && left != null && right != null) {
                Bounds(left, top, right, bottom).intersect(screen)
            } else {
                null
            }
        return listOfNotNull(statusBar, title)
    }

    /**
     * La barra de navegación entera a partir de sus pestañas ([tabs]): con [pill], se le suma lo que sobresale
     * el fondo de la pastilla (medidas en dp convertidas con [density]); sin ella, son las propias pestañas.
     */
    fun expandNav(tabs: Bounds, pill: PillSpec?, density: Float): Bounds {
        if (pill == null) return tabs
        val padX = (pill.horizontalDp * density).roundToInt()
        val padY = (pill.verticalDp * density).roundToInt()
        return Bounds(tabs.left - padX, tabs.top - padY, tabs.right + padX, tabs.bottom + padY)
    }

    /**
     * El marco liso que rodea la pastilla de la barra de navegación, con un hueco redondeado exacto (radio = mitad
     * del alto) para que se vea la pastilla de Instagram entera, sin recortarle las puntas. `null` si la barra no es
     * una pastilla (entonces las franjas lisas rodean el rectángulo) o si faltan medidas.
     */
    fun frame(snapshot: ScreenSnapshot): NavFrame? {
        if (!snapshot.navRounded) return null
        val screen = snapshot.screen ?: return null
        val left = snapshot.navLeft ?: return null
        val top = snapshot.navTop ?: return null
        val bottom = snapshot.navBottom ?: return null
        val hole = Bounds(left, top, screen.right - (left - screen.left), bottom)
        if (hole.right <= hole.left || hole.height <= 0) return null
        return NavFrame(Bounds(screen.left, top, screen.right, screen.bottom), hole, hole.height / 2)
    }
}

/** Zona lisa de [area] con un hueco redondeado [hole] (de radio [radius]); coordenadas de pantalla. */
data class NavFrame(val area: Bounds, val hole: Bounds, val radius: Int)
