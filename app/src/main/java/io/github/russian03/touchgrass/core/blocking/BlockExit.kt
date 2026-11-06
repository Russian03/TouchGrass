package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.rules.Action

/**
 * Lo que hace «Volver atrás» en la pantalla de bloqueo. Las vistas de Android solo las toca el
 * servicio: aquí se decide qué vista vale y qué hacer si no se encuentra.
 */
object BlockExit {
    const val MAX_CLICK_ANCESTORS = 4

    /** Ejecuta la salida de [action]: `Back` → [back]; `Click` → [click] y, si no lo consigue, [back]. */
    fun perform(action: Action, click: (Action.Click) -> Boolean, back: () -> Boolean) {
        when (action) {
            Action.Back -> back()
            // Si no encuentra a dónde ir, al menos vuelve atrás.
            is Action.Click -> click(action) || back()
        }
    }

    /** Si una vista encontrada por el id de [action] con texto [text] es el destino (sin textos en la acción, vale cualquiera). */
    fun matchesById(action: Action.Click, text: String?): Boolean = action.text.isEmpty() || text in action.text

    /** Si una vista encontrada al buscar [expected] lo tiene como texto o descripción exactos (no basta con contenerlo). */
    fun matchesText(expected: String, text: String?, description: String?): Boolean =
        text == expected || description == expected

    /**
     * [node] o el primero de sus padres que se puede pulsar, mirando como mucho [MAX_CLICK_ANCESTORS]
     * vistas en total (en TikTok el texto de la pestaña no se puede pulsar, pero su padre sí).
     */
    fun <N : Any> clickableSelfOrParent(node: N, parent: (N) -> N?, isClickable: (N) -> Boolean): N? =
        generateSequence(node, parent).take(MAX_CLICK_ANCESTORS).firstOrNull(isClickable)
}
