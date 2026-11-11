package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot

/** Dónde va la capa verde de Inicio: del borde de abajo de las historias al borde de arriba de la barra. */
object CoverZone {
    /** Menos de esta parte de la pantalla es que algo (teclado, hoja) la ha encogido: no se cubre. */
    private const val MIN_SHARE = 0.30

    /** Más de esta parte sería tapar casi toda la pantalla por un id mal leído: no se cubre. */
    private const val MAX_SHARE = 0.90

    /**
     * Zona a cubrir en [snapshot], recortada a la pantalla, o `null` si falta alguna posición o la zona
     * no es creíble. Sin historias a la vista empieza bajo la cabecera.
     */
    fun of(snapshot: ScreenSnapshot): Bounds? {
        val screen = snapshot.screen ?: return null
        val navTop = snapshot.navTop ?: return null
        val top = snapshot.storiesBottom ?: snapshot.headerBottom ?: return null
        val zone = Bounds(screen.left, top, screen.right, navTop).intersect(screen) ?: return null
        val share = zone.height.toDouble() / screen.height
        return zone.takeIf { share in MIN_SHARE..MAX_SHARE }
    }
}
