package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Un destello: posición relativa (0..1), tamaño, forma y desfase de su parpadeo (0..1). */
data class Spark(val x: Float, val y: Float, val size: Dp, val kind: SparkKind, val phase: Float)

enum class SparkKind {
    /** Estrella de cuatro puntas de trazo fino. */
    Star,

    /** Rayitas que salen de un punto, como un brillo. */
    Burst,

    /** Cruz pequeña. */
    Cross,
}

/** Destellos repartidos alrededor de un elemento central, como en «12 Days planned». */
val AroundSparks =
    listOf(
        Spark(0.12f, 0.18f, 18.dp, SparkKind.Star, 0f),
        Spark(0.86f, 0.12f, 22.dp, SparkKind.Burst, 0.35f),
        Spark(0.92f, 0.62f, 16.dp, SparkKind.Star, 0.7f),
        Spark(0.06f, 0.72f, 14.dp, SparkKind.Cross, 0.5f),
        Spark(0.30f, 0.04f, 10.dp, SparkKind.Cross, 0.85f),
        Spark(0.72f, 0.92f, 12.dp, SparkKind.Star, 0.2f),
    )

/**
 * Destellos que parpadean (opacidad 0,3↔1) y giran ±15° cada uno a su ritmo. [boost] (≥ 1)
 * los agranda un momento, p. ej. al cambiar el número que rodean.
 */
@Composable
fun Sparkles(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    sparks: List<Spark> = AroundSparks,
    boost: Float = 1f,
    periodMs: Int = 1_800,
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else color
    val t by rememberInfiniteTransition(label = "sparkles").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkleTime",
    )
    Canvas(modifier) {
        sparks.forEach { spark ->
            val wave = sin(2 * PI * (t + spark.phase)).toFloat()
            val alpha = 0.65f + 0.35f * wave
            val radius = spark.size.toPx() / 2 * boost * (0.9f + 0.1f * wave)
            val center = Offset(spark.x * size.width, spark.y * size.height)
            rotate(degrees = 15f * wave, pivot = center) {
                drawSpark(spark.kind, center, radius, tint.copy(alpha = tint.alpha * alpha))
            }
        }
    }
}

/** Dibuja un destello de radio [radius] centrado en [center]. */
fun DrawScope.drawSpark(kind: SparkKind, center: Offset, radius: Float, color: Color) {
    val stroke = (radius * 0.14f).coerceAtLeast(1.5.dp.toPx())
    when (kind) {
        SparkKind.Star -> {
            // Cuatro puntas cóncavas: cada lado es una curva que pasa cerca del centro.
            val pull = radius * 0.12f
            val path =
                Path().apply {
                    moveTo(center.x, center.y - radius)
                    quadraticTo(center.x + pull, center.y - pull, center.x + radius, center.y)
                    quadraticTo(center.x + pull, center.y + pull, center.x, center.y + radius)
                    quadraticTo(center.x - pull, center.y + pull, center.x - radius, center.y)
                    quadraticTo(center.x - pull, center.y - pull, center.x, center.y - radius)
                    close()
                }
            drawPath(path, color, style = Stroke(width = stroke))
        }
        SparkKind.Burst ->
            repeat(5) { i ->
                // Abanico de rayas hacia arriba, separadas del centro.
                val angle = PI * (1.15 + 0.175 * i)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    color,
                    Offset(center.x + dx * radius * 0.35f, center.y + dy * radius * 0.35f),
                    Offset(center.x + dx * radius, center.y + dy * radius),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        SparkKind.Cross -> {
            val r = radius * 0.7f
            drawLine(
                color,
                Offset(center.x - r, center.y - r),
                Offset(center.x + r, center.y + r),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(center.x - r, center.y + r),
                Offset(center.x + r, center.y - r),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}
