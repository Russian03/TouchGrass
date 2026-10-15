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
        // La pantalla de carga se queda hasta saber si toca primeros pasos o Inicio y, después, hasta
        // que acabe el ciclo de la animación en curso: normalmente uno; si la app tarda, varios enteros.
        val shownAt = SystemClock.uptimeMillis()
        var readyAt = -1L
        installSplashScreen().setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - shownAt
            if (viewModel.start.value == null) return@setKeepOnScreenCondition true
            if (readyAt < 0) readyAt = elapsed
            val cycles = maxOf(1L, (readyAt + SPLASH_CYCLE_MS - 1) / SPLASH_CYCLE_MS)
            elapsed < cycles * SPLASH_CYCLE_MS
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
        /** Lo que dura un ciclo de la animación de la pantalla de carga (`splash_logo_animated`). */
        private const val SPLASH_CYCLE_MS = 3_000L

        /** Desde la pantalla de bloqueo: abre Inicio, sea cual sea la pantalla en la que se quedó la app. */
        const val EXTRA_OPEN_HOME = "io.github.russian03.touchgrass.extra.OPEN_HOME"

        /** Mes (`2026-09`) cuyas estadísticas se abren, desde la notificación del resumen. */
        const val EXTRA_OPEN_STATS = "io.github.russian03.touchgrass.extra.OPEN_STATS"
    }
}
