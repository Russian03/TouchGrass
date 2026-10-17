package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.FlameOrange
import io.github.russian03.touchgrass.ui.theme.FlameYellow
import io.github.russian03.touchgrass.ui.theme.Ink

// El dibujo va en una caja de 100 × 130, con margen para el borde.
private const val FLAME_W = 100f
private const val FLAME_H = 130f
private const val MARGIN = 6f

/**
 * El fuego de la racha, dibujado en código: una llama naranja con borde crema y otra amarilla
 * dentro. La punta se mece, la llama respira y la de dentro va a otro ritmo, para que parpadee
 * sin repetirse igual. En las vistas previas sale quieta.
 */
@Composable
fun AnimatedFlame(modifier: Modifier = Modifier, outline: Color = Cream) {
    val still = LocalInspectionMode.current
    val flicker = rememberInfiniteTransition(label = "flame")
    val sway by flicker.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val breath by flicker.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val inner by flicker.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(530, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inner",
    )
    Canvas(modifier.aspectRatio((FLAME_W + 2 * MARGIN) / (FLAME_H + 2 * MARGIN))) {
        val s = size.width / (FLAME_W + 2 * MARGIN)
        val sw = if (still) 0f else sway
        val br = if (still) 0.5f else breath
        val inn = if (still) 0f else inner
        scale(s, s, pivot = Offset.Zero) {
            translate(MARGIN, MARGIN) {
                // La llama se estira desde abajo al respirar.
                scale(1f + 0.02f * (br - 0.5f), 0.96f + 0.06f * br, pivot = Offset(FLAME_W / 2, FLAME_H)) {
                    val outer = outerFlame(tip = 6f * sw, notch = 3f * sw)
                    drawPath(outer, outline, style = Stroke(width = 10f, join = StrokeJoin.Round))
                    drawPath(outer, FlameOrange)
                    scale(0.92f + 0.1f * br, 0.9f + 0.12f * br, pivot = Offset(FLAME_W / 2, FLAME_H)) {
                        drawPath(innerFlame(tip = 5f * inn), FlameYellow)
                    }
                }
            }
        }
    }
}

/** La llama de fuera, con una lengua a la izquierda; [tip] y [notch] mueven la punta y la lengua. */
private fun outerFlame(tip: Float, notch: Float) = Path().apply {
    moveTo(50f + tip, 0f)
    cubicTo(60f + tip, 20f, 90f, 38f, 90f, 82f)
    cubicTo(90f, 112f, 70f, 130f, 50f, 130f)
    cubicTo(28f, 130f, 10f, 114f, 10f, 86f)
    cubicTo(10f, 66f, 20f, 52f, 30f + notch, 40f)
    cubicTo(30f + notch, 54f, 35f, 62f, 43f, 66f)
    cubicTo(38f, 42f, 42f + tip / 2, 18f, 50f + tip, 0f)
    close()
}

/** La llama de dentro, una gota con la punta que se mece con [tip]. */
private fun innerFlame(tip: Float) = Path().apply {
    moveTo(51f + tip, 60f)
    cubicTo(58f + tip / 2, 74f, 71f, 86f, 71f, 104f)
    cubicTo(71f, 119f, 61f, 128f, 50f, 128f)
    cubicTo(39f, 128f, 29f, 120f, 29f, 106f)
    cubicTo(29f, 90f, 42f + tip / 2, 80f, 51f + tip, 60f)
    close()
}

@Preview
@Composable
private fun FlamePreview() {
    AnimatedFlame(Modifier.background(Ink).padding(12.dp).width(80.dp))
}
