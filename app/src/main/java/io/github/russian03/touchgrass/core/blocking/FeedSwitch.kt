package io.github.russian03.touchgrass.core.blocking

/**
 * «Ir a Siguiendo» en Instagram, en dos pasos: pulsar el selector de la cabecera de Inicio y, cuando
 * sale el menú (en una ventana propia), pulsar «Siguiendo». Solo decide qué paso toca; las vistas las
 * toca el servicio. Si el menú no sale a tiempo se rinde y no reintenta hasta un nuevo [start].
 */
class FeedSwitch {
    sealed interface Step {
        /** Nada que hacer ahora (esperar, o no hay salida en marcha). */
        data object None : Step

        /** Pulsar el selector de la cabecera de Inicio. */
        data object OpenMenu : Step

        /** Pulsar «Siguiendo» en el menú abierto. */
        data object PickFollowing : Step
    }

    private enum class Phase { IDLE, STARTED, WAITING_MENU, DONE, GAVE_UP }

    private var phase = Phase.IDLE
    private var openedAtMs = 0L

    /** Se rindió porque el menú no salió a tiempo. */
    val gaveUp: Boolean get() = phase == Phase.GAVE_UP

    /** Empieza una salida nueva (el usuario ha pulsado «Ir a Siguiendo»). */
    fun start(@Suppress("UNUSED_PARAMETER") nowMs: Long) {
        phase = Phase.STARTED
    }

    /** Qué hacer ahora; [menuShown] = si lo que se ve es el menú del selector. */
    fun next(nowMs: Long, menuShown: Boolean): Step = when (phase) {
        Phase.IDLE, Phase.DONE, Phase.GAVE_UP -> Step.None
        Phase.STARTED ->
            if (menuShown) {
                phase = Phase.DONE
                Step.PickFollowing
            } else {
                phase = Phase.WAITING_MENU
                openedAtMs = nowMs
                Step.OpenMenu
            }
        Phase.WAITING_MENU ->
            when {
                menuShown -> {
                    phase = Phase.DONE
                    Step.PickFollowing
                }
                nowMs - openedAtMs >= MENU_TIMEOUT_MS -> {
                    phase = Phase.GAVE_UP
                    Step.None
                }
                else -> Step.None
            }
    }

    companion object {
        /** Lo que se espera a que salga el menú tras pulsar el selector. */
        const val MENU_TIMEOUT_MS = 1_500L
    }
}
