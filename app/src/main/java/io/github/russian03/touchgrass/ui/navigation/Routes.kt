package io.github.russian03.touchgrass.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Onboarding

@Serializable
data object Home

@Serializable
data object SettingsRoute

/** Estadísticas; con [month] (`2026-09`) se abre ese mes en lugar del actual. */
@Serializable
data class StatsRoute(val month: String? = null)

/** Pedir tiempo; con [packageName] se entra directamente en los minutos de esa app. */
@Serializable
data class UnlockRoute(val packageName: String? = null)
