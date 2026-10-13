package io.github.russian03.touchgrass.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
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
import io.github.russian03.touchgrass.ui.common.ManualTimeCard
import io.github.russian03.touchgrass.ui.common.ProtectionCard
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.TopBar
import io.github.russian03.touchgrass.ui.common.XiaomiTip
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenUnlock: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val unlock by viewModel.unlock.collectAsStateWithLifecycle()
    val appNames by viewModel.appNames.collectAsStateWithLifecycle()
    HomeContent(
        protection = rememberProtectionState(),
        budget = budget,
        unlock = unlock,
        unlockActions = UnlockActions(appNames = appNames, onOpen = onOpenUnlock, onEnd = viewModel::endUnlock),
        showXiaomiTip = SystemSettings.isXiaomi,
        onOpenSettings = onOpenSettings,
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
        onOpenDateSettings = { SystemSettings.openDateTime(context) },
    )
}

private class UnlockActions(val appNames: Map<String, String>, val onOpen: () -> Unit, val onEnd: () -> Unit)

@Composable
private fun HomeContent(
    protection: ProtectionState,
    budget: BudgetSummary?,
    unlock: UnlockUiState,
    unlockActions: UnlockActions,
    showXiaomiTip: Boolean,
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
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
            if (budget != null) {
                TodayCard(budget = budget, modifier = Modifier.padding(top = 32.dp))
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

@Preview(showBackground = true)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme {
        HomeContent(ProtectionState.Running, previewBudget(), UnlockUiState(), noActions, true, {}, {}, {}, {})
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
            {},
            {},
            {},
            {},
        )
    }
}
