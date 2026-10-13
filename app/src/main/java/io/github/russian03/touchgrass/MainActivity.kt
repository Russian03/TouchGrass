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
        if (savedInstanceState == null) viewModel.requestUnlock(intent.getStringExtra(EXTRA_UNLOCK_PACKAGE))
        setContent {
            TouchGrassTheme {
                val start by viewModel.start.collectAsStateWithLifecycle()
                val unlockRequest by viewModel.unlockRequest.collectAsStateWithLifecycle()
                start?.let { AppNavHost(it, unlockRequest, viewModel::onUnlockRequestHandled) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.requestUnlock(intent.getStringExtra(EXTRA_UNLOCK_PACKAGE))
    }

    companion object {
        /**
         * App para la que se pide tiempo desde la pantalla de bloqueo. Solo elige qué app sale
         * marcada: la pantalla de pedir tiempo ignora un paquete que no tenga reglas.
         */
        const val EXTRA_UNLOCK_PACKAGE = "io.github.russian03.touchgrass.extra.UNLOCK_PACKAGE"
    }
}
