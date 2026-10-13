package io.github.russian03.touchgrass

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
        setContent {
            TouchGrassTheme {
                val start by viewModel.start.collectAsStateWithLifecycle()
                start?.let { AppNavHost(start = it) }
            }
        }
    }
}
