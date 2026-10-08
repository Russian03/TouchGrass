package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Blade(
    val x: Float, // 0..1 del ancho
    val height: Float, // 0..1 de la altura del césped
    val width: Float, // px relativos
    val phase: Float,
    val delay: Float, // 0..1, cuándo empieza a crecer
    val shade: Float, // 0 = oscuro, 1 = claro
)

private val GrassDark = Color(0xFF1B5E20)
private val GrassLight = Color(0xFF8BC34A)

/**
 * Césped dibujado con briznas que se mecen. Si [grow] es true, las briznas crecen
 * desde el suelo (la primera vez en la sesión); si no, aparecen ya crecidas.
 */
@Composable
fun GrassScene(grow: Boolean, modifier: Modifier = Modifier) {
    val blades = remember { generateBlades(seed = 7, count = 90) }

    val growth = remember { Animatable(if (grow) 0f else 1f) }
    LaunchedEffect(grow) {
        if (grow) growth.animateTo(1f, tween(durationMillis = 2_200, easing = FastOutSlowInEasing))
    }

    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "swayPhase",
    )

    Canvas(modifier) {
        blades.forEach { drawBlade(it, growth.value, sway) }
    }
}

private fun DrawScope.drawBlade(blade: Blade, growth: Float, sway: Float) {
    // Cada brizna crece con su propio retraso para que el césped "brote" de forma natural.
    val local = ((growth - blade.delay * 0.5f) / 0.5f).coerceIn(0f, 1f)
    if (local <= 0f) return

    val baseX = blade.x * size.width
    val baseY = size.height
    val h = blade.height * size.height * local
    val bend = sin(sway + blade.phase) * h * 0.12f
    val w = blade.width * size.width / 100f

    val path =
        Path().apply {
            moveTo(baseX - w, baseY)
            quadraticTo(baseX - w * 0.3f + bend * 0.4f, baseY - h * 0.5f, baseX + bend, baseY - h)
            quadraticTo(baseX + w * 0.3f + bend * 0.4f, baseY - h * 0.5f, baseX + w, baseY)
            close()
        }
    drawPath(path, lerp(GrassDark, GrassLight, blade.shade))
}

private fun generateBlades(seed: Int, count: Int): List<Blade> {
    val random = Random(seed)
    return List(count) {
        Blade(
            x = random.nextFloat() * 1.04f - 0.02f,
            height = 0.45f + random.nextFloat() * 0.55f,
            width = 1.2f + random.nextFloat() * 1.6f,
            phase = random.nextFloat() * (2 * PI).toFloat(),
            delay = random.nextFloat(),
            shade = random.nextFloat(),
        )
    }.sortedBy { it.shade } // las oscuras detrás, las claras delante
}
