package io.github.russian03.touchgrass.core.rules

import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot

class RuleEngine(ruleSets: List<RuleSet>) {

    private val rulesByPackage: Map<String, List<Rule>> =
        ruleSets.groupBy({ it.packageName }, { it.rules }).mapValues { (_, lists) -> lists.flatten() }

    val packages: Set<String> get() = rulesByPackage.keys

    /** Primera regla que se cumple en [snapshot], o `null` si la pantalla está permitida. */
    fun evaluate(snapshot: ScreenSnapshot): Rule? =
        rulesByPackage[snapshot.packageName].orEmpty().firstOrNull { it.matches(snapshot) }

    private fun Rule.matches(snapshot: ScreenSnapshot): Boolean =
        match.allOf.all { snapshot.satisfies(it) } && match.noneOf.none { snapshot.satisfies(it) }

    private fun ScreenSnapshot.satisfies(condition: Condition): Boolean = has(condition.viewId, condition.selected)
}
