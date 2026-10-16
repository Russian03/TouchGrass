package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cómo está el personaje: cambia la boca y los ojos. */
enum class SproutMood { Calm, Curious, Happy, Joy, Worried, Sad }

// Medidas en el espacio del dibujo `sprout_body` (360 × 390), sacadas de los huecos del logo.
private const val BODY_W = 360f
private const val BODY_H = 390f

/** Hasta aquí llega la ramita; por debajo, el cuerpo. Se mece sobre su base. */
private const val TWIG_BOTTOM = 96f
private val TwigBase = Offset(183f, 100f)
private val LeftEye = Offset(108f, 235f)
private val RightEye = Offset(245f, 250f)
private const val EYE_RX = 15f
private const val EYE_RY = 16.5f
private val NoseTop = Offset(181f, 205f)
private val NoseLeft = Offset(166f, 252f)
private val NoseRight = Offset(186f, 251f)
private val MouthCentre = Offset(173f, 276f)
private const val MOUTH_W = 84f

/** Cuánto se mueven los ojos y la nariz al mirar a un lado (en el espacio del dibujo). */
private val LookReach = Offset(14f, 10f)

/** Forma de la cara para cada estado: sonrisa (−1 triste … 1), apertura de la boca, ojos felices y tamaño de ojos. */
private data class Face(val smile: Float, val open: Float, val happy: Float, val eyes: Float)

private fun SproutMood.face(): Face = when (this) {
    SproutMood.Calm -> Face(smile = 0.4f, open = 0f, happy = 0f, eyes = 1f)
    SproutMood.Curious -> Face(smile = 0f, open = 0.1f, happy = 0f, eyes = 1.15f)
    SproutMood.Happy -> Face(smile = 1f, open = 0.55f, happy = 0f, eyes = 1f)
    SproutMood.Joy -> Face(smile = 1f, open = 1f, happy = 1f, eyes = 1f)
    SproutMood.Worried -> Face(smile = -0.5f, open = 0f, happy = 0f, eyes = 1.12f)
    SproutMood.Sad -> Face(smile = -1f, open = 0.25f, happy = 0f, eyes = 0.9f)
}

/**
 * El logo hecho personaje: la silueta lima con la ramita y la cara recortada (del color de lo que
 * hay detrás, [faceColor]). Parpadea solo, respira, mece la ramita y mira a los lados de vez en
 * cuando; con [look] (de −1 a 1 en cada eje) mira hacia ahí. Cada vez que cambia [bounce] da un
 * saltito y la ramita se sacude. [mood] cambia la boca y los ojos con muelle.
 */
@Composable
fun Sprout(
    mood: SproutMood,
    modifier: Modifier = Modifier,
    look: Offset? = null,
    bounce: Int = 0,
    faceColor: Color = Ink,
) {
    val inspection = LocalInspectionMode.current
    val body = painterResource(R.drawable.sprout_body)
    val target = mood.face()
    val smile = remember { Animatable(target.smile) }
    val open = remember { Animatable(target.open) }
    val happy = remember { Animatable(target.happy) }
    val eyes = remember { Animatable(target.eyes) }
    LaunchedEffect(mood) {
        val face = mood.face()
        val springy = spring<Float>(dampingRatio = 0.55f, stiffness = 300f)
        launch { smile.animateTo(face.smile, springy) }
        launch { open.animateTo(face.open, springy) }
        launch { happy.animateTo(face.happy, tween(220)) }
        launch { eyes.animateTo(face.eyes, springy) }
    }

    // Parpadeo: cada 2,5–5 s, y a veces dos seguidos.
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2_500, 5_000))
            repeat(if (Random.nextInt(4) == 0) 2 else 1) {
                blink.animateTo(0f, tween(70))
                blink.animateTo(1f, tween(110))
                delay(90)
            }
        }
    }

    // Mirada: hacia [look] o, sin nada que mirar, a un lado y a otro de vez en cuando.
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    LaunchedEffect(look) {
        val springy = spring<Float>(dampingRatio = 0.7f, stiffness = 250f)
        if (look != null) {
            launch { lookX.animateTo(look.x.coerceIn(-1f, 1f), springy) }
            lookY.animateTo(look.y.coerceIn(-1f, 1f), springy)
            return@LaunchedEffect
        }
        if (inspection) return@LaunchedEffect
        val glances = listOf(Offset(-0.8f, 0f), Offset(0f, 0f), Offset(0.8f, -0.2f), Offset(0f, 0f))
        var i = 0
        while (true) {
            delay(Random.nextLong(1_800, 3_600))
            val g = glances[i++ % glances.size]
            launch { lookX.animateTo(g.x, springy) }
            lookY.animateTo(g.y, springy)
        }
    }

    // Saltito con cada [bounce]; no en la primera vez.
    val hop = remember { Animatable(0f) }
    val first = remember { intArrayOf(bounce) }
    LaunchedEffect(bounce) {
        if (bounce == first[0] || inspection) return@LaunchedEffect
        hop.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
        hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 380f))
    }

    val idle = rememberInfiniteTransition(label = "sprout")
    val breath by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000), RepeatMode.Restart),
        label = "breath",
    )
    val sway by idle.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1_900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )

    Canvas(modifier.aspectRatio(BODY_W / BODY_H)) {
        val s = size.width / BODY_W
        val h = hop.value
        val b = sin(2 * PI * breath).toFloat()
        val squashY = 1f + 0.02f * b - 0.06f * h
        val squashX = 1f - 0.01f * b + 0.04f * h
        withTransform({
            translate(top = -h * 0.08f * size.height)
            scale(squashX, squashY, pivot = Offset(size.width / 2, size.height))
            scale(s, s, pivot = Offset.Zero)
        }) {
            val full = Size(BODY_W, BODY_H)
            // La ramita, aparte, girando sobre su base; sacudida al saltar.
            rotate(sway + 18f * h * sin(PI * h).toFloat(), pivot = TwigBase) {
                clipRect(right = BODY_W, bottom = TWIG_BOTTOM) { with(body) { draw(full) } }
            }
            // Los recortes van en el espacio del dibujo, no en píxeles: por eso los bordes son explícitos.
            clipRect(top = TWIG_BOTTOM, right = BODY_W, bottom = BODY_H) { with(body) { draw(full) } }
            drawFace(
                faceColor = faceColor,
                look = Offset(lookX.value * LookReach.x, lookY.value * LookReach.y),
                blink = blink.value,
                smile = smile.value,
                open = open.value.coerceAtLeast(0f),
                happy = happy.value.coerceIn(0f, 1f),
                eyeScale = eyes.value,
            )
        }
    }
}

