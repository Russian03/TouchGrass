package io.github.russian03.touchgrass

import android.content.Intent
import android.os.Bundle
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
        // La pantalla de carga se queda hasta saber si toca primeros pasos o Inicio.
        installSplashScreen().setKeepOnScreenCondition { viewModel.start.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la primera vez: al girar la pantalla no se vuelve a abrir.
        if (savedInstanceState == null) handle(intent)
        setContent {
            TouchGrassTheme {
                val start by viewModel.start.collectAsStateWithLifecycle()
                val unlockRequest by viewModel.unlockRequest.collectAsStateWithLifecycle()
                val statsRequest by viewModel.statsRequest.collectAsStateWithLifecycle()
                start?.let {
                    AppNavHost(
                        start = it,
                        unlockRequest = unlockRequest,
                        onUnlockRequestHandled = viewModel::onUnlockRequestHandled,
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
        viewModel.requestUnlock(intent.getStringExtra(EXTRA_UNLOCK_PACKAGE))
        viewModel.openStats(intent.getStringExtra(EXTRA_OPEN_STATS))
    }

    companion object {
        /**
         * App para la que se pide tiempo desde la pantalla de bloqueo. Solo elige qué app sale
         * marcada: la pantalla de pedir tiempo ignora un paquete que no tenga reglas.
         */
        const val EXTRA_UNLOCK_PACKAGE = "io.github.russian03.touchgrass.extra.UNLOCK_PACKAGE"

        /** Mes (`2026-09`) cuyas estadísticas se abren, desde la notificación del resumen. */
        const val EXTRA_OPEN_STATS = "io.github.russian03.touchgrass.extra.OPEN_STATS"
    }
}
