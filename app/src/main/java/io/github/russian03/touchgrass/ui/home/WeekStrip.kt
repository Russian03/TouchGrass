package io.github.russian03.touchgrass.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.github.russian03.touchgrass.ui.design.fadingEdges
import io.github.russian03.touchgrass.ui.design.simple
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import kotlin.math.roundToInt

/** Retraso entre píldoras al entrar, de izquierda a derecha. */
private const val PILL_STEP_MS = 40L

/** Cascada de las píldoras que entran al volver del pop-up: retraso entre una y otra y lo que dura cada una. */
private const val APPEAR_STEP = 0.12f
private const val APPEAR_SPAN = 0.5f

/** Píldoras que caben a la vez: la que asoma por el borde invita a deslizar. */
internal const val VISIBLE_PILLS = 6.75f
private val PillGap = 10.dp

/** Margen de la pantalla, el mismo que el resto de Inicio. */
internal val ScreenEdge = 24.dp

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
 *
 * Con [appearing], las píldoras que no se veían en ese momento entran desde abajo, en cascada,
 * según [appear] (de 0 a 1): es lo que pasa al volver del pop-up a Inicio, cuando caben más.
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
    // Empieza al final (la semana actual): el valor se ajusta al máximo real al medir.
    scroll: ScrollState = rememberScrollState(Int.MAX_VALUE),
    appearing: Boolean = false,
    appear: () -> Float = { 1f },
) {
    val currentWeekStart = (week.size - StatsPolicy.WEEK_DAYS).coerceAtLeast(0)
    // Un día por descubrir sin animación propia se queda con todo a 0.
    val hidden = remember { DayReveal() }
    BoxWithConstraints(if (bleed) modifier.bleed(edge) else modifier) {
        val pillWidth = (maxWidth - edge - PillGap * (visiblePills - 1)) / visiblePills
        val density = LocalDensity.current
        // Píldoras que se veían cuando empezó a entrar el resto.
        val shown =
            remember(appearing) {
                if (!appearing) return@remember null
                with(density) {
                    val step = (pillWidth + PillGap).toPx()
                    val from = scroll.value - edge.toPx()
                    val to = from + maxWidth.toPx()
                    week.indices.filter { i ->
                        val left = i * step
                        left + pillWidth.toPx() * 0.7f > from && left + pillWidth.toPx() * 0.3f < to
                    }.toSet()
                }
            }
        val entering = shown?.let { week.indices.filter { it !in shown && it > shown.max() } }.orEmpty()
        val rise = with(density) { 8.dp.toPx() }
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
                .fadingEdges(scroll, Orientation.Horizontal)
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
                    // Hoy aún no ha acabado: sin cara hasta que se enseñe mañana.
                    face = if (time == DayTime.Past) day.face.simple() else null,
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
                            if (i in entering) {
                                val k = entering.indexOf(i)
                                Modifier.graphicsLayer {
                                    val a = ((appear() - k * APPEAR_STEP) / APPEAR_SPAN).coerceIn(0f, 1f)
                                    alpha = a
                                    translationY = (1f - a) * rise
                                    val scale = 0.8f + 0.2f * a
                                    scaleX = scale
                                    scaleY = scale
                                }
                            } else {
                                Modifier
                            },
                        )
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
    val faces = listOf(DayFace.Free, DayFace.Happy, DayFace.Neutral, DayFace.Sad, DayFace.Disabled, DayFace.Cool)
    return (0 until 14).map { i ->
        val day = LocalDate.of(2026, 10, 5 + i)
        // Los dos primeros, antes de empezar a usar la app.
        val face = if (i >= 2 && day <= LocalDate.of(2026, 10, 14)) faces[i % faces.size] else DayFace.Untracked
        WeekDay(day, face, 0, 10, 30)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun WeekStripPreview() {
    TouchGrassTheme { WeekStrip(previewWeek(), LocalDate.of(2026, 10, 14), Modifier.padding(24.dp)) }
}
