package io.github.russian03.touchgrass.core.navigation

/**
 * Recuerda de dónde viene el usuario y si ha pasado de página en el visor que tiene abierto.
 * Instagram usa el mismo visor para los reels de Explorar, de un perfil o de un chat, así
 * que lo único que los distingue es el último lugar por el que se ha pasado.
 */
class ViewerSession {

    /** Último lugar reconocido (p. ej. "profile" o "chat"), o `null` si aún no se ha visto ninguno. */
    var origin: String? = null
        private set

    /** Si el usuario ha pasado a otra página desde que abrió el visor. */
    var scrolled: Boolean = false
        private set

    private var startPage: Int? = null

    /** Lugar reconocido en pantalla. Estar en un lugar significa que el visor está cerrado. */
    fun onPlace(place: String) {
        origin = place
        startPage = null
        scrolled = false
    }

    /** El visor muestra la página [page]; la primera que llega es en la que se abrió. */
    fun onPagerScrolled(page: Int) {
        if (page < 0) return
        val start = startPage
        if (start == null) {
            startPage = page
        } else if (page != start) {
            scrolled = true
        }
    }
}
