package io.github.russian03.touchgrass.core.snapshot

/**
 * Lo único que TouchGrass sabe de una pantalla: qué vistas con identificador hay
 * visibles y si están seleccionadas. A propósito no guarda textos, descripciones
 * ni nombres de usuario.
 */
data class ScreenSnapshot(val packageName: String, val nodes: List<ViewNode>) {
    private val byId: Map<String, List<ViewNode>> = nodes.groupBy { it.viewId }

    fun has(viewId: String, selected: Boolean? = null): Boolean =
        byId[viewId].orEmpty().any { selected == null || it.selected == selected }

    val viewIds: Set<String> get() = byId.keys
}

data class ViewNode(
    /** Identificador sin el prefijo del paquete, p. ej. `clips_tab`. */
    val viewId: String,
    val selected: Boolean = false,
)
