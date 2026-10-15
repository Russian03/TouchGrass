package io.github.russian03.touchgrass.ui.design

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Momento de un día de la semana respecto a hoy. */
enum class DayTime { Past, Today, Future }

/** Las cinco caras de los días, de mejor a peor, con su dibujo. */
enum class SimpleFace(@DrawableRes val drawable: Int) {
    /** Sin pedir tiempo o con racha de poco uso. */
    Great(R.drawable.face_genial),

    /** Hasta el 25 % del límite. */
    Good(R.drawable.face_bien),

    /** Hasta la mitad. */
    Serious(R.drawable.face_seria),

    /** Más de la mitad sin agotarlo. */
    SoSo(R.drawable.face_mediomal),

    /** Saldo agotado. */
    Bad(R.drawable.face_mal),
}

/** Cara de un día, o `null` si ese día no se contaba. */
fun DayFace.simple(): SimpleFace? = when (this) {
    DayFace.Mythic, DayFace.Legend, DayFace.Cool, DayFace.Free -> SimpleFace.Great
    DayFace.Happy -> SimpleFace.Good
    DayFace.Calm -> SimpleFace.Serious
    DayFace.Neutral, DayFace.Tight -> SimpleFace.SoSo
    DayFace.Sad -> SimpleFace.Bad
    DayFace.Untracked -> null
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
) {
    // La tinta sigue al tema, para que hoy y los días que vienen se vean también en modo noche.
    val ink = MaterialTheme.colorScheme.onBackground
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
