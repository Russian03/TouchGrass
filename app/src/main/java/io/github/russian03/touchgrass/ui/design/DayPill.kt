package io.github.russian03.touchgrass.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayFace
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlin.math.PI
import kotlin.math.sin

/** Momento de un día de la semana respecto a hoy. */
enum class DayTime { Past, Today, Future }

/**
 * Las cinco caras de los días, de mejor a peor, con su dibujo. Para animarlas por partes también
 * llevan los ojos y la boca por separado, con la altura de los ojos ([eyesY]) y el centro de la
 * boca ([mouthX], [mouthY]) en fracción del dibujo.
 */
enum class SimpleFace(
    @DrawableRes val drawable: Int,
    @DrawableRes val eyes: Int,
    @DrawableRes val mouth: Int,
    val eyesY: Float,
    val mouthX: Float,
    val mouthY: Float,
) {
    /** Sin pedir tiempo o con racha de poco uso. */
    Great(R.drawable.face_genial, R.drawable.face_genial_eyes, R.drawable.face_genial_mouth, 0.39f, 0.5f, 0.638f),

    /** Hasta el 25 % del límite. */
    Good(R.drawable.face_bien, R.drawable.face_bien_eyes, R.drawable.face_bien_mouth, 0.457f, 0.498f, 0.64f),

    /** Hasta la mitad. */
    Serious(R.drawable.face_seria, R.drawable.face_seria_eyes, R.drawable.face_seria_mouth, 0.5f, 0.5f, 0.63f),

    /** Más de la mitad sin agotarlo. */
    SoSo(
        R.drawable.face_mediomal,
        R.drawable.face_mediomal_eyes,
        R.drawable.face_mediomal_mouth,
        0.479f,
        0.503f,
        0.632f,
    ),

    /** Saldo agotado o protección quitada. */
    Bad(R.drawable.face_mal, R.drawable.face_mal_eyes, R.drawable.face_mal_mouth, 0.426f, 0.5f, 0.611f),
}

/** Cara de un día, o `null` si ese día no se contaba. */
fun DayFace.simple(): SimpleFace? = when (this) {
    DayFace.Mythic, DayFace.Legend, DayFace.Cool, DayFace.Free -> SimpleFace.Great
    DayFace.Happy -> SimpleFace.Good
    DayFace.Calm -> SimpleFace.Serious
    DayFace.Neutral, DayFace.Tight -> SimpleFace.SoSo
    DayFace.Sad, DayFace.Disabled -> SimpleFace.Bad
    DayFace.Untracked -> null
}

/**
 * Cómo va la aparición de un día que aún no se había enseñado: primero salen los ojos, luego la
 * boca y al final la píldora se pone verde. Cada parte va de 0 a 1 (los muelles pueden pasarse un
 * poco). Con todo a 0 el día se ve «por descubrir»: borde y círculo vacío.
 */
@Stable
class DayReveal(eyes: Float = 0f, mouth: Float = 0f, green: Float = 0f) {
    val eyes = Animatable(eyes)
    val mouth = Animatable(mouth)
    val green = Animatable(green)
}

/**
 * Píldora vertical de «Mi semana», como «My Week»: un círculo arriba con la cara del día, el
 * número y su abreviatura. Los días pasados van en lima, hoy en tinta y los que vienen, solo con
 * borde y un punto en lugar del círculo.
 */
