package io.github.russian03.touchgrass.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.design.MorphingShape
import io.github.russian03.touchgrass.ui.design.SparkKind
import io.github.russian03.touchgrass.ui.design.Sparkles
import io.github.russian03.touchgrass.ui.design.drawSpark
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Una brizna: posición de la base (0..1), alto (0..1 del lienzo), cuánto se inclina la punta y ancho. */
private data class Blade(val x: Float, val height: Float, val lean: Float, val width: Float)

private val Blades =
    listOf(
        Blade(0.22f, 0.42f, -0.10f, 0.07f),
        Blade(0.32f, 0.66f, -0.06f, 0.09f),
        Blade(0.44f, 0.86f, 0.02f, 0.10f),
        Blade(0.55f, 0.72f, 0.08f, 0.09f),
        Blade(0.66f, 0.54f, 0.12f, 0.08f),
        Blade(0.76f, 0.36f, 0.14f, 0.06f),
    )

/** Retraso entre briznas al crecer. */
private const val BLADE_STEP_MS = 90L

/**
 * Briznas de hierba que crecen desde el suelo de una en una (muelle con rebote) y luego se mecen
 * ±3° cada una a su ritmo. El relleno lima va desplazado de la línea, como mal impreso, y la
 * línea solo marca un borde de cada brizna: es una guía, no un contorno.
 */
