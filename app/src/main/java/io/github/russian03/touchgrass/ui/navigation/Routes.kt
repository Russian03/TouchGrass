package io.github.russian03.touchgrass.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Onboarding

@Serializable
data object Home

/** Ajustes; con [protection] se abre en el apartado de la protección. */
@Serializable
data class SettingsRoute(val protection: Boolean = false)

/** Estadísticas: se abren en el mes actual. */
@Serializable
data object StatsRoute

/** Pedir tiempo: se elige la app y los minutos. */
@Serializable
data object UnlockRoute
