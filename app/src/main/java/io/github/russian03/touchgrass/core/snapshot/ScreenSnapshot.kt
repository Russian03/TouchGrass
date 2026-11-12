package io.github.russian03.touchgrass.core.snapshot

/**
 * Lo único que TouchGrass sabe de una pantalla: qué vistas con identificador hay
 * visibles y si están seleccionadas. A propósito no guarda descripciones ni nombres de
 * usuario, y de los textos solo los que nombran las reglas (p. ej. «Para ti»).
 */
data class ScreenSnapshot(
    val packageName: String,
    val nodes: List<ViewNode>,
    /** Pantalla de la ventana leída; `null` si no se ha leído (solo con una regla `cover`). */
    val screen: Bounds? = null,
    /** Borde de abajo de la cabecera de Inicio; `null` si no se ve (p. ej. con el feed bajado). */
    val headerBottom: Int? = null,
    /** Borde de arriba de la lista del feed; `null` si no se ha leído. */
    val listTop: Int? = null,
    /** Borde izquierdo de la barra de navegación (con `navRounded`, la pastilla entera); el derecho es simétrico. */
    val navLeft: Int? = null,
    /** Borde de arriba de la barra de navegación; `null` si no se ha leído. */
    val navTop: Int? = null,
    /** Borde de abajo de la barra de navegación (las pestañas); `null` si no se ha leído. */
    val navBottom: Int? = null,
    /** La barra de navegación (`navLeft`…`navBottom`) es una pastilla redondeada del todo, no un rectángulo. */
    val navRounded: Boolean = false,
    /** Lo que sí se toca de la barra: sus pestañas (izquierda, arriba, abajo; la derecha es simétrica). `null` = la barra entera. */
    val tabsLeft: Int? = null,
    val tabsTop: Int? = null,
    val tabsBottom: Int? = null,
    /** Bordes izquierdo y derecho del título de la cabecera (entre sus botones); `null` si no se han leído. */
    val titleLeft: Int? = null,
    val titleRight: Int? = null,
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
