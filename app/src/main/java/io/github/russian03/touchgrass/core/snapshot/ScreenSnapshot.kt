package io.github.russian03.touchgrass.core.snapshot

/**
 * Lo único que TouchGrass sabe de una pantalla: qué vistas con identificador hay
 * visibles y si están seleccionadas. A propósito no guarda descripciones ni nombres de
 * usuario, y de los textos solo los que nombran las reglas (p. ej. «Para ti»).
 */
data class ScreenSnapshot(
    val packageName: String,
    val nodes: List<ViewNode>,
    /** Posición en pantalla de la lista que pide una regla con `mask`; `null` si no se ha leído. */
    val listBounds: Bounds? = null,
    /** Filas de esa lista (solo posiciones y dos marcas, nunca contenido); vacío si no se han leído. */
    val rows: List<ListRow> = emptyList(),
) {
    private val byId: Map<String, List<ViewNode>> = nodes.groupBy { it.viewId }

    fun has(viewId: String, selected: Boolean? = null, text: Collection<String> = emptyList()): Boolean =
        byId[viewId].orEmpty().any {
            (selected == null || it.selected == selected) && (text.isEmpty() || it.text in text)
        }
}

data class ViewNode(
    /** Identificador sin el prefijo del paquete, p. ej. `clips_tab`. */
    val viewId: String,
    val selected: Boolean = false,
    /** Texto de la vista, solo si es uno de los que buscan las reglas; si no, `null`. */
    val text: String? = null,
)

/** Rectángulo en píxeles de pantalla. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top

    /** Parte común con [other], o `null` si no se tocan o la intersección queda vacía. */
    fun intersect(other: Bounds): Bounds? {
        val common =
            Bounds(
                maxOf(left, other.left),
                maxOf(top, other.top),
                minOf(right, other.right),
                minOf(bottom, other.bottom),
            )
        return common.takeIf { it.right > it.left && it.bottom > it.top }
    }
}

/**
 * Una fila de la lista de Inicio: su posición, si lleva la cabecera de un post ([header]) y si esa
 * cabecera es de alguien que no sigues ([marked], p. ej. lleva «Seguir»). No guarda contenido.
 */
data class ListRow(val bounds: Bounds, val header: Boolean, val marked: Boolean)
