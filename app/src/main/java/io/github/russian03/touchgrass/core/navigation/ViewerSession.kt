package io.github.russian03.touchgrass.core.navigation

/**
 * Recuerda de dónde viene el usuario y si ha empezado a pasar de reel en el visor que tiene
 * abierto. Instagram usa el mismo visor para los reels de Explorar, de un perfil o de un chat,
 * así que lo único que los distingue es el último lugar por el que se ha pasado.
 *
 * El índice de página que da Instagram no sirve para saber si se ha pasado de reel: mientras
 * se arrastra sigue marcando la página de antes. En cambio, el visor solo hace scroll cuando
 * el usuario lo arrastra, salvo justo al abrirse; por eso, cualquier scroll que llegue más de
 * [settleMs] después de ver el visor abierto cuenta como pasar al siguiente.
 */
class ViewerSession(private val settleMs: Long = DEFAULT_SETTLE_MS) {

    /** Último lugar reconocido (p. ej. "profile" o "chat"), o `null` si aún no se ha visto ninguno. */
    var origin: String? = null
        private set

    /** Si el usuario ha empezado a pasar de reel desde que abrió el visor. */
    var scrolled: Boolean = false
        private set

    private var viewerShownAt: Long? = null

    /** Lugar reconocido en pantalla. Estar en un lugar significa que el visor está cerrado. */
    fun onPlace(place: String) {
        origin = place
        viewerShownAt = null
        scrolled = false
    }

    /** El visor se ve abierto en pantalla. */
    fun onViewerShown(now: Long) {
        if (viewerShownAt == null) viewerShownAt = now
    }

    /** El visor ha hecho scroll. Antes de verlo abierto, o justo al abrirse, no cuenta. */
    fun onPagerScrolled(now: Long) {
        val shownAt = viewerShownAt ?: return
        if (now - shownAt >= settleMs) scrolled = true
    }

    private companion object {
        const val DEFAULT_SETTLE_MS = 500L
    }
}
