package io.github.russian03.touchgrass.ui.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayRow
import io.github.russian03.touchgrass.core.stats.DayStats
import io.github.russian03.touchgrass.core.stats.MonthStats
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.StatsState
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.core.stats.UnlockRecord
import io.github.russian03.touchgrass.ui.common.BeigeCard
import io.github.russian03.touchgrass.ui.common.duration
import io.github.russian03.touchgrass.ui.common.monthName
import io.github.russian03.touchgrass.ui.common.monthShort
import io.github.russian03.touchgrass.ui.common.monthTitle
import io.github.russian03.touchgrass.ui.common.reasonLabel
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.common.weekdayDay
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.SectionTitle
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Estadísticas: se abren en el mes en curso y se puede ir a los anteriores. */
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(state = state, onBack = onBack, onShow = viewModel::show)
}

/**
 * Estadísticas con el estilo de Inicio: el banner lima (el mismo de Inicio, que encoge al llegar)
 * con el título y el mes; debajo, en cascada, el calendario del mes con el recuento de caras, los
 * recuadros (media, rachas y bloqueos) y el detalle de siempre.
 */
@Composable
private fun StatsContent(state: StatsUiState?, onBack: () -> Unit, onShow: (YearMonth) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            LimeHeader {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                MixedTitle(
                    text = stringResource(R.string.stats_header),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
                if (state != null) {
                    MonthSwitcher(state, onShow, Modifier.padding(top = 12.dp, end = 12.dp))
                    // Las caras y las medias del mes van según su límite: se ve lo primero.
                    MonthLimit(state.limits, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
                }
            }
            if (state == null) return@Column
            val month = state.month
            // Al cambiar de mes, la cascada vuelve a empezar.
            key(month.month) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                    var order = 1
                    CalendarCard(month, state.today, Modifier.padding(top = 4.dp).staggerIn(order++))
                    StatTiles(
                        month,
                        state.currentStreak,
                        state.bestStreak,
                        Modifier.padding(top = 16.dp).staggerIn(order++),
                    )
                    if (month.trackedDays > 0) {
                        SectionTitle(stringResource(R.string.stats_more_section), Modifier.staggerIn(order++))
                        Column(Modifier.staggerIn(order), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SummaryCard(month)
                            if (month.apps.isNotEmpty()) AppsCard(month, state.appNames)
                            if (month.unlockCount > 0) ReasonsCard(month)
                            if (state.comparison.size > 1) ComparisonCard(state.comparison)
                            DaysCard(month, state.today, state.appNames)
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de tinta con el límite diario del mes («90 min al día», o «60–90» si cambió). */
@Composable
private fun MonthLimit(limits: List<Int>, modifier: Modifier = Modifier) {
    if (limits.isEmpty()) return
    val range =
        if (limits.size == 1) duration(limits.first()) else "${duration(limits.first())}–${duration(limits.last())}"
    Text(
        text = stringResource(R.string.stats_month_limit, range),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Cream,
        modifier = modifier.background(Ink, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** El mes que se mira, con las flechas para ir al anterior y al siguiente con datos. */
@Composable
private fun MonthSwitcher(state: StatsUiState, onShow: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        val previous = state.previous
        val next = state.next
        IconButton(onClick = { previous?.let(onShow) }, enabled = previous != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.stats_previous_month),
            )
        }
        Text(
            text = monthTitle(state.month.month),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { next?.let(onShow) }, enabled = next != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.stats_next_month),
            )
        }
    }
}

/** Lo que no sale en los recuadros: total desbloqueado, veces que se pidió tiempo y se volvió atrás. */
@Composable
private fun SummaryCard(month: MonthStats) {
    BeigeCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = duration(month.unlockedMinutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = stringResource(R.string.stats_unlocked_minutes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatLine(stringResource(R.string.stats_unlock_count), month.unlockCount.toString())
            StatLine(stringResource(R.string.stats_leave_count), month.leaveCount.toString())
            StatLine(
                stringResource(R.string.stats_days_without_unlock),
                stringResource(R.string.stats_days_of, month.daysWithoutUnlock, month.trackedDays),
            )
            StatLine(
                stringResource(R.string.stats_days_within_limit),
                stringResource(R.string.stats_days_of, month.daysWithinLimit, month.trackedDays),
            )
        }
    }
}

@Composable
private fun AppsCard(month: MonthStats, appNames: Map<String, String>) {
    BeigeCard {
        CardTitle(stringResource(R.string.stats_apps_title))
        month.apps.forEachIndexed { index, app ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Column(Modifier.padding(top = if (index == 0) 12.dp else 0.dp)) {
                Text(
                    appNames[app.packageName] ?: app.packageName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Body(
                    stringResource(
                        R.string.stats_app_line,
                        duration(app.unlockedMinutes),
                        pluralStringResource(R.plurals.stats_times, app.unlockCount, app.unlockCount),
                        pluralStringResource(R.plurals.stats_blocks, app.blockCount, app.blockCount),
                    ),
                    Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ReasonsCard(month: MonthStats) {
    val max = month.reasons.values.maxOrNull() ?: return
    BeigeCard {
        CardTitle(stringResource(R.string.stats_reasons_title))
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UnlockReason.entries
                .map { it to (month.reasons[it] ?: 0) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .forEach { (reason, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(reasonLabel(reason)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(96.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            Bar(fraction = count.toFloat() / max, highlighted = true, horizontal = true)
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(32.dp),
                        )
                    }
                }
        }
    }
}

/** Media de minutos al día de cada mes, con el que se mira resaltado, y la diferencia con el anterior. */
@Composable
private fun ComparisonCard(months: List<MonthStats>) {
    val current = months.last()
    val previous = months[months.size - 2]
    val max = months.maxOf { it.averageMinutes }.coerceAtLeast(1.0)
    BeigeCard {
        CardTitle(stringResource(R.string.stats_comparison_title))
        Body(stringResource(R.string.stats_comparison_body), Modifier.padding(top = 2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            months.forEach { month ->
                val label =
                    stringResource(
                        R.string.stats_comparison_bar,
                        monthName(month.month),
                        formatDuration(month.averageMinutes),
                    )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = label },
                ) {
                    Text(formatDuration(month.averageMinutes), style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.height(96.dp).padding(top = 4.dp), contentAlignment = Alignment.BottomCenter) {
                        Bar(fraction = (month.averageMinutes / max).toFloat(), highlighted = month === current)
                    }
                    Text(
                        monthShort(month.month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            text = comparisonSentence(current, previous),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (current.averageMinutes <=
                previous.averageMinutes
            ) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun comparisonSentence(current: MonthStats, previous: MonthStats): String {
    val name = monthName(previous.month)
    val diff = current.averageMinutes - previous.averageMinutes
    if (abs(diff).roundToInt() == 0) return stringResource(R.string.stats_comparison_same, name)
    val percent =
        if (previous.averageMinutes > 0) (abs(diff) / previous.averageMinutes * 100).roundToInt() else null
    val amount = formatDuration(abs(diff))
    return when {
        diff < 0 && percent != null -> stringResource(R.string.stats_comparison_less, amount, name, percent)
        diff < 0 -> stringResource(R.string.stats_comparison_less_plain, amount, name)
        percent != null -> stringResource(R.string.stats_comparison_more, amount, name, percent)
        else -> stringResource(R.string.stats_comparison_more_plain, amount, name)
    }
}

@Composable
private fun DaysCard(month: MonthStats, today: LocalDate, appNames: Map<String, String>) {
    BeigeCard {
        CardTitle(stringResource(R.string.stats_days_title))
        month.days.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            DayItem(row, today, appNames)
        }
    }
}

/** Un día: lo que se desbloqueó y cuántas veces saltó el bloqueo. Se despliega con los desbloqueos. */
@Composable
private fun DayItem(row: DayRow, today: LocalDate, appNames: Map<String, String>) {
    var expanded by rememberSaveable(row.day.toString()) { mutableStateOf(false) }
    val unlocks = row.stats.unlocks
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = unlocks.isNotEmpty()) { expanded = !expanded }
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                when (row.day) {
                    today -> stringResource(R.string.stats_today)
                    today.minusDays(1) -> stringResource(R.string.stats_yesterday)
                    else -> weekdayDay(row.day)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                if (unlocks.isEmpty()) {
                    stringResource(R.string.stats_day_no_unlock)
                } else {
                    duration(row.unlockedMinutes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (unlocks.isEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        if (row.blockCount > 0) {
            Body(
                pluralStringResource(R.plurals.stats_blocks, row.blockCount, row.blockCount),
                Modifier.padding(top = 2.dp),
            )
        }
        if (expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                unlocks.sortedBy { it.startWallMs }.forEach { UnlockLine(it, appNames) }
            }
        }
    }
}

@Composable
private fun UnlockLine(record: UnlockRecord, appNames: Map<String, String>) {
    val reason = stringResource(reasonLabel(record.reason)).let { label -> record.note?.let { "$label: $it" } ?: label }
    val minutes =
        if (record.refundedMinutes > 0) {
            stringResource(
                R.string.stats_unlock_refunded,
                duration(record.netMinutes),
                pluralStringResource(R.plurals.stats_refunded, record.refundedMinutes, record.refundedMinutes),
            )
        } else {
            duration(record.minutes)
        }
    Body(
        stringResource(
            R.string.stats_unlock_line,
            timeOf(record.startWallMs),
            appNames[record.packageName] ?: record.packageName,
            minutes,
            reason,
        ),
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** Barra de un gráfico: vertical (llena el alto del padre) u horizontal (llena el ancho). */
@Composable
private fun Bar(fraction: Float, highlighted: Boolean, horizontal: Boolean = false) {
    val color =
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val size = fraction.coerceIn(0.02f, 1f)
    val shape = RoundedCornerShape(4.dp)
    if (horizontal) {
        Box(Modifier.fillMaxWidth(size).height(10.dp).background(color, shape))
    } else {
        Box(Modifier.width(20.dp).fillMaxHeight(size).background(color, shape))
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Una media de minutos, redondeada al minuto y en horas y minutos: "1 h 5 min". */
internal fun formatDuration(minutes: Double): String = duration(minutes.roundToInt())

private fun previewState(): StatsUiState {
    val today = LocalDate.of(2026, 10, 14)
    val zone = ZoneId.systemDefault()
    fun at(day: LocalDate, hour: Int) = day.atTime(hour, 15).atZone(zone).toInstant().toEpochMilli()
    val days =
        mapOf(
            "2026-10-14" to
                DayStats(
                    limitMinutes = 30,
                    unlocks =
                    listOf(
                        UnlockRecord(IG, 10, at(today, 9), UnlockReason.Bus),
                        UnlockRecord(
                            TT,
                            15,
                            at(today, 14),
                            UnlockReason.Other,
                            note = "Ver un vídeo de cocina",
                            refundedMinutes = 5,
                        ),
                    ),
                    blocks = mapOf(IG to 4, TT to 2),
                    leaves = mapOf(IG to 3),
                ),
            "2026-10-12" to DayStats(blocks = mapOf(IG to 1), leaves = mapOf(IG to 1)),
            "2026-09-20" to
                DayStats(
                    limitMinutes = 30,
                    unlocks = listOf(UnlockRecord(IG, 30, at(today.minusDays(24), 22), UnlockReason.Sleep)),
                ),
            "2026-08-20" to
                DayStats(
                    limitMinutes = 30,
                    unlocks = listOf(UnlockRecord(IG, 60, at(today.minusDays(55), 22), UnlockReason.Hobby)),
                ),
        )
    val stats = StatsState(since = "2026-08-01", days = days)
    val month = YearMonth.from(today)
    return StatsUiState(
        today = today,
        months = StatsPolicy.months(stats, today),
        month = StatsPolicy.month(stats, month, today),
        comparison = StatsPolicy.comparison(stats, month, today),
        appNames = mapOf(IG to "Instagram", TT to "TikTok"),
        currentStreak = 5,
        bestStreak = 14,
        limits = listOf(60, 90),
    )
}

private const val IG = "com.instagram.android"
private const val TT = "com.zhiliaoapp.musically"

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun StatsPreview() {
    TouchGrassTheme { StatsContent(previewState(), {}, {}) }
}
