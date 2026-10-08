package io.github.russian03.touchgrass.service

import android.view.accessibility.AccessibilityNodeInfo
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode

internal object SnapshotBuilder {

    /** Tope de nodos recorridos por evento, para no penalizar la batería en pantallas enormes. */
    private const val MAX_NODES = 2_000

    fun build(root: AccessibilityNodeInfo): ScreenSnapshot {
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
                ?.takeIf { it.startsWith(idPrefix) }
                ?.let { nodes += ViewNode(viewId = it.removePrefix(idPrefix), selected = node.isSelected) }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
        return ScreenSnapshot(packageName, nodes)
    }
}
