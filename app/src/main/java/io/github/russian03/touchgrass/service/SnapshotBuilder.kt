package io.github.russian03.touchgrass.service

import android.view.accessibility.AccessibilityNodeInfo
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode

internal object SnapshotBuilder {

    /** Tope de nodos recorridos por evento, para no penalizar la batería en pantallas enormes. */
    private const val MAX_NODES = 2_000

    /** [texts]: por identificador, los textos que interesan; cualquier otro texto se ignora. */
    fun build(root: AccessibilityNodeInfo, texts: Map<String, Set<String>> = emptyMap()): ScreenSnapshot {
        val packageName = root.packageName?.toString().orEmpty()
        val idPrefix = "$packageName:id/"
        val nodes = mutableListOf<ViewNode>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue

            node.viewIdResourceName
                ?.let { localViewId(it, idPrefix) }
                ?.let { id ->
                    val text = texts[id]?.let { wanted -> node.text?.toString()?.takeIf { it in wanted } }
                    nodes += ViewNode(viewId = id, selected = node.isSelected, text = text)
                }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return ScreenSnapshot(packageName, nodes)
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
