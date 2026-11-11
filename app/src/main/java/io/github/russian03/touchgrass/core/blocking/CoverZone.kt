package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot

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
     * Franjas que tapan lo que la barra de navegación deja al aire (sus márgenes laterales y lo que hay
     * debajo): por ahí el feed se podría seguir moviendo. Vacío si no se conocen las medidas de la barra.
     */
    fun edges(snapshot: ScreenSnapshot): List<Bounds> {
        val screen = snapshot.screen ?: return emptyList()
        val left = snapshot.navLeft ?: return emptyList()
        val top = snapshot.navTop ?: return emptyList()
        val bottom = snapshot.navBottom ?: return emptyList()
        val right = screen.right - (left - screen.left)
        return listOfNotNull(
            Bounds(screen.left, bottom, screen.right, screen.bottom).takeIf { bottom < screen.bottom },
            Bounds(screen.left, top, left, bottom).takeIf { left > screen.left && bottom > top },
            Bounds(right, top, screen.right, bottom).takeIf { right < screen.right && bottom > top },
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
}
