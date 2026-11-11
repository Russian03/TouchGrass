package io.github.russian03.touchgrass.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.github.russian03.touchgrass.core.rules.CoverSpec
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode

internal object SnapshotBuilder {

    /** Tope de nodos recorridos por evento, para no penalizar la batería en pantallas enormes. */
    private const val MAX_NODES = 2_000

    /**
     * [texts]: por identificador, los textos que interesan; cualquier otro texto se ignora.
     * [covers]: reglas con capa, de las que, además, se leen unas posiciones (cabecera, lista y barra de
     * navegación) y la pantalla; nunca contenido.
     */
    fun build(
        root: AccessibilityNodeInfo,
        texts: Map<String, Set<String>> = emptyMap(),
        covers: List<CoverSpec> = emptyList(),
    ): ScreenSnapshot {
        val packageName = root.packageName?.toString().orEmpty()
        val idPrefix = "$packageName:id/"
        val nodes = mutableListOf<ViewNode>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        var headerBottom: Int? = null
        var listTop: Int? = null
        var nav: Bounds? = null
        var title: Bounds? = null

        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue

            node.viewIdResourceName
                ?.let { localViewId(it, idPrefix) }
                ?.let { id ->
                    for (spec in covers) {
                        when (id) {
                            spec.header -> headerBottom = headerBottom ?: boundsOf(node).bottom
                            spec.list -> listTop = listTop ?: boundsOf(node).top
                            spec.nav -> nav = nav ?: boundsOf(node)
                            spec.title -> title = title ?: boundsOf(node)
                        }
                    }
                    val text = texts[id]?.let { wanted -> node.text?.toString()?.takeIf { it in wanted } }
                    nodes += ViewNode(viewId = id, selected = node.isSelected, text = text)
                }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        val screen = if (covers.isEmpty()) null else boundsOf(root)
        return ScreenSnapshot(
            packageName,
            nodes,
            screen,
            headerBottom,
            listTop,
            nav?.left,
            nav?.top,
            nav?.bottom,
            title?.left,
            title?.right,
        )
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
