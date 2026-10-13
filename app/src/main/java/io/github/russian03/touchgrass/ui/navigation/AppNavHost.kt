package io.github.russian03.touchgrass.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.russian03.touchgrass.ui.home.HomeScreen
import io.github.russian03.touchgrass.ui.onboarding.OnboardingScreen
import io.github.russian03.touchgrass.ui.settings.SettingsScreen

@Composable
fun AppNavHost(start: Any) {
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
            HomeScreen(onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
