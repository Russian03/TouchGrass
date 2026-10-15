package io.github.russian03.touchgrass

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.ui.navigation.AppNavHost
import io.github.russian03.touchgrass.ui.navigation.MainViewModel
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // La pantalla de carga se queda hasta saber si toca primeros pasos o Inicio, y al menos lo
        // que dura la animación del logo, para que no se corte.
        val shownAt = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.start.value == null || SystemClock.uptimeMillis() - shownAt < SPLASH_MIN_MS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la primera vez: al girar la pantalla no se vuelve a abrir.
        if (savedInstanceState == null) handle(intent)
        setContent {
            TouchGrassTheme {
                val start by viewModel.start.collectAsStateWithLifecycle()
                val homeRequest by viewModel.homeRequest.collectAsStateWithLifecycle()
                val statsRequest by viewModel.statsRequest.collectAsStateWithLifecycle()
                start?.let {
                    AppNavHost(
                        start = it,
                        homeRequest = homeRequest,
                        onHomeRequestHandled = viewModel::onHomeRequestHandled,
                        statsRequest = statsRequest,
                        onStatsRequestHandled = viewModel::onStatsRequestHandled,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) viewModel.requestHome()
        viewModel.openStats(intent.getStringExtra(EXTRA_OPEN_STATS))
    }

    companion object {
        /** Lo que dura la animación del logo en la pantalla de carga (`splash_logo_animated`). */
        private const val SPLASH_MIN_MS = 1_000L

        /** Desde la pantalla de bloqueo: abre Inicio, sea cual sea la pantalla en la que se quedó la app. */
        const val EXTRA_OPEN_HOME = "io.github.russian03.touchgrass.extra.OPEN_HOME"

        /** Mes (`2026-09`) cuyas estadísticas se abren, desde la notificación del resumen. */
        const val EXTRA_OPEN_STATS = "io.github.russian03.touchgrass.extra.OPEN_STATS"
    }
}