@Composable
fun GrassSprouts(visible: Boolean, modifier: Modifier = Modifier, ink: Color = MaterialTheme.colorScheme.onBackground) {
    val inspection = LocalInspectionMode.current
    val grow = remember { Blades.map { Animatable(if (inspection) 1f else 0f) } }
    LaunchedEffect(visible) {
        if (!visible || inspection) return@LaunchedEffect
        grow.forEachIndexed { i, anim ->
            launch {
                anim.snapTo(0f)
                delay(i * BLADE_STEP_MS)
                anim.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
        }
    }
    val t by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
        label = "swayTime",
    )
    Box(modifier) {
        Sparkles(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val ground = size.height * 0.92f
            val stroke = 2.dp.toPx()
            val misprint = Offset(6.dp.toPx(), 4.dp.toPx())
            Blades.forEachIndexed { i, blade ->
                val g = grow[i].value
                if (g <= 0f) return@forEachIndexed
                val base = Offset(blade.x * size.width, ground)
                val w = blade.width * size.width
                val h = blade.height * size.height
                val tip = Offset(base.x + blade.lean * size.width, ground - h)
                val sway = 3f * sin(2 * PI * (t + i * 0.13)).toFloat()
                val fill =
                    Path().apply {
                        moveTo(base.x - w / 2, base.y)
                        quadraticTo(base.x - w / 2, base.y - h * 0.6f, tip.x, tip.y)
                        quadraticTo(base.x + w / 2, base.y - h * 0.5f, base.x + w / 2, base.y)
                        close()
                    }
                val guide =
                    Path().apply {
                        moveTo(base.x - w / 2, base.y)
                        quadraticTo(base.x - w / 2, base.y - h * 0.6f, tip.x, tip.y)
                        quadraticTo(base.x + w / 3, base.y - h * 0.55f, base.x + w / 3, base.y - h * 0.25f)
                    }
                rotate(sway * g, pivot = base) {
                    scale(scaleX = 1f, scaleY = g, pivot = base) {
                        translate(misprint.x, misprint.y) { drawPath(fill, Lime) }
                        drawPath(guide, ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                }
            }
            // El suelo: una línea que no llega a cerrarse.
            drawLine(
                ink,
                Offset(size.width * 0.12f, ground),
                Offset(size.width * 0.58f, ground),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(size.width * 0.64f, ground),
                Offset(size.width * 0.86f, ground),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/**
 * Forma lima que se transforma en cada punto de «Cómo funciona» (galleta, trébol, sol) y gira
 * 45°, con el número del punto en el centro.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HowShape(active: Int, modifier: Modifier = Modifier) {
    val polygons = remember { listOf(MaterialShapes.Cookie4Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny) }
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(modifier, contentAlignment = Alignment.Center) {
        Sparkles(Modifier.fillMaxSize())
        MorphingShape(
            polygon = polygons[active % polygons.size],
            color = Lime,
            outline = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(200.dp),
        ) {
            AnimatedContent(
                targetState = active,
                transitionSpec = {
                    (fadeIn(enter) + scaleIn(initialScale = 0.6f)) togetherWith fadeOut(exit)
                },
                label = "howNumber",
            ) { index ->
                Text((index + 1).toString(), style = MaterialTheme.typography.displayLarge, color = Ink)
            }
        }
    }
}

/**
 * Forma lima con la inicial del nombre en el centro. Cada letra nueva entra con un salto; al
 * escribir la primera, la galleta se transforma en sol. Sin nombre, dice «¡Hola!».
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NameShape(initial: Char?, modifier: Modifier = Modifier) {
    val enter = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(modifier, contentAlignment = Alignment.Center) {
        Sparkles(Modifier.fillMaxSize())
        MorphingShape(
            polygon = if (initial == null) MaterialShapes.Cookie9Sided else MaterialShapes.Sunny,
            color = Lime,
            outline = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(200.dp),
        ) {
            AnimatedContent(
                targetState = initial,
                transitionSpec = { scaleIn(enter, initialScale = 0.4f) togetherWith fadeOut(exit) },
                label = "nameInitial",
            ) { letter ->
                Text(
                    text = letter?.toString() ?: stringResource(R.string.home_greeting_anonymous).replace("*", ""),
                    style = if (letter ==
                        null
                    ) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displayLarge
                    },
                    color = Ink,
                )
            }
        }
    }
}

/** Galleta lima que respira y gira despacio, con un candado de línea encima. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BreathingLock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "breathSpin",
    )
    val ink = MaterialTheme.colorScheme.onBackground
    Box(modifier, contentAlignment = Alignment.Center) {
        Sparkles(Modifier.fillMaxSize())
        MorphingShape(
            polygon = MaterialShapes.Cookie9Sided,
            color = Lime,
            outline = ink,
            modifier =
            Modifier.size(190.dp).graphicsLayer {
                scaleX = breath
                scaleY = breath
                rotationZ = spin
            },
        )
        Canvas(Modifier.size(72.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            // Arco abierto y cuerpo que no llegan a tocarse: líneas de guía.
            drawArc(Ink, 180f, 180f, false, Offset(w * 0.25f, h * 0.08f), Size(w * 0.5f, h * 0.5f), style = stroke)
            drawLine(Ink, Offset(w * 0.25f, h * 0.33f), Offset(w * 0.25f, h * 0.44f), stroke.width, StrokeCap.Round)
            drawRoundRect(
                Ink,
                Offset(w * 0.14f, h * 0.48f),
                Size(w * 0.72f, h * 0.46f),
                CornerRadius(w * 0.1f),
                style = stroke,
            )
            drawLine(Ink, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.5f, h * 0.76f), stroke.width, StrokeCap.Round)
        }
    }
}

/**
 * Interruptor grande dibujado en código. Al activarse la protección, la bola se desliza con muelle,
 * se transforma de círculo en galleta, se dibuja un check, salta un estallido de destellos y el
 * móvil vibra una vez. Tocarlo lleva a Accesibilidad.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtectionSwitch(on: Boolean, alert: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val inspection = LocalInspectionMode.current
    val haptics = LocalHapticFeedback.current
    val knobOffset by animateDpAsState(
        targetValue = if (on) 88.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    val track by animateColorAsState(if (on) Lime else Color.Transparent, label = "track")
    val check = remember { Animatable(if (on) 1f else 0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (inspection) return@LaunchedEffect
        if (on) {
            if (check.value < 1f) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(900))
                }
            }
            check.animateTo(1f, tween(450, delayMillis = 200))
        } else {
            check.snapTo(0f)
        }
    }

    Box(modifier.size(260.dp, 200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = burst.value
            if (p <= 0f || p >= 1f) return@Canvas
            repeat(8) { i ->
                val angle = 2 * PI * i / 8
                val r = size.minDimension * (0.25f + 0.3f * p)
                val c = Offset(center.x + cos(angle).toFloat() * r * 1.3f, center.y + sin(angle).toFloat() * r)
                drawSpark(
                    if (i % 2 ==
                        0
                    ) {
                        SparkKind.Star
                    } else {
                        SparkKind.Cross
                    },
                    c,
                    10.dp.toPx(),
                    ink.copy(alpha = 1f - p),
                )
            }
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = track,
            border = BorderStroke(2.dp, if (alert && !on) MaterialTheme.colorScheme.error else ink),
            modifier = Modifier.size(184.dp, 96.dp),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                MorphingShape(
                    polygon = if (on) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                    color = if (on) Ink else ink.copy(alpha = 0.85f),
                    modifier = Modifier.padding(10.dp).offset { IntOffset(knobOffset.roundToPx(), 0) }.size(76.dp),
                ) {
                    Canvas(Modifier.size(34.dp)) {
                        val c = check.value
                        if (c <= 0f) return@Canvas
                        val a = Offset(size.width * 0.12f, size.height * 0.52f)
                        val b = Offset(size.width * 0.4f, size.height * 0.8f)
                        val d = Offset(size.width * 0.9f, size.height * 0.2f)
                        val stroke = 4.dp.toPx()
                        val first = (c / 0.4f).coerceAtMost(1f)
                        drawLine(Cream, a, a + (b - a) * first, stroke, StrokeCap.Round)
                        if (c > 0.4f) {
                            val second = ((c - 0.4f) / 0.6f).coerceAtMost(1f)
                            drawLine(Cream, b, b + (d - b) * second, stroke, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}
