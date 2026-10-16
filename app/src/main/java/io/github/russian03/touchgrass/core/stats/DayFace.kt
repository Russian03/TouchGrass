package io.github.russian03.touchgrass.core.stats

/**
 * Cara que resume un día de uso. Depende del porcentaje del límite gastado y, en los días de poco
 * uso, de la racha: cuantos más días seguidos con el scroll a raya, mejor cara.
 */
enum class DayFace {
    /** Aún no había estadísticas. */
    Untracked,

    /** Poco uso con racha de [DayFaces.MYTHIC_STREAK] días o más: dorado, gafas y corona. */
    Mythic,

    /** Poco uso con racha de [DayFaces.LEGEND_STREAK] días o más: dorado y gafas. */
    Legend,

    /** Poco uso con racha de [DayFaces.COOL_STREAK] días o más: gafas de sol. */
    Cool,

    /** Sin pedir tiempo. */
    Free,

    /** Hasta el 25 % del límite. */
    Happy,

    /** Hasta el 50 %. */
    Calm,

    /** Hasta el 75 %. */
    Neutral,

    /** Más del 75 % sin agotarlo. */
    Tight,

    /** Saldo agotado. */
    Sad,

    /** Se quitó la protección: no cuenta lo que se usó. */
    Disabled,
}

object DayFaces {
    /** Uso máximo (fracción del límite) para que un día cuente como «poco uso» y sume racha. */
    const val LOW_USE = 0.25

    const val COOL_STREAK = 3
    const val LEGEND_STREAK = 7
    const val MYTHIC_STREAK = 14

    private const val CALM_USE = 0.5
    private const val NEUTRAL_USE = 0.75

    /**
     * Fracción del límite gastada en [row]. Sin desbloqueos es 0. Sin límite apuntado (no debería
     * pasar si se pidió tiempo) cuenta como gastado del todo.
     */
    fun usage(row: DayRow): Double {
        if (row.unlockedMinutes == 0) return 0.0
        val limit = row.stats.limitMinutes ?: return 1.0
        return if (limit <= 0) 1.0 else row.unlockedMinutes.toDouble() / limit
    }

    /** Día de poco uso: suma a la racha. */
    fun isLow(row: DayRow): Boolean = !row.stats.protectionOff && !row.exhausted && usage(row) <= LOW_USE

    /** Cara de [row], que lleva una racha de [streak] días de poco uso contándolo a él. */
    fun face(row: DayRow, streak: Int): DayFace {
        if (row.stats.protectionOff) return DayFace.Disabled
        if (row.exhausted) return DayFace.Sad
        val use = usage(row)
        return when {
            use <= LOW_USE && streak >= MYTHIC_STREAK -> DayFace.Mythic
            use <= LOW_USE && streak >= LEGEND_STREAK -> DayFace.Legend
            use <= LOW_USE && streak >= COOL_STREAK -> DayFace.Cool
            use == 0.0 -> DayFace.Free
            use <= LOW_USE -> DayFace.Happy
            use <= CALM_USE -> DayFace.Calm
            use <= NEUTRAL_USE -> DayFace.Neutral
            use < 1.0 -> DayFace.Tight
            else -> DayFace.Sad
        }
    }
}
