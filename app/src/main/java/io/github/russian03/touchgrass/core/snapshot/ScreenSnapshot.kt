package io.github.russian03.touchgrass.core.snapshot

/**
 * Lo único que TouchGrass sabe de una pantalla: qué vistas con identificador hay
 * visibles y si están seleccionadas. A propósito no guarda descripciones ni nombres de
 * usuario, y de los textos solo los que nombran las reglas (p. ej. «Para ti»).
 */
data class ScreenSnapshot(val packageName: String, val nodes: List<ViewNode>) {
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
