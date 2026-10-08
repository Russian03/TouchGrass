package io.github.russian03.touchgrass.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Conjunto de reglas para una app, cargado desde `assets/rules/<app>.json`. */
@Serializable
data class RuleSet(val version: Int, val packageName: String, val rules: List<Rule>)

@Serializable
data class Rule(val id: String, val description: String, val match: Match, val action: Action)

/** La regla se cumple si se cumplen todas las condiciones de [allOf] y ninguna de [noneOf]. */
@Serializable
data class Match(val allOf: List<Condition>, val noneOf: List<Condition> = emptyList()) {
    init {
        require(allOf.isNotEmpty()) { "Una regla necesita al menos una condición en allOf" }
    }
}

/** Existe una vista visible con [viewId] y, si se indica, con ese estado de selección. */
@Serializable
data class Condition(val viewId: String, val selected: Boolean? = null)

@Serializable
sealed interface Action {
    /** Pulsar "atrás". */
    @Serializable
    @SerialName("back")
    data object Back : Action

    /** Pulsar la vista [target] (p. ej. la pestaña de Inicio). Si no existe, se hace "atrás". */
    @Serializable
    @SerialName("click")
    data class Click(val target: String) : Action
}
