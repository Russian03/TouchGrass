package io.github.russian03.touchgrass

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.ui.design.LocalSplashGone
import io.github.russian03.touchgrass.ui.navigation.AppNavHost
import io.github.russian03.touchgrass.ui.navigation.MainViewModel
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Pasa a `true` cuando se va la pantalla de carga. */
    private val splashGone = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // La pantalla de carga se queda hasta saber si toca primeros pasos o Inicio y, después, hasta
        // que acabe el ciclo de la animación en curso: normalmente uno; si la app tarda, varios enteros.
        // Al recrearse (p. ej. al girar) no sale la pantalla de carga.
        if (savedInstanceState != null) splashGone.value = true
        val shownAt = SystemClock.uptimeMillis()
        var readyAt = -1L
        installSplashScreen().setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - shownAt
            if (viewModel.start.value == null) return@setKeepOnScreenCondition true
            if (readyAt < 0) readyAt = elapsed
            val cycles = maxOf(1L, (readyAt + SPLASH_CYCLE_MS - 1) / SPLASH_CYCLE_MS)
            val keep = elapsed < cycles * SPLASH_CYCLE_MS
            if (!keep) splashGone.value = true
            keep
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo la primera vez: al girar la pantalla no se vuelve a abrir.
        if (savedInstanceState == null) handle(intent)
        setContent {
            TouchGrassTheme {
                CompositionLocalProvider(LocalSplashGone provides splashGone) {
                    val start by viewModel.start.collectAsStateWithLifecycle()
                    val homeRequest by viewModel.homeRequest.collectAsStateWithLifecycle()
                    start?.let {
                        AppNavHost(
                            start = it,
                            homeRequest = homeRequest,
                            onHomeRequestHandled = viewModel::onHomeRequestHandled,
                        )
                    }
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
    }

    companion object {
        /** Lo que dura un ciclo de la animación de la pantalla de carga (`splash_logo_animated`). */
        private const val SPLASH_CYCLE_MS = 4_200L

        /** Desde la pantalla de bloqueo: abre Inicio, sea cual sea la pantalla en la que se quedó la app. */
        const val EXTRA_OPEN_HOME = "io.github.russian03.touchgrass.extra.OPEN_HOME"
    }
}
