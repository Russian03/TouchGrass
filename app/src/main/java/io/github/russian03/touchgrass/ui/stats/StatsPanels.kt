package io.github.russian03.touchgrass.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayFace
import io.github.russian03.touchgrass.core.stats.MonthStats
import io.github.russian03.touchgrass.ui.common.BeigeCard
import io.github.russian03.touchgrass.ui.common.beigeCardColor
import io.github.russian03.touchgrass.ui.common.weekdayDay
import io.github.russian03.touchgrass.ui.design.AnimatedFlame
import io.github.russian03.touchgrass.ui.design.FaceDrawing
import io.github.russian03.touchgrass.ui.design.FitText
import io.github.russian03.touchgrass.ui.design.SimpleFace
import io.github.russian03.touchgrass.ui.design.Tile
import io.github.russian03.touchgrass.ui.design.simple
import io.github.russian03.touchgrass.ui.theme.Alert
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Dull
import io.github.russian03.touchgrass.ui.theme.FaceGood
import io.github.russian03.touchgrass.ui.theme.FaceGreat
import io.github.russian03.touchgrass.ui.theme.FaceSerious
import io.github.russian03.touchgrass.ui.theme.FaceSoSo
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Color de cada cara en el calendario: más distintos entre sí que en «Mi semana». */
internal fun SimpleFace.calendarColor(): Color = when (this) {
    SimpleFace.Great -> FaceGreat
    SimpleFace.Good -> FaceGood
    SimpleFace.Serious -> FaceSerious
    SimpleFace.SoSo -> FaceSoSo
    SimpleFace.Bad -> Dull
    SimpleFace.Off -> Alert
}

/** Nombre de cada cara, para el recuento y los lectores de pantalla. */
private fun SimpleFace.label(): Int = when (this) {
    SimpleFace.Great -> R.string.face_great
    SimpleFace.Good -> R.string.face_good
    SimpleFace.Serious -> R.string.face_serious
    SimpleFace.SoSo -> R.string.face_soso
    SimpleFace.Bad -> R.string.face_bad
    SimpleFace.Off -> R.string.face_off
}

private val CellShape = RoundedCornerShape(10.dp)

/**
 * El mes como calendario, de lunes a domingo: cada día pasado con su cara y su color, los de antes
 * de empezar en gris con un guion, hoy con borde y sin cara (aún no ha acabado) y los que vienen
 * solo con un borde suave. Debajo, cuántos días ha habido de cada cara.
 */
