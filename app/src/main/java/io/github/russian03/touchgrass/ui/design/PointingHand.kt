package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mano de línea con el índice estirado, como la de la referencia, pero con la muñeca cerrada.
// Rejilla de 38 × 42, con el dedo hacia arriba.
private const val GRID_W = 38f
private const val GRID_H = 42f
private const val OUTLINE =
    "M22,19 L22,12.5 A2.5,2.5 0 0 1 27,12.5 L27,19.5 C29.5,19.5 31.5,19.5 32.5,21 C33.2,22 33.2,23 33.2,24 " +
        "L33.2,38 Q33.2,42 29.5,42 L21.5,42 Q18,42 18,38 L18,35 C15,33 11.5,30 11.5,25.5 " +
        "C11.5,21.5 14.5,19 18,19 Z"
private const val THUMB =
    "M27.5,20.5 C25,21.5 24,24 23.5,26 C22.6,29 20.6,31 18.4,32.2 M23.4,26.4 C24.6,25.6 25.9,25.7 26.4,26.6"
private const val STROKE = 1.4f

private val Outline = PathParser().parsePathString(OUTLINE).toPath()
private val Thumb = PathParser().parsePathString(THUMB).toPath()

/**
 * Mano que señala hacia la derecha (el dibujo va girado 90°), en trazo de tinta y rellena de
 * [fill] para que no se transparente. Sirve para señalar un texto a su lado.
 */
@Composable
fun PointingHand(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fill: Color = MaterialTheme.colorScheme.background,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / GRID_H
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        rotate(90f) {
            translate((this.size.width - GRID_W * s) / 2, (this.size.height - GRID_H * s) / 2) {
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(Outline, fill)
                    drawPath(Outline, color, style = stroke)
                    drawPath(Thumb, color, style = stroke)
                }
            }
        }
    }
}

/**
 * Entrada de la mano: de invisible y sin tamaño a su tamaño, girando un poco con un rebote. Pasa
 * cada vez que entra en pantalla (p. ej. al cambiar el apartado que señala).
 */
@Composable
fun Modifier.handAppear(): Modifier {
    val inspection = LocalInspectionMode.current
    val p = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f)) }
    return graphicsLayer {
        val v = p.value
        scaleX = v.coerceAtLeast(0f)
        scaleY = v.coerceAtLeast(0f)
        alpha = v.coerceIn(0f, 1f)
        rotationZ = -25f * (1f - v)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEDE6D3)
@Composable
private fun PointingHandPreview() {
    PointingHand(size = 64.dp, fill = Color(0xFFEDE6D3))
}
