package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow

/** Qué partes de una lista de posts hay que tapar: las de los posts de quien no se sigue. */
object PostMask {
    /** Tope de capas a la vez (una ventana por zona). */
    const val MAX_ZONES = 3

    /**
     * Zonas a tapar, de arriba abajo, recortadas a [list]. Un post empieza en su fila con cabecera
     * y llega hasta la siguiente cabecera; sus filas contiguas se juntan en una sola zona. Las filas
     * sobre la primera cabecera visible son del post de arriba: se tapan si [leadingMarked].
     */
    fun zones(rows: List<ListRow>, list: Bounds?, leadingMarked: Boolean = false): List<Bounds> {
        if (list == null) return emptyList()
        val zones = mutableListOf<Bounds>()
        var marked = leadingMarked
        var current: Bounds? = null
        for (row in rows) {
            if (row.header) marked = row.marked
            if (marked) {
                current = current?.let { union(it, row.bounds) } ?: row.bounds
            } else {
                current?.let(zones::add)
                current = null
            }
        }
        current?.let(zones::add)
        return zones.mapNotNull { it.intersect(list) }.take(MAX_ZONES)
    }

    private fun union(a: Bounds, b: Bounds) =
        Bounds(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
}