@Composable
internal fun CalendarCard(month: MonthStats, today: LocalDate, modifier: Modifier = Modifier) {
    val first = month.month.atDay(1)
    val blanks = first.dayOfWeek.value - 1
    val days = month.calendar.keys.sorted()
    val cells: List<LocalDate?> = List(blanks) { null } + days
    BeigeCard(modifier) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { weekday ->
                Text(
                    weekday.getDisplayName(TextStyle.NARROW, SPANISH).uppercase(SPANISH),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(DayOfWeek.entries.size).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                repeat(DayOfWeek.entries.size) { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).padding(3.dp).aspectRatio(1f)) {
                        if (day != null) CalendarDay(day, month.calendar[day], today)
                    }
                }
            }
        }
        if (month.faces.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FaceCounts(month.faces, Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun CalendarDay(day: LocalDate, face: DayFace?, today: LocalDate) {
    val simple = face?.simple()
    val name = weekdayDay(day)
    val description =
        when {
            day == today -> stringResource(R.string.stats_calendar_today, name)
            face == null -> name
            simple == null -> stringResource(R.string.week_day_untracked, name)
            else -> stringResource(R.string.stats_calendar_day, name, stringResource(simple.label()))
        }
    val base = Modifier.fillMaxSize().clip(CellShape).semantics { contentDescription = description }
    when {
        day == today ->
            Box(
                base.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CellShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        face == null ->
            Box(base.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CellShape))
        simple == null ->
            Box(base.background(Dull), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(0.36f).height(3.dp).background(Ink, CircleShape))
            }
        else ->
            Box(base.background(simple.calendarColor())) {
                FaceDrawing(simple, Modifier.fillMaxSize().padding(1.dp))
            }
    }
}

/** El recuento del mes: cada cara que ha salido, en su color, con cuántas veces. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaceCounts(faces: Map<DayFace, Int>, modifier: Modifier = Modifier) {
    val counts =
        faces.entries
            .mapNotNull { (face, count) -> face.simple()?.let { it to count } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SimpleFace.entries.filter { it in counts }.forEach { face ->
            val count = counts.getValue(face)
            val label = stringResource(face.label())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $count" },
            ) {
                Box(Modifier.size(30.dp).clip(CellShape).background(face.calendarColor())) {
                    FaceDrawing(face, Modifier.fillMaxSize())
                }
                Text(
                    "×$count",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * Los recuadros del mes: minutos de media al día y la mejor racha arriba; la racha actual, a lo
 * ancho y con cómo se cuenta; y las veces que saltó el bloqueo, con su línea día a día.
 */
@Composable
internal fun StatTiles(month: MonthStats, currentStreak: Int, bestStreak: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(beigeCardColor(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).aspectRatio(1f)) {
                TileTitle(stringResource(R.string.stats_tile_average))
                Spacer(Modifier.weight(1f))
                BigNumber(formatDuration(month.averageMinutes))
                Text(stringResource(R.string.stats_tile_average_unit), style = MaterialTheme.typography.bodySmall)
            }
            Tile(Ink, Cream, Modifier.weight(1f).aspectRatio(1f)) {
                TileTitle(stringResource(R.string.stats_tile_best))
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth()) {
                    // Deja sitio al fuego de la derecha.
                    Column(Modifier.padding(end = 48.dp)) {
                        BigNumber(bestStreak.toString())
                        Text(
                            pluralStringResource(R.plurals.stats_days, bestStreak),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AnimatedFlame(Modifier.align(Alignment.BottomEnd).width(44.dp))
                }
            }
        }
        Tile(Lime, Ink, Modifier.fillMaxWidth()) {
            TileTitle(stringResource(R.string.stats_tile_streak))
            BigNumber(currentStreak.toString(), Modifier.padding(top = 8.dp))
            Text(
                pluralStringResource(R.plurals.achievements_streak_days, currentStreak),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.stats_tile_streak_how),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Tile(beigeCardColor(), MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth()) {
            TileTitle(stringResource(R.string.stats_block_count))
            BigNumber(month.blockCount.toString(), Modifier.padding(top = 8.dp))
            LineChart(
                values = month.blocksByDay,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(top = 12.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun TileTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

/** Número grande del recuadro: encoge si no cabe («1 h 30 min» en un recuadro estrecho). */
@Composable
private fun BigNumber(text: String, modifier: Modifier = Modifier) {
    FitText(
        text = text,
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier,
    )
}

/**
 * Gráfico de línea de [values] (uno por día), suavizado, con el área de debajo sombreada y un punto
 * en el último día. Se dibuja de izquierda a derecha al entrar.
 */
@Composable
private fun LineChart(values: List<Int>, modifier: Modifier = Modifier, color: Color = Ink) {
    val still = LocalInspectionMode.current
    val reveal = remember(values) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(values) { reveal.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1)
        val dot = 4.dp.toPx()
        val top = dot
        val bottom = size.height - dot
        val points =
            values.mapIndexed { i, v ->
                val x = if (values.size == 1) size.width / 2 else i * (size.width - dot * 2) / (values.size - 1) + dot
                Offset(x, bottom - (bottom - top) * v / max)
            }
        val line =
            Path().apply {
                moveTo(points.first().x, points.first().y)
                points.zipWithNext().forEach { (a, b) ->
                    val midX = (a.x + b.x) / 2
                    cubicTo(midX, a.y, midX, b.y, b.x, b.y)
                }
            }
        val area =
            Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
        clipRect(right = size.width * reveal.value) {
            drawPath(area, color.copy(alpha = 0.1f))
            drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (reveal.value >= 1f) drawCircle(color, radius = dot, center = points.last())
    }
}

private const val REVEAL_MS = 900

private val SPANISH = Locale.forLanguageTag("es-ES")
