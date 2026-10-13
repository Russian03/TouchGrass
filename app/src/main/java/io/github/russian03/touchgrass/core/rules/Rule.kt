package io.github.russian03.touchgrass.core.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Conjunto de reglas para una app, cargado desde `assets/rules/<app>.json`. */
@Serializable
data class RuleSet(
    val version: Int,
    val packageName: String,
    val rules: List<Rule>,
    val places: List<Place> = emptyList(),
    /** Nombre de la app que se muestra al usuario (p. ej. "Instagram"). */
    val name: String = packageName,
    /** Lo que se bloquea, tal como se muestra al usuario (p. ej. "Reels"). */
    val blocks: String = "",
)

/**
 * Pantalla de la app que sirve de punto de partida (p. ej. un perfil o un chat). TouchGrass
 * recuerda el último lugar por el que ha pasado el usuario para saber de dónde viene cuando
 * abre algo, ya que el visor de Reels es el mismo venga de donde venga.
 */
@Serializable
data class Place(val id: String, val match: Match)

/**
 * Zona bloqueada. Si se cumple [match], se tapa la pantalla; [action] es lo que se hace
 * cuando el usuario pulsa "Volver atrás". [allowFrom] la deja libre si el usuario llega
 * desde ciertos lugares, y [pager] es la vista paginada en la que se cuenta el scroll.
 */
@Serializable
data class Rule(
    val id: String,
    val description: String,
    val match: Match,
    val action: Action,
    val allowFrom: List<Allowance> = emptyList(),
    val pager: String? = null,
)

/** Permite la regla si se llega desde [place]; con [untilScroll], solo hasta pasar de página. */
@Serializable
data class Allowance(val place: String, val untilScroll: Boolean = false)

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
