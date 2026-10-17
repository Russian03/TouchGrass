package io.github.russian03.touchgrass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.design.AnimatedFlame
import io.github.russian03.touchgrass.ui.design.Tile
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Mis logros», al final de Inicio: a la izquierda, el recuadro lima que abre las estadísticas,
 * con un gráfico de barras que crece; a la derecha, el negro con los días de racha ([streak]) y el
 * fuego.
 */
@Composable
fun Achievements(streak: Int, onOpenStats: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        StatsTile(onOpenStats, Modifier.weight(1f).aspectRatio(1f))
        StreakTile(streak, Modifier.weight(1f).aspectRatio(1f))
    }
}

/**
 * Recuadro que abre las estadísticas. Para que se vea que se puede pulsar, lleva una flecha en un
 * círculo que cada pocos segundos da un empujoncito, a la vez que el recuadro late muy leve; al
 * pulsarlo encoge y las barras dan un saltito.
 */
@Composable
private fun StatsTile(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val still = LocalInspectionMode.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val nudge = remember { Animatable(0f) }
    val beat = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(HINT_FIRST_MS)
        while (true) {
            launch {
                beat.animateTo(HINT_BEAT, tween(HINT_UP_MS))
                beat.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
            }
            nudge.animateTo(1f, tween(HINT_UP_MS))
            nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f))
            delay(HINT_EVERY_MS)
        }
    }
    val jump by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "barsJump",
    )
    Tile(
        color = Lime,
        contentColor = Ink,
        onClick = onOpen,
        interaction = interaction,
        modifier =
        modifier.graphicsLayer {
            scaleX = beat.value
            scaleY = beat.value
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(R.string.achievements_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).background(Ink, CircleShape),
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Cream,
                    modifier =
                    Modifier.size(20.dp).graphicsLayer { translationX = nudge.value * 4.dp.toPx() },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GrowingBars(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + 0.12f * jump
                },
        )
    }
}

/** La pista de que se puede pulsar: primera vez, cada cuánto, cuánto late y lo que tarda en subir. */
private const val HINT_FIRST_MS = 1_200L
private const val HINT_EVERY_MS = 3_000L
private const val HINT_BEAT = 1.03f
private const val HINT_UP_MS = 180

@Composable
private fun StreakTile(streak: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.achievements_streak_days, streak)
    Tile(
        color = Ink,
        contentColor = Cream,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$streak $label" },
    ) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            Column {
                Text(streak.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            AnimatedFlame(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 0.dp)
                    .width(64.dp),
            )
        }
    }
}

/** Alturas de las barras del dibujito, de izquierda a derecha. */
private val BarHeights = listOf(0.45f, 0.75f, 0.55f, 1f)

/**
 * Dibujito de gráfico de barras: al entrar, las barras crecen una detrás de otra; después
 * respiran un poco, cada una a destiempo. En las vistas previas sale quieto.
 */
@Composable
private fun GrowingBars(modifier: Modifier = Modifier) {
    val still = LocalInspectionMode.current
    val grow = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing)) }
    val breath by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Restart),
        label = "barsBreath",
    )
    Canvas(modifier) {
        val gap = size.width * 0.08f
        val barW = (size.width - gap * (BarHeights.size - 1)) / BarHeights.size
        BarHeights.forEachIndexed { i, base ->
            // Cada barra empieza a crecer un poco después que la anterior.
            val g = ((grow.value - i * STAGGER) / (1f - STAGGER * (BarHeights.size - 1))).coerceIn(0f, 1f)
            val wave = if (still) 0f else sin(2 * PI * (breath + i * 0.25f)).toFloat()
            val h = size.height * base * g * (1f + 0.05f * wave)
            drawRoundRect(
                color = Ink,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW * 0.3f),
            )
        }
    }
}

private const val GROW_MS = 900
private const val BREATH_MS = 2_400
private const val STAGGER = 0.15f

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF, widthDp = 360)
@Composable
private fun AchievementsPreview() {
    TouchGrassTheme { Achievements(streak = 5, onOpenStats = {}, modifier = Modifier.padding(24.dp)) }
}
