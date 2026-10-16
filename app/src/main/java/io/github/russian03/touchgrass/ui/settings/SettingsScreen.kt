package io.github.russian03.touchgrass.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.BuildConfig
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.PrimaryButton
import io.github.russian03.touchgrass.ui.common.ProtectionCard
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.TopBar
import io.github.russian03.touchgrass.ui.common.firstDayOf
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.home.previewBudget
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.YearMonth

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    focusProtection: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsContent(
        state = state,
        protection = rememberProtectionState(),
        focusProtection = focusProtection,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onSaveName = viewModel::saveName,
        onDraftChange = viewModel::onDraftChange,
        onSave = viewModel::save,
        onCancelPending = viewModel::cancelPending,
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    protection: ProtectionState,
    focusProtection: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onDraftChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancelPending: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var confirmLower by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // Altura del apartado de la protección, para abrir Ajustes ya ahí.
    var protectionTop by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusProtection, protectionTop) {
        if (focusProtection && protectionTop >= 0) scroll.animateScrollTo(protectionTop)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scroll)
                .padding(24.dp),
        ) {
            Section(stringResource(R.string.settings_name_section))
            NameCard(state, onNameChange, onSaveName)

            Section(stringResource(R.string.settings_limit_section), Modifier.padding(top = 32.dp))
            LimitCard(
                state = state,
                onDraftChange = onDraftChange,
                onSave = { if (state.lowers) confirmLower = true else onSave() },
                onCancelPending = onCancelPending,
            )

            Section(
                stringResource(R.string.settings_protection_section),
                Modifier.padding(top = 32.dp).onPlaced { protectionTop = it.positionInParent().y.toInt() },
            )
            ProtectionCard(state = protection, onOpenAccessibilitySettings = onOpenAccessibilitySettings)

            Section(stringResource(R.string.settings_privacy_section), Modifier.padding(top = 32.dp))
            Card {
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
        }
    }

    if (confirmLower) {
        AlertDialog(
            onDismissRequest = { confirmLower = false },
            title = { Text(stringResource(R.string.settings_lower_title, state.shownLimit)) },
            text = { Text(stringResource(R.string.settings_lower_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLower = false
                    onSave()
                }) { Text(stringResource(R.string.settings_lower_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLower = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Nombre del saludo de Inicio: se escribe y se guarda; vacío lo quita. */
@Composable
private fun NameCard(state: SettingsUiState, onNameChange: (String) -> Unit, onSave: () -> Unit) {
    Card {
        OutlinedTextField(
            value = state.shownName,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
            keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canSaveName) onSave() }),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_name_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.settings_name_save),
            onClick = onSave,
            enabled = state.canSaveName,
            modifier = Modifier.padding(top = 16.dp),
        )
        state.nameSaved?.let { ok ->
            Text(
                text = stringResource(if (ok) R.string.settings_name_saved else R.string.error_saving),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun LimitCard(
    state: SettingsUiState,
    onDraftChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancelPending: () -> Unit,
) {
    val budget = state.budget ?: return
    val nextMonth = firstDayOf(YearMonth.now().plusMonths(1))
    Card {
        Text(
            text = stringResource(R.string.settings_limit_current, budget.limitMinutes),
            style = MaterialTheme.typography.bodyLarge,
        )
        LimitPicker(
            minutes = state.shownLimit,
            onChange = onDraftChange,
            modifier = Modifier.padding(top = 20.dp),
        )
        val hint =
            when {
                state.draft == null || state.draft == budget.limitMinutes -> stringResource(
                    R.string.settings_limit_rule,
                )
                state.lowers -> stringResource(R.string.settings_limit_applies_now)
                else -> stringResource(R.string.settings_limit_applies_from, nextMonth)
            }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        state.result?.let { result ->
            val colors = MaterialTheme.colorScheme
            Text(
                // Al programar un cambio, la fecha buena es la que ha guardado la política.
                text = resultText(result, budget.pendingFrom?.let(::firstDayOf) ?: nextMonth),
                style = MaterialTheme.typography.bodySmall,
                color = if (result is LimitChange.Failed) colors.error else colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.settings_limit_save),
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.padding(top = 16.dp),
        )
        val pending = budget.pendingLimitMinutes
        val from = budget.pendingFrom
        if (pending != null && from != null) {
            Text(
                text = stringResource(R.string.limit_pending, firstDayOf(from), pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp),
            )
            TextButton(onClick = onCancelPending, enabled = !state.saving) {
                Text(stringResource(R.string.settings_limit_cancel_pending))
            }
        }
    }
}

@Composable
private fun resultText(result: LimitChange, nextMonth: String): String = when (result) {
    LimitChange.Applied -> stringResource(R.string.settings_limit_applied)
    LimitChange.Scheduled -> stringResource(R.string.settings_limit_scheduled, nextMonth)
    LimitChange.Cancelled -> stringResource(R.string.settings_limit_cancelled)
    is LimitChange.Failed ->
        when (result.reason) {
            Denial.ManualTime -> stringResource(R.string.settings_limit_manual_time)
            else -> stringResource(R.string.error_saving)
        }
}

@Preview(showSystemUi = true)
@Composable
private fun SettingsPreview() {
    TouchGrassTheme {
        SettingsContent(
            state = SettingsUiState(budget = previewBudget(), draft = 45),
            protection = ProtectionState.Running,
            focusProtection = false,
            onBack = {},
            onNameChange = {},
            onSaveName = {},
            onDraftChange = {},
            onSave = {},
            onCancelPending = {},
            onOpenAccessibilitySettings = {},
        )
    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPendingPreview() {
    TouchGrassTheme {
        SettingsContent(
            state = SettingsUiState(
                budget = previewBudget().copy(
                    pendingLimitMinutes = BudgetPolicy.MAX_LIMIT_MINUTES / 2,
                    pendingFrom = YearMonth.now().plusMonths(1),
                ),
            ),
            protection = ProtectionState.Running,
            focusProtection = false,
            onBack = {},
            onNameChange = {},
            onSaveName = {},
            onDraftChange = {},
            onSave = {},
            onCancelPending = {},
            onOpenAccessibilitySettings = {},
        )
    }
}
