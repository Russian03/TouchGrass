package io.github.russian03.touchgrass.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.russian03.touchgrass.ui.design.LimeHeaderScope
import io.github.russian03.touchgrass.ui.design.LocalLimeHeaderScope
import io.github.russian03.touchgrass.ui.home.HomeScreen
import io.github.russian03.touchgrass.ui.onboarding.OnboardingScreen
import io.github.russian03.touchgrass.ui.settings.SettingsScreen
import io.github.russian03.touchgrass.ui.stats.StatsScreen
import io.github.russian03.touchgrass.ui.unlock.UnlockScreen

/**
 * Pantallas de la app. Con [homeRequest] (desde la pantalla de bloqueo) se vuelve a Inicio y se
 * avisa con [onHomeRequestHandled].
 * [statsRequest] es el mes cuyas estadísticas se han pedido desde la notificación del resumen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    start: Any,
    homeRequest: Boolean = false,
    onHomeRequestHandled: () -> Unit = {},
    statsRequest: String? = null,
    onStatsRequestHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    // Las pantallas entran con fundido y creciendo un poco, y salen con fundido y encogiendo.
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Inicio y Ajustes comparten el banner lima: al ir de una a otra cambia de tamaño en lugar de fundirse.
    SharedTransitionLayout {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = { fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f) },
            exitTransition = { fadeOut(effects) + scaleOut(spatial, targetScale = 1.02f) },
            popEnterTransition = { fadeIn(effects) + scaleIn(spatial, initialScale = 1.02f) },
            popExitTransition = { fadeOut(effects) + scaleOut(spatial, targetScale = 0.96f) },
        ) {
            composable<Onboarding> {
                OnboardingScreen(
                    onFinished = {
                        // Sin vuelta atrás a los primeros pasos.
                        nav.navigate(Home) { popUpTo<Onboarding> { inclusive = true } }
                    },
                )
            }
            composable<Home> {
                CompositionLocalProvider(
                    LocalLimeHeaderScope provides LimeHeaderScope(this@SharedTransitionLayout, this),
                ) {
                    HomeScreen(
                        onOpenSettings = { nav.navigate(SettingsRoute()) { launchSingleTop = true } },
                        onOpenProtection = {
                            nav.navigate(SettingsRoute(protection = true)) { launchSingleTop = true }
                        },
                        onOpenStats = { month ->
                            nav.navigate(StatsRoute(month?.toString())) { launchSingleTop = true }
                        },
                        onOpenUnlock = { nav.navigate(UnlockRoute) { launchSingleTop = true } },
                    )
                }
            }
            composable<StatsRoute> { entry ->
                StatsScreen(initialMonth = entry.toRoute<StatsRoute>().month, onBack = { nav.popBackStack() })
            }
            composable<SettingsRoute> { entry ->
                CompositionLocalProvider(
                    LocalLimeHeaderScope provides LimeHeaderScope(this@SharedTransitionLayout, this),
                ) {
                    SettingsScreen(
                        focusProtection = entry.toRoute<SettingsRoute>().protection,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable<UnlockRoute> {
                UnlockScreen(
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }

    LaunchedEffect(homeRequest) {
        if (!homeRequest) return@LaunchedEffect
        onHomeRequestHandled()
        // Durante los primeros pasos no hay Inicio al que volver.
        if (nav.currentDestination?.hasRoute<Onboarding>() == true) return@LaunchedEffect
        nav.navigate(Home) {
            popUpTo<Home> { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(statsRequest) {
        val month = statsRequest ?: return@LaunchedEffect
        onStatsRequestHandled()
        if (nav.currentDestination?.hasRoute<Onboarding>() == true) return@LaunchedEffect
        nav.navigate(StatsRoute(month)) {
            popUpTo<Home>()
            launchSingleTop = true
        }
    }
}