private fun DrawScope.drawFace(
    faceColor: Color,
    look: Offset,
    blink: Float,
    smile: Float,
    open: Float,
    happy: Float,
    eyeScale: Float,
) {
    // Ojos: óvalos que se cierran al parpadear; con ojos felices, arcos «^».
    listOf(LeftEye, RightEye).forEach { centre ->
        val c = centre + look
        val rx = EYE_RX * eyeScale
        val ry = EYE_RY * eyeScale * blink.coerceAtLeast(0.08f)
        if (happy < 1f) {
            drawOval(
                faceColor.copy(alpha = 1f - happy),
                topLeft = Offset(c.x - rx, c.y - ry),
                size = Size(rx * 2, ry * 2),
            )
        }
        if (happy > 0f) {
            val arc =
                Path().apply {
                    moveTo(c.x - rx, c.y + 4f)
                    quadraticTo(c.x, c.y - ry * 1.6f, c.x + rx, c.y + 4f)
                }
            drawPath(arc, faceColor.copy(alpha = happy), style = Stroke(width = 8f, cap = StrokeCap.Round))
        }
    }
    // Nariz: el triángulo redondeado del logo, que sigue un poco la mirada.
    val n = look * 0.5f
    val nose =
        Path().apply {
            moveTo(NoseTop.x + n.x, NoseTop.y + n.y)
            lineTo(NoseRight.x + n.x, NoseRight.y + n.y)
            lineTo(NoseLeft.x + n.x, NoseLeft.y + n.y)
            close()
        }
    drawPath(nose, faceColor)
    drawPath(nose, faceColor, style = Stroke(width = 6f, join = StrokeJoin.Round))
    // Boca: de triste a sonrisa abierta como la del logo (la «D» de lado).
    drawPath(mouthPath(smile, open, look * 0.3f), faceColor)
}

/** Boca con las comisuras arriba ([smile] > 0) o abajo (< 0) y más o menos abierta ([open]). */
private fun mouthPath(smile: Float, open: Float, shift: Offset): Path {
    val c = MouthCentre + shift
    val half = MOUTH_W / 2 * (0.62f + 0.38f * open.coerceAtMost(1f))
    val corners = c.y - 14f * smile
    val top = c.y + 8f * smile
    // Grosor: una línea si está cerrada; abierta, baja hasta la «D» del logo.
    val depth = 7f + 46f * open * if (smile >= 0f) 1f else 0.4f
    return Path().apply {
        moveTo(c.x - half, corners)
        quadraticTo(c.x, top, c.x + half, corners)
        quadraticTo(c.x, top + depth * 2f, c.x - half, corners)
        close()
    }
}

/** Medidas del personaje dentro de su caja, para colocarlo en el bulto de la tarjeta. */
object SproutGeometry {
    /** Alto respecto al ancho. */
    const val ASPECT = BODY_H / BODY_W

    /** Centro del cuerpo (sin la ramita), en fracción del alto. */
    const val BODY_CENTRE_Y = 240f / BODY_H

    /** Borde de abajo del cuerpo, en fracción del alto. */
    const val BODY_BOTTOM_Y = 376f / BODY_H
}

@Preview(showBackground = true, backgroundColor = 0xFF141513)
@Composable
private fun SproutMoodsPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.background(Color(0xFF141513)).padding(8.dp),
    ) {
        SproutMood.entries.forEach { Sprout(it, Modifier.width(80.dp), faceColor = Color(0xFF141513)) }
    }
}
