package io.github.russian03.touchgrass.ui.stats

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayRow
import io.github.russian03.touchgrass.core.stats.DayStats
import io.github.russian03.touchgrass.core.stats.MonthStats
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.StatsState
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.core.stats.UnlockRecord
import io.github.russian03.touchgrass.notification.MonthlyReport
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.TopBar
import io.github.russian03.touchgrass.ui.common.monthName
import io.github.russian03.touchgrass.ui.common.monthShort
import io.github.russian03.touchgrass.ui.common.monthTitle
import io.github.russian03.touchgrass.ui.common.reasonLabel
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.common.weekdayDay
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Estadísticas de un mes: [initialMonth] (`2026-09`) o, sin él, el mes en curso. */
@Composable
fun StatsScreen(initialMonth: String?, onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialMonth) {
        initialMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }?.let(viewModel::show)
    }
    StatsContent(
        state = state,
        notifications = rememberNotificationState(),
        onBack = onBack,
        onShow = viewModel::show,
    )
}

/** Si los avisos están activados y, si no, cómo pedirlos (o `null` si no se pueden pedir desde aquí). */
private class NotificationState(val enabled: Boolean, val request: (() -> Unit)?)

@Composable
private fun rememberNotificationState(): NotificationState {
    val context = LocalContext.current
    // Se vuelve a mirar al volver a la pantalla, p. ej. desde los ajustes del sistema.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
    val enabled = remember(lifecycle, asked) { MonthlyReport.canNotify(context) }
    val request =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        } else {
            null
        }
    return NotificationState(enabled, request)
}

@Composable
private fun StatsContent(
    state: StatsUiState?,
    notifications: NotificationState,
    onBack: () -> Unit,
    onShow: (YearMonth) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state == null) return@Column
            MonthSwitcher(state, onShow)
            val month = state.month
            if (month.trackedDays == 0) {
                Card { Body(stringResource(R.string.stats_empty)) }
            } else {
                SummaryCard(month)
                if (month.apps.isNotEmpty()) AppsCard(month, state.appNames)
                if (month.unlockCount > 0) ReasonsCard(month)
                if (state.comparison.size > 1) ComparisonCard(state.comparison)
                DaysCard(month, state.today, state.appNames)
            }
            if (!notifications.enabled) NotificationsCard(notifications.request)
        }
    }
}

@Composable
private fun MonthSwitcher(state: StatsUiState, onShow: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            fontWeight = FontWeight.Medium,
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

@Composable
private fun SummaryCard(month: MonthStats) {
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = month.unlockedMinutes.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = stringResource(R.string.stats_unlocked_minutes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        Body(
            stringResource(R.string.stats_average, formatMinutes(month.averageMinutes)),
            Modifier.padding(top = 2.dp),
        )
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatLine(stringResource(R.string.stats_unlock_count), month.unlockCount.toString())
            StatLine(stringResource(R.string.stats_block_count), month.blockCount.toString())
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
    Card {
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
                        pluralStringResource(R.plurals.minutes, app.unlockedMinutes, app.unlockedMinutes),
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
    Card {
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
    Card {
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
                        formatMinutes(month.averageMinutes),
                    )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = label },
                ) {
                    Text(formatMinutes(month.averageMinutes), style = MaterialTheme.typography.labelSmall)
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
    if (abs(diff) < 0.05) return stringResource(R.string.stats_comparison_same, name)
    val percent =
        if (previous.averageMinutes > 0) (abs(diff) / previous.averageMinutes * 100).roundToInt() else null
    val amount = formatMinutes(abs(diff))
    return when {
        diff < 0 && percent != null -> stringResource(R.string.stats_comparison_less, amount, name, percent)
        diff < 0 -> stringResource(R.string.stats_comparison_less_plain, amount, name)
        percent != null -> stringResource(R.string.stats_comparison_more, amount, name, percent)
        else -> stringResource(R.string.stats_comparison_more_plain, amount, name)
    }
}

@Composable
private fun DaysCard(month: MonthStats, today: LocalDate, appNames: Map<String, String>) {
    Card {
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
                    pluralStringResource(R.plurals.minutes, row.unlockedMinutes, row.unlockedMinutes)
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
                record.netMinutes,
                pluralStringResource(R.plurals.stats_refunded, record.refundedMinutes, record.refundedMinutes),
            )
        } else {
            pluralStringResource(R.plurals.minutes, record.minutes, record.minutes)
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
private fun NotificationsCard(request: (() -> Unit)?) {
    Card {
        CardTitle(stringResource(R.string.stats_notifications_title))
        Body(
            stringResource(
                if (request !=
                    null
                ) {
                    R.string.stats_notifications_body
                } else {
                    R.string.stats_notifications_off
                },
            ),
            Modifier.padding(top = 4.dp),
        )
        if (request != null) {
            TextButton(onClick = request, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.stats_notifications_enable))
            }
        }
    }
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

/** "12", o "2,5" con decimales. */
private fun formatMinutes(minutes: Double): String {
    val rounded = (minutes * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("es-ES"), "%.1f", rounded)
    }
}

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
    )
}

private const val IG = "com.instagram.android"
private const val TT = "com.zhiliaoapp.musically"

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun StatsPreview() {
    TouchGrassTheme { StatsContent(previewState(), NotificationState(false) {}, {}, {}) }
}
