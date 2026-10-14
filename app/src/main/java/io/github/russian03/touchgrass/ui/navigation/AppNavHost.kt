package io.github.russian03.touchgrass.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.russian03.touchgrass.ui.home.HomeScreen
import io.github.russian03.touchgrass.ui.onboarding.OnboardingScreen
import io.github.russian03.touchgrass.ui.settings.SettingsScreen
import io.github.russian03.touchgrass.ui.stats.StatsScreen
import io.github.russian03.touchgrass.ui.unlock.UnlockScreen

/**
 * Pantallas de la app. [unlockRequest] es la app para la que se ha pedido tiempo desde la
 * pantalla de bloqueo; se abre su pantalla de pedir tiempo y se avisa con [onUnlockRequestHandled].
 * [statsRequest] es el mes cuyas estadísticas se han pedido desde la notificación del resumen.
 */
@Composable
fun AppNavHost(
    start: Any,
    unlockRequest: String? = null,
    onUnlockRequestHandled: () -> Unit = {},
    statsRequest: String? = null,
    onStatsRequestHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = start) {
        composable<Onboarding> {
            OnboardingScreen(
                onFinished = {
                    // Sin vuelta atrás a los primeros pasos.
                    nav.navigate(Home) { popUpTo<Onboarding> { inclusive = true } }
                },
            )
        }
        composable<Home> {
            HomeScreen(
                onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                onOpenStats = { month -> nav.navigate(StatsRoute(month?.toString())) { launchSingleTop = true } },
                onOpenUnlock = { nav.navigate(UnlockRoute()) { launchSingleTop = true } },
            )
        }
        composable<StatsRoute> { entry ->
            StatsScreen(initialMonth = entry.toRoute<StatsRoute>().month, onBack = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable<UnlockRoute> { entry ->
            UnlockScreen(
                preselected = entry.toRoute<UnlockRoute>().packageName,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
    }

    LaunchedEffect(unlockRequest) {
        val packageName = unlockRequest ?: return@LaunchedEffect
        onUnlockRequestHandled()
        // Durante los primeros pasos no se pide tiempo: aún no hay saldo.
        if (nav.currentDestination?.hasRoute<Onboarding>() == true) return@LaunchedEffect
        nav.navigate(UnlockRoute(packageName)) {
            popUpTo<Home>()
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
