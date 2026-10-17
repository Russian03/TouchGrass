package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Si ya se ha ido la pantalla de carga. Lo que se anima una sola vez al abrir (como el pop-up del
 * primer acceso del día) espera a que sea `true`, para que no pase escondido detrás. Fuera de
 * `MainActivity` (vistas previas) siempre lo es.
 */
val LocalSplashGone = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/** Retraso entre elementos que entran en cascada. */
const val STAGGER_STEP_MS = 60L

/**
 * Entrada en cascada: el elemento número [index] aparece [stepMs] ms después del anterior, con
 * fundido, subiendo [offsetY] y creciendo de 0,96 a 1 con el muelle del tema. Mientras
 * [visible] es `false` se queda escondido (p. ej. una página del asistente que aún no se ve).
 * Con [enabled] en falso (al entrar ya pintado) no anima.
 */
@Composable
fun Modifier.staggerIn(
    index: Int,
    visible: Boolean = true,
    stepMs: Long = STAGGER_STEP_MS,
    offsetY: Dp = 16.dp,
    fromScale: Float = 0.96f,
    enabled: Boolean = true,
): Modifier {
    // En las vistas previas no corre el tiempo: se pinta ya colocado. Sin [enabled], también.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection || !enabled) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(visible) {
        if (visible) {
            delay(index * stepMs)
            progress.animateTo(1f, spec)
        } else {
            progress.snapTo(0f)
        }
    }
    val dy = with(LocalDensity.current) { offsetY.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * dy
        val scale = fromScale + (1f - fromScale) * p
        scaleX = scale
        scaleY = scale
    }
}

/** Punto de estado con un halo que late, como un pulso tranquilo (1,6 s). */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "dotPulse",
    )
    Box(modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val scale = 1f + pulse * 1.2f
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - pulse) * 0.5f
                }
                .background(color, CircleShape),
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}
