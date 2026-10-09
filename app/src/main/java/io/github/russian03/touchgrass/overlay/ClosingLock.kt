package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.ui.theme.Grass
import io.github.russian03.touchgrass.ui.theme.GrassGlow
import kotlinx.coroutines.delay

/**
 * Candado que aparece abierto y se cierra: el arco baja con un pequeño rebote y el
 * cuerpo "encaja" con un leve aplastamiento. Detrás, el mismo halo verde que respira
 * en la pantalla de bloqueo.
 */
@Composable
fun ClosingLock(modifier: Modifier = Modifier) {
    val lift = remember { Animatable(1f) } // 1 = arco levantado (abierto), 0 = cerrado
    val squash = remember { Animatable(1f) }
    val keyhole = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(350)
        lift.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) {
        // El "clic" coincide con el momento en que el arco toca el cuerpo.
        delay(520)
        squash.animateTo(0.9f, tween(90))
        squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    LaunchedEffect(Unit) {
        delay(620)
        keyhole.animateTo(1f, tween(300))
    }

    val breath by rememberInfiniteTransition(label = "lockGlow").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lockGlowScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(150.dp)) {
        Box(
            Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                    alpha = 1.6f - breath
                }
                .background(GrassGlow.copy(alpha = 0.22f), CircleShape),
        )
        Surface(
            color = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(104.dp).shadow(4.dp, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = w * 0.11f
                    val bodyTop = h * 0.44f
                    val shackleWidth = w * 0.52f
                    val shackleLeft = (w - shackleWidth) / 2
                    val shackleTop = h * 0.04f - lift.value * h * 0.2f
                    val legBottom = bodyTop + h * 0.08f - lift.value * h * 0.2f

                    val shackle =
                        Path().apply {
                            moveTo(shackleLeft, legBottom)
                            lineTo(shackleLeft, shackleTop + shackleWidth / 2)
                            arcTo(
                                Rect(Offset(shackleLeft, shackleTop), Size(shackleWidth, shackleWidth)),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false,
                            )
                            lineTo(shackleLeft + shackleWidth, legBottom)
                        }
                    drawPath(shackle, Grass, style = Stroke(width = stroke, cap = StrokeCap.Round))

                    scale(scaleX = 1f / squash.value, scaleY = squash.value, pivot = Offset(w / 2, h)) {
                        drawRoundRect(
                            color = Grass,
                            topLeft = Offset(w * 0.12f, bodyTop),
                            size = Size(w * 0.76f, h - bodyTop),
                            cornerRadius = CornerRadius(w * 0.14f),
                        )
                        val holeCenter = Offset(w / 2, bodyTop + (h - bodyTop) * 0.45f)
                        val holeColor = Color.White.copy(alpha = keyhole.value)
                        drawCircle(holeColor, radius = w * 0.075f, center = holeCenter)
                        drawRoundRect(
                            color = holeColor,
                            topLeft = Offset(holeCenter.x - w * 0.03f, holeCenter.y),
                            size = Size(w * 0.06f, h * 0.15f),
                            cornerRadius = CornerRadius(w * 0.03f),
                        )
                    }
                }
            }
        }
    }
}
