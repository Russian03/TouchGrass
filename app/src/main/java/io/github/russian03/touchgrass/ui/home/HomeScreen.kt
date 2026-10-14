package io.github.russian03.touchgrass.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.ManualTimeCard
import io.github.russian03.touchgrass.ui.common.PrimaryButton
import io.github.russian03.touchgrass.ui.common.ProtectionCard
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.TopBar
import io.github.russian03.touchgrass.ui.common.XiaomiTip
import io.github.russian03.touchgrass.ui.common.monthName
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
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
    HomeContent(
        protection = rememberProtectionState(),
        budget = budget,
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

@Composable
private fun HomeContent(
    protection: ProtectionState,
    budget: BudgetSummary?,
    unlock: UnlockUiState,
    unlockActions: UnlockActions,
    showXiaomiTip: Boolean,
    summary: SummaryActions,
    onOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDateSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                action = {
                    Row {
                        IconButton(onClick = { summary.onOpen(null) }) {
                            Icon(
                                painterResource(R.drawable.ic_stats),
                                contentDescription = stringResource(R.string.stats_title),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            summary.month?.let { month ->
                SummaryReadyCard(month, onOpen = { summary.onOpen(month) }, modifier = Modifier.padding(top = 32.dp))
            }
            if (budget != null) {
                TodayCard(
                    budget = budget,
                    modifier = Modifier.padding(
                        top = if (summary.month !=
                            null
                        ) {
                            16.dp
                        } else {
                            32.dp
                        },
                    ),
                )
                UnlockCard(
                    budget = budget,
                    state = unlock,
                    appNames = unlockActions.appNames,
                    onOpenUnlock = unlockActions.onOpen,
                    onEnd = unlockActions.onEnd,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (!budget.autoTime) {
                    ManualTimeCard(onOpenDateSettings = onOpenDateSettings, modifier = Modifier.padding(top = 16.dp))
                }
            }
            ProtectionCard(
                state = protection,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (showXiaomiTip && protection != ProtectionState.Off) {
                XiaomiTip(onOpenAppSettings = onOpenAppSettings, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

/** El resumen del mes anterior está listo: se queda en Inicio hasta que se abre. */
@Composable
private fun SummaryReadyCard(month: YearMonth, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Text(
            text = stringResource(R.string.home_summary_title, monthName(month)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.home_summary_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.home_summary_open),
            onClick = onOpen,
            modifier = Modifier.padding(top = 16.dp),
        )
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

@Preview(showBackground = true)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme {
        HomeContent(ProtectionState.Running, previewBudget(), UnlockUiState(), noActions, true, noSummary, {
        }, {}, {}, {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeStoppedPreview() {
    TouchGrassTheme {
        HomeContent(
            ProtectionState.Stopped,
            previewBudget(used = 30, autoTime = false),
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
