package io.github.russian03.touchgrass.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.ProtectionRow
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTipRow
import io.github.russian03.touchgrass.ui.common.fullDay
import io.github.russian03.touchgrass.ui.common.monthName
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.OutlinePillButton
import io.github.russian03.touchgrass.ui.design.PulsingDot
import io.github.russian03.touchgrass.ui.design.SectionTitle
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenStats: (YearMonth?) -> Unit,
    onOpenUnlock: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val unlock by viewModel.unlock.collectAsStateWithLifecycle()
    val appNames by viewModel.appNames.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSummary.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    HomeContent(
        protection = rememberProtectionState(),
        budget = budget,
        week = week,
        unlock = unlock,
        unlockActions = UnlockActions(appNames = appNames, onOpen = onOpenUnlock, onEnd = viewModel::endUnlock),
        showXiaomiTip = SystemSettings.isXiaomi,
        summary = SummaryActions(pendingSummary, onOpenStats),
        onOpenSettings = onOpenSettings,
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
        onOpenDateSettings = { SystemSettings.openDateTime(context) },
    )
}

/** Resumen del mes listo para ver ([month]) y cómo abrir las estadísticas. */
private class SummaryActions(val month: YearMonth?, val onOpen: (YearMonth?) -> Unit)

private class UnlockActions(val appNames: Map<String, String>, val onOpen: () -> Unit, val onEnd: () -> Unit)

/**
 * Inicio. Arriba, la cabecera lima (fecha, título, pedir tiempo o la cuenta atrás); debajo, en
 * cascada: el resumen del mes si está listo, «Mi día», «Mi semana» y «Mi protección».
 */
@Composable
private fun HomeContent(
    protection: ProtectionState,
    budget: BudgetSummary?,
    week: List<WeekDay>,
    unlock: UnlockUiState,
    unlockActions: UnlockActions,
    showXiaomiTip: Boolean,
    summary: SummaryActions,
    onOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDateSettings: () -> Unit,
) {
    val openStats = { summary.onOpen(null) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // La cabecera baja desde arriba; el resto entra en cascada detrás.
            LimeHeader(Modifier.staggerIn(index = 0, offsetY = (-24).dp, fromScale = 1f)) {
                HeaderTop(onOpenStats = openStats, onOpenSettings = onOpenSettings)
                MixedTitle(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                )
                Quote(stringResource(R.string.home_tagline), Modifier.padding(top = 12.dp, end = 12.dp))
                if (budget != null) {
                    UnlockSection(
                        budget = budget,
                        state = unlock,
                        appNames = unlockActions.appNames,
                        onOpenUnlock = unlockActions.onOpen,
                        onEnd = unlockActions.onEnd,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                var order = 1
                summary.month?.let { month ->
                    SummaryReadyCard(
                        month,
                        onOpen = { summary.onOpen(month) },
                        modifier = Modifier.padding(top = 24.dp).staggerIn(order++),
                    )
                }
                if (budget != null) {
                    SectionTitle(stringResource(R.string.home_my_day), Modifier.staggerIn(order++))
                    TodayCard(budget = budget, modifier = Modifier.staggerIn(order++))
                }
                if (week.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.home_my_week), Modifier.staggerIn(order++))
                    WeekStrip(
                        week = week,
                        today = week.last().day,
                        onOpenStats = openStats,
                        modifier = Modifier.staggerIn(order++),
                    )
                }
                SectionTitle(stringResource(R.string.home_my_protection), Modifier.staggerIn(order++))
                Column(Modifier.staggerIn(order++)) {
                    val manualTime = budget?.autoTime == false
                    val xiaomi = showXiaomiTip && protection != ProtectionState.Off
                    ProtectionRow(
                        state = protection,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        divider = manualTime || xiaomi,
                    )
                    if (manualTime) {
                        StatusRow(
                            title = stringResource(R.string.manual_time_title),
                            detail = stringResource(R.string.manual_time_body),
                            divider = xiaomi,
                            leading = { PulsingDot(MaterialTheme.colorScheme.error) },
                            trailing = {
                                TextButton(onClick = onOpenDateSettings) {
                                    Text(stringResource(R.string.home_service_enable_short))
                                }
                            },
                        )
                    }
                    if (xiaomi) XiaomiTipRow(onOpenAppSettings = onOpenAppSettings, divider = false)
                }
                OutlinePillButton(
                    text = stringResource(R.string.home_open_stats),
                    onClick = openStats,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp).staggerIn(order),
                )
            }
        }
    }
}

/** Fecha de hoy a la izquierda; estadísticas y ajustes a la derecha. */
@Composable
private fun HeaderTop(onOpenStats: () -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = fullDay(LocalDate.now()),
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenStats) {
            Icon(painterResource(R.drawable.ic_stats), contentDescription = stringResource(R.string.stats_title))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
        }
    }
}

/** El lema como una cita: comillas grandes y texto suave, como la frase del diseño. */
@Composable
private fun Quote(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("“", fontSize = 40.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    }
}

/** El resumen del mes anterior está listo: se queda en Inicio hasta que se abre. */
@Composable
private fun SummaryReadyCard(month: YearMonth, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            MixedTitle(
                text = stringResource(R.string.home_summary_title, "*${monthName(month)}*"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            )
            Text(
                text = stringResource(R.string.home_summary_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            InkPillButton(
                text = stringResource(R.string.home_summary_open),
                onClick = onOpen,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

internal fun previewBudget(used: Int = 10, autoTime: Boolean = true) = BudgetSummary(
    limitMinutes = 30,
    usedMinutes = used,
    remainingMinutes = 30 - used,
    pendingLimitMinutes = null,
    pendingFrom = null,
    autoTime = autoTime,
    nextResetMs = System.currentTimeMillis() + 3_600_000,
    onboardingDone = true,
    unlock = null,
    unlockRemainingMs = 0,
    refundMinutes = 0,
)

private val noActions = UnlockActions(emptyMap(), {}, {})

private val noSummary = SummaryActions(null) {}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme {
        HomeContent(
            ProtectionState.Running,
            previewBudget(),
            previewWeek(),
            UnlockUiState(),
            noActions,
            true,
            noSummary,
            {},
            {},
            {},
            {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeStoppedPreview() {
    TouchGrassTheme {
        HomeContent(
            ProtectionState.Stopped,
            previewBudget(used = 30, autoTime = false),
            previewWeek(),
            UnlockUiState(),
            noActions,
            false,
            SummaryActions(YearMonth.of(2026, 9)) {},
            {},
            {},
            {},
            {},
        )
    }
}
