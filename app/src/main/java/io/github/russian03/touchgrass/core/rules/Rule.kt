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
 * [coveredBy] es una hoja que puede tapar la zona (p. ej. los comentarios, que al abrirse son
 * lo único que se ve): si la regla casaba en la mirada anterior y ahora solo se ve esa hoja,
 * sigue bloqueando. Por sí sola no bloquea. Con [silentBack] no se muestra
 * pausa: el servicio cierra lo que haya abierto con un atrás silencioso (la vista previa al
 * mantener pulsado, o su menú). Con [cover] no hay pausa: se cubre el feed con una capa sobre la zona que
 * dibujan las posiciones de [CoverSpec], y el botón de la capa hace su propia salida ([action] solo es
 * obligatoria por el formato).
 */
@Serializable
data class Rule(
    val id: String,
    val description: String,
    val match: Match,
    val action: Action,
    val allowFrom: List<Allowance> = emptyList(),
    val pager: String? = null,
    val coveredBy: Match? = null,
    val silentBack: Boolean = false,
    val cover: CoverSpec? = null,
)

/**
 * Qué posiciones se leen para saber dónde va la capa: [header] es el id de la cabecera (su borde de abajo
 * es donde empieza la capa), [list] el de la lista del feed (donde empieza si la cabecera no se ve) y [nav]
 * el de una pestaña de la barra de navegación (su borde de arriba es donde acaba la capa; sus lados y su
 * parte de abajo, donde van las franjas que tapan el resto) y [title] el del título de la cabecera (entre
 * cuyos lados va un escudo transparente que no deja tocarlo ni deslizar por ahí). La capa cubre también
 * las historias.
 */
@Serializable
data class CoverSpec(val header: String, val nav: String, val list: String? = null, val title: String? = null)

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

/**
 * Existe una vista visible con [viewId] y, si se indica, con ese estado de selección y uno
 * de los textos de [text]. El texto sirve para vistas que comparten identificador, como las
 * pestañas de TikTok («Para ti», «Siguiendo»…).
 */
@Serializable
data class Condition(val viewId: String, val selected: Boolean? = null, val text: List<String> = emptyList())

@Serializable
sealed interface Action {
    /** Pulsar "atrás". */
    @Serializable
    @SerialName("back")
    data object Back : Action

    /**
     * Pulsar la vista [target] (p. ej. la pestaña de Inicio). Con [text], la que tenga uno de
     * esos textos; sin [target], cualquier vista cuyo texto o descripción sea uno de [text]
     * (p. ej. la pestaña «Mensajes» de TikTok, que no tiene un identificador estable). Si no
     * existe, se hace "atrás".
     */
    @Serializable
    @SerialName("click")
    data class Click(val target: String? = null, val text: List<String> = emptyList()) : Action {
        init {
            require(target != null || text.isNotEmpty()) { "Un click necesita target o text" }
        }
    }
}
