package io.github.russian03.touchgrass.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.core.stats.DayMark
import io.github.russian03.touchgrass.ui.theme.Alert
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime

/** Cara que resume un día: contenta sin pedir tiempo, seria al pedirlo y triste al agotarlo. */
enum class Mood { Happy, Neutral, Sad, None }

fun DayMark.mood(): Mood = when (this) {
    DayMark.Free -> Mood.Happy
    DayMark.Unlocked -> Mood.Neutral
    DayMark.Exhausted -> Mood.Sad
    DayMark.Untracked -> Mood.None
}

/**
 * Píldora vertical de «Mi semana»: una cara arriba y el día debajo, como en «My Week». Los días
 * sin pedir tiempo van en lima; hoy, en tinta; el resto, solo con borde.
 */
@Composable
fun DayPill(
    number: String,
    weekday: String,
    mark: DayMark,
    today: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val (container, content, border) =
        when {
            today -> Triple(ink, MaterialTheme.colorScheme.background, null)
            mark == DayMark.Free -> Triple(Lime, Ink, null)
            mark == DayMark.Untracked -> Triple(Color.Transparent, ink.copy(alpha = 0.4f), ink.copy(alpha = 0.2f))
            else -> Triple(Color.Transparent, ink, ink)
        }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border?.let { BorderStroke(1.5.dp, it) },
        modifier = modifier.width(52.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        ) {
            val faceBackground = if (today) Lime else Cream
            Box(Modifier.size(40.dp).background(faceBackground, CircleShape), contentAlignment = Alignment.Center) {
                Face(
                    mark.mood(),
                    color = if (mark ==
                        DayMark.Exhausted
                    ) {
                        Alert
                    } else {
                        Ink
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(weekday, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Carita de línea dibujada a mano: dos puntos y una boca que sonríe, se queda recta o cae. */
@Composable
fun Face(mood: Mood, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.08f).coerceAtLeast(1.5.dp.toPx())
        if (mood == Mood.None) {
            drawLine(
                color.copy(alpha = 0.4f),
                Offset(w * 0.3f, h * 0.5f),
                Offset(w * 0.7f, h * 0.5f),
                stroke,
                StrokeCap.Round,
            )
            return@Canvas
        }
        drawCircle(color, radius = stroke * 0.8f, center = Offset(w * 0.33f, h * 0.38f))
        drawCircle(color, radius = stroke * 0.8f, center = Offset(w * 0.67f, h * 0.38f))
        val mouth = Stroke(width = stroke, cap = StrokeCap.Round)
        when (mood) {
            Mood.Happy ->
                drawArc(color, 20f, 140f, false, Offset(w * 0.25f, h * 0.35f), Size(w * 0.5f, h * 0.38f), style = mouth)
            Mood.Sad ->
                drawArc(color, 200f, 140f, false, Offset(w * 0.25f, h * 0.62f), Size(w * 0.5f, h * 0.3f), style = mouth)
            else -> drawLine(color, Offset(w * 0.32f, h * 0.7f), Offset(w * 0.68f, h * 0.7f), stroke, StrokeCap.Round)
        }
    }
}