@Composable
fun DayPill(
    number: String,
    weekday: String,
    time: DayTime,
    face: SimpleFace?,
    description: String,
    modifier: Modifier = Modifier,
    reveal: DayReveal? = null,
) {
    // La tinta sigue al tema, para que hoy y los días que vienen se vean también en modo noche.
    val ink = MaterialTheme.colorScheme.onBackground
    if (reveal != null && time == DayTime.Past) {
        RevealPill(number, weekday, face, description, reveal, ink, modifier)
        return
    }
    val (container, content) =
        when (time) {
            DayTime.Past -> Lime to Ink
            DayTime.Today -> ink to MaterialTheme.colorScheme.background
            DayTime.Future -> Color.Transparent to ink
        }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (time == DayTime.Future) BorderStroke(1.5.dp, ink) else null,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        ) {
            DayCircle(time, face, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(weekday, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Día pasado que se está enseñando: empieza con borde y el círculo vacío, salen los ojos y la boca
 * y se rellena de lima con un pequeño latido. El borde y el relleno se pintan en la fase de dibujo,
 * para no recomponer en cada fotograma.
 */
@Composable
private fun RevealPill(
    number: String,
    weekday: String,
    face: SimpleFace?,
    description: String,
    reveal: DayReveal,
    ink: Color,
    modifier: Modifier,
) {
    val green = reveal.green.value.coerceIn(0f, 1f)
    val content = lerp(ink, Ink, green)
    val border = with(LocalDensity.current) { 1.5.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
        modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .graphicsLayer {
                val bump = 1f + REVEAL_BUMP * sin(PI.toFloat() * reveal.green.value.coerceIn(0f, 1f))
                scaleX = bump
                scaleY = bump
            }
            .drawBehind {
                val g = reveal.green.value.coerceIn(0f, 1f)
                val radius = CornerRadius(size.width / 2)
                drawRoundRect(Lime.copy(alpha = g), cornerRadius = radius)
                if (g < 1f) {
                    drawRoundRect(
                        ink.copy(alpha = 1f - g),
                        topLeft = Offset(border / 2, border / 2),
                        size = Size(size.width - border, size.height - border),
                        cornerRadius = CornerRadius(size.width / 2 - border / 2),
                        style = Stroke(border),
                    )
                }
            }
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Cream, CircleShape)) {
            if (face != null) RevealFace(face, reveal, Modifier.fillMaxSize())
        }
        Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
        Text(weekday, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/** Cuánto crece la píldora a mitad del paso a verde. */
private const val REVEAL_BUMP = 0.08f

/** Los ojos se abren de arriba abajo y la boca crece desde su centro hacia fuera. */
@Composable
private fun RevealFace(face: SimpleFace, reveal: DayReveal, modifier: Modifier = Modifier) {
    Box(modifier) {
        Icon(
            painterResource(face.eyes),
            contentDescription = null,
            tint = Ink,
            modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val e = reveal.eyes.value
                transformOrigin = TransformOrigin(0.5f, face.eyesY)
                scaleY = e.coerceAtLeast(0f)
                scaleX = (0.6f + 0.4f * e).coerceAtLeast(0f)
                alpha = e.coerceIn(0f, 1f)
            },
        )
        Icon(
            painterResource(face.mouth),
            contentDescription = null,
            tint = Ink,
            modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val m = reveal.mouth.value.coerceAtLeast(0f)
                transformOrigin = TransformOrigin(face.mouthX, face.mouthY)
                scaleX = m
                scaleY = m
                alpha = m.coerceIn(0f, 1f)
            },
        )
    }
}

/** Círculo de arriba: crema con la cara en días pasados y hoy; en los que vienen, solo un punto. */
@Composable
private fun DayCircle(time: DayTime, face: SimpleFace?, modifier: Modifier = Modifier) {
    if (time == DayTime.Future) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
        }
    } else {
        Box(modifier.background(Cream, CircleShape)) {
            if (face != null) FaceDrawing(face, Modifier.fillMaxSize())
        }
    }
}

/** Dibujo de la cara en tinta, ocupando todo el círculo (el encuadre ya va en el dibujo). */
@Composable
fun FaceDrawing(face: SimpleFace, modifier: Modifier = Modifier) {
    Icon(painterResource(face.drawable), contentDescription = null, tint = Ink, modifier = modifier)
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun FacesPreview() {
    TouchGrassTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            SimpleFace.entries.forEach { face ->
                Box(Modifier.size(48.dp).background(Cream, CircleShape)) { FaceDrawing(face, Modifier.fillMaxSize()) }
            }
        }
    }
}
