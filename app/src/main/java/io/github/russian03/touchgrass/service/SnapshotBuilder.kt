package io.github.russian03.touchgrass.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.github.russian03.touchgrass.core.rules.PostMaskSpec
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode

internal object SnapshotBuilder {

    /** Tope de nodos recorridos por evento, para no penalizar la batería en pantallas enormes. */
    private const val MAX_NODES = 2_000

    /** Tope de nodos que se miran dentro de cada fila de una lista de posts; cuenta para [MAX_NODES]. */
    private const val MAX_ROW_NODES = 200

    /**
     * [texts]: por identificador, los textos que interesan; cualquier otro texto se ignora.
     * [masks]: listas de posts de las que, además, se leen las posiciones de sus filas (nunca su contenido).
     */
    fun build(
        root: AccessibilityNodeInfo,
        texts: Map<String, Set<String>> = emptyMap(),
        masks: List<PostMaskSpec> = emptyList(),
    ): ScreenSnapshot {
        val packageName = root.packageName?.toString().orEmpty()
        val idPrefix = "$packageName:id/"
        val nodes = mutableListOf<ViewNode>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        var listBounds: Bounds? = null
        var rows = emptyList<ListRow>()

        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue

            node.viewIdResourceName
                ?.let { localViewId(it, idPrefix) }
                ?.let { id ->
                    masks.firstOrNull { it.list == id }?.takeIf { listBounds == null }?.let { spec ->
                        listBounds = boundsOf(node)
                        rows = readRows(node, spec, idPrefix)
                        visited += rows.size
                    }
                    val text = texts[id]?.let { wanted -> node.text?.toString()?.takeIf { it in wanted } }
                    nodes += ViewNode(viewId = id, selected = node.isSelected, text = text)
                }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return ScreenSnapshot(packageName, nodes, listBounds, rows)
    }

    /**
     * Una fila por cada hijo directo de [list]: su posición, si su subárbol tiene la cabecera de un post
     * y si esa cabecera trae el ancla (p. ej. «Seguir»), aunque no se vea. Solo ids, ningún texto.
     */
    private fun readRows(list: AccessibilityNodeInfo, spec: PostMaskSpec, idPrefix: String): List<ListRow> =
        (0 until list.childCount).mapNotNull { i ->
            val row = list.getChild(i) ?: return@mapNotNull null
            var header = false
            var marked = false
            var seen = 0
            val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(row) }
            while (stack.isNotEmpty() && seen < MAX_ROW_NODES) {
                val node = stack.removeLast()
                seen++
                when (node.viewIdResourceName?.let { localViewId(it, idPrefix) }) {
                    spec.header -> header = true
                    spec.anchor -> marked = true
                }
                for (c in 0 until node.childCount) node.getChild(c)?.let(stack::add)
            }
            ListRow(boundsOf(row), header, marked)
        }

    private fun boundsOf(node: AccessibilityNodeInfo): Bounds {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    /**
     * Los identificadores de la app se guardan sin prefijo (`clips_tab`); los del propio Android,
     * que algunas apps usan (las pestañas de TikTok son `android:id/text1`), con él. El resto se ignora.
     */
    private fun localViewId(resourceName: String, idPrefix: String): String? = when {
        resourceName.startsWith(idPrefix) -> resourceName.removePrefix(idPrefix)
        resourceName.startsWith(ANDROID_ID_PREFIX) -> resourceName
        else -> null
    }

    /** Nombre completo de [viewId] tal como lo usa Android, para buscar la vista en pantalla. */
    fun resourceName(packageName: String, viewId: String): String =
        if (viewId.startsWith(ANDROID_ID_PREFIX)) viewId else "$packageName:id/$viewId"

    private const val ANDROID_ID_PREFIX = "android:id/"
}
