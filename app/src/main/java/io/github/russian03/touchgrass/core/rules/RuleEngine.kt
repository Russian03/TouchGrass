package io.github.russian03.touchgrass.core.rules

import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot

class RuleEngine(ruleSets: List<RuleSet>) {

    private val rulesByPackage: Map<String, List<Rule>> =
        ruleSets.groupBy({ it.packageName }, { it.rules }).mapValues { (_, lists) -> lists.flatten() }

    private val placesByPackage: Map<String, List<Place>> =
        ruleSets.groupBy({ it.packageName }, { it.places }).mapValues { (_, lists) -> lists.flatten() }

    val packages: Set<String> get() = rulesByPackage.keys

    /** Vistas paginadas en las que se cuenta el scroll, por app. */
    fun pagers(packageName: String): Set<String> = rulesByPackage[packageName].orEmpty().mapNotNull { it.pager }.toSet()

    /** Primer lugar que se reconoce en [snapshot], o `null` si no es ninguno. */
    fun place(snapshot: ScreenSnapshot): String? =
        placesByPackage[snapshot.packageName].orEmpty().firstOrNull { snapshot.satisfies(it.match) }?.id

    /**
     * Primera regla que se cumple en [snapshot] y no está permitida por venir de [origin]
     * (con [scrolled] si el usuario ya ha pasado de página), o `null` si la pantalla está permitida.
     */
    fun evaluate(snapshot: ScreenSnapshot, origin: String? = null, scrolled: Boolean = false): Rule? =
        rulesByPackage[snapshot.packageName].orEmpty().firstOrNull { rule ->
            snapshot.satisfies(rule.match) &&
                rule.allowFrom.none { it.place == origin && !(it.untilScroll && scrolled) }
        }

    private fun ScreenSnapshot.satisfies(match: Match): Boolean =
        match.allOf.all { satisfies(it) } && match.noneOf.none { satisfies(it) }

    private fun ScreenSnapshot.satisfies(condition: Condition): Boolean = has(condition.viewId, condition.selected)
}
