package io.github.russian03.touchgrass.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayFace
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.ui.common.weekdayDay
import io.github.russian03.touchgrass.ui.common.weekdayShort
import io.github.russian03.touchgrass.ui.design.DayPill
import io.github.russian03.touchgrass.ui.design.DayReveal
import io.github.russian03.touchgrass.ui.design.DayTime
import io.github.russian03.touchgrass.ui.design.simple
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import kotlin.math.roundToInt

/** Retraso entre píldoras al entrar, de izquierda a derecha. */
private const val PILL_STEP_MS = 40L

/** Píldoras que caben a la vez: la que asoma por el borde invita a deslizar. */
private const val VISIBLE_PILLS = 6.75f
private val PillGap = 10.dp

/** Margen de la pantalla, el mismo que el resto de Inicio. */
private val ScreenEdge = 24.dp

/**
 * «Mi semana»: la semana anterior y la actual, de lunes a domingo, como «My Week». Ocupa todo el
 * ancho de la pantalla y se desliza de lado; empieza mostrando la semana actual, con unas seis
 * píldoras y tres cuartos a la vista. Cada día pasado y hoy llevan su cara.
 *
 * Los días [pending] aún no se han enseñado: salen «por descubrir» o, si están en [reveals], con su
 * animación. Con [focus], la tira se desliza sola hasta dejar ese día en el centro. En el pop-up
 * del primer acceso del día la tira va dentro de un recuadro: sin salirse por los lados
 * ([bleed] a `false`), con píldoras más grandes ([visiblePills]) y sin que se pueda tocar
 * ([interactive] a `false`). [growRoom] deja sitio arriba y abajo para que una píldora que crece
 * al animarse no quede cortada por el borde de la tira.
 */
@Composable
fun WeekStrip(
    week: List<WeekDay>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    pending: Set<LocalDate> = emptySet(),
    reveals: Map<LocalDate, DayReveal> = emptyMap(),
    focus: LocalDate? = null,
    visiblePills: Float = VISIBLE_PILLS,
    edge: Dp = ScreenEdge,
    bleed: Boolean = true,
    growRoom: Dp = 0.dp,
    interactive: Boolean = true,
) {
    // Empieza al final (la semana actual): el valor se ajusta al máximo real al medir.
    val scroll = rememberScrollState(Int.MAX_VALUE)
    val currentWeekStart = (week.size - StatsPolicy.WEEK_DAYS).coerceAtLeast(0)
    // Un día por descubrir sin animación propia se queda con todo a 0.
    val hidden = remember { DayReveal() }
    BoxWithConstraints(if (bleed) modifier.bleed(edge) else modifier) {
        val pillWidth = (maxWidth - edge - PillGap * (visiblePills - 1)) / visiblePills
        val density = LocalDensity.current
        LaunchedEffect(focus, week) {
            val index = week.indexOfFirst { it.day == focus }
            if (index < 0) return@LaunchedEffect
            with(density) {
                val left = edge.toPx() + index * (pillWidth + PillGap).toPx()
                val target = left - (maxWidth.toPx() - pillWidth.toPx()) / 2
                scroll.animateScrollTo(target.roundToInt().coerceIn(0, scroll.maxValue))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PillGap),
            modifier =
            Modifier
                .fadingEdges(scroll)
                .horizontalScroll(scroll, enabled = interactive)
                .padding(horizontal = edge, vertical = growRoom),
        ) {
            week.forEachIndexed { i, day ->
                val time =
                    when {
                        day.day < today -> DayTime.Past
                        day.day == today -> DayTime.Today
                        else -> DayTime.Future
                    }
                val isPending = day.day in pending
                DayPill(
                    number = day.day.dayOfMonth.toString(),
                    weekday = weekdayShort(day.day),
                    time = time,
                    face = if (time == DayTime.Future) null else day.face.simple(),
                    description =
                    if (isPending && day.day !in reveals) {
                        stringResource(R.string.week_day_pending, weekdayDay(day.day))
                    } else {
                        describe(day, weekdayDay(day.day), time)
                    },
                    reveal = if (isPending) reveals[day.day] ?: hidden else null,
                    modifier =
                    Modifier
                        .width(pillWidth)
                        .then(
                            if (interactive) {
                                // La cascada empieza en el lunes de esta semana, que es lo que se ve al entrar.
                                Modifier.staggerIn(
                                    index = (i - currentWeekStart).coerceAtLeast(0),
                                    stepMs = PILL_STEP_MS,
                                    offsetY = 8.dp,
                                    fromScale = 0.8f,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** Ancho del difuminado de cada lado. */
private val FadeWidth = 40.dp

/**
 * Difumina los bordes de la tira por el lado en que queda más por ver, para que se entienda que
 * se puede deslizar. Cada lado aparece o se va con un fundido según la posición del scroll.
 */
@Composable
private fun Modifier.fadingEdges(scroll: ScrollState): Modifier {
    // Solo importa si queda algo a cada lado, no la posición exacta: así no se recompone en cada píxel.
    val moreBefore by remember(scroll) { derivedStateOf { scroll.value > 0 } }
    val moreAfter by remember(scroll) { derivedStateOf { scroll.value < scroll.maxValue } }
    val start by animateFloatAsState(if (moreBefore) 1f else 0f, label = "fadeStart")
    val end by animateFloatAsState(if (moreAfter) 1f else 0f, label = "fadeEnd")
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = FadeWidth.toPx()
            // Se borra lo que queda debajo del degradado (DstOut): opaco en el borde, nada hacia dentro.
            if (start > 0f) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = start), Color.Transparent),
                        startX = 0f,
                        endX = fade,
                    ),
                    size = size.copy(width = fade),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (end > 0f) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = end)),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = size.copy(width = fade),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
}

/** Ocupa también los márgenes laterales del padre, para que la tira llegue al borde de la pantalla. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun describe(day: WeekDay, name: String, time: DayTime): String = when {
    time == DayTime.Future -> name
    day.face == DayFace.Untracked -> stringResource(R.string.week_day_untracked, name)
    day.face == DayFace.Disabled -> stringResource(R.string.week_day_disabled, name)
    day.face == DayFace.Sad -> stringResource(R.string.week_day_exhausted, name)
    day.unlockedMinutes == 0 -> stringResource(R.string.week_day_free, name)
    else -> stringResource(R.string.week_day_unlocked, name, day.unlockedMinutes)
}

/** Semanas de ejemplo: del lunes 5 al domingo 18, con hoy el miércoles 14 y caras variadas. */
internal fun previewWeek(): List<WeekDay> {
    val faces = listOf(DayFace.Free, DayFace.Happy, DayFace.Neutral, DayFace.Sad, DayFace.Cool)
    return (0 until 14).map { i ->
        val day = LocalDate.of(2026, 10, 5 + i)
        val face = if (day <= LocalDate.of(2026, 10, 14)) faces[i % faces.size] else DayFace.Untracked
        WeekDay(day, face, 0, 10, 30)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun WeekStripPreview() {
    TouchGrassTheme { WeekStrip(previewWeek(), LocalDate.of(2026, 10, 14), Modifier.padding(24.dp)) }
}
