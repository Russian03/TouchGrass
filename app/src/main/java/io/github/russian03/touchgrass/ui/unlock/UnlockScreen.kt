package io.github.russian03.touchgrass.ui.unlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.MinutesPicker
import io.github.russian03.touchgrass.ui.common.PrimaryButton
import io.github.russian03.touchgrass.ui.common.TopBar
import io.github.russian03.touchgrass.ui.common.reasonLabel
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

private val UNLOCK_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Pedir tiempo: primero se elige la app y después los minutos, nunca más de los que quedan
 * hoy. Con [preselected] (al venir de la pantalla de bloqueo) se entra directamente en los
 * minutos de esa app. [onDone] se llama cuando el desbloqueo ya está concedido.
 */
@Composable
fun UnlockScreen(
    preselected: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(preselected) { preselected?.let(viewModel::choose) }
    LaunchedEffect(form.granted) { if (form.granted) onDone() }
    // Atrás en los minutos vuelve a la lista de apps, si hay más de una.
    BackHandler(enabled = form.selected != null && form.canChooseApp) { viewModel.backToApps() }
    UnlockContent(
        budget = budget,
        form = form,
        onBack = { if (!viewModel.backToApps()) onBack() },
        onChoose = viewModel::choose,
        onChangeApp = { viewModel.backToApps() },
        onDraftChange = viewModel::onDraftChange,
        onReasonChange = viewModel::onReasonChange,
        onNoteChange = viewModel::onNoteChange,
        onRequest = viewModel::request,
    )
}

@Composable
private fun UnlockContent(
    budget: BudgetSummary?,
    form: UnlockFormState,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onChangeApp: () -> Unit,
    onDraftChange: (Int) -> Unit,
    onReasonChange: (UnlockReason) -> Unit,
    onNoteChange: (String) -> Unit,
    onRequest: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(title = stringResource(R.string.unlock_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            if (budget == null || form.apps == null) return@Column
            val blocker =
                when {
                    // Un desbloqueo a la vez: el que hay en marcha se termina desde Inicio.
                    budget.unlock != null && !form.granted -> stringResource(R.string.unlock_already)
                    !budget.autoTime -> stringResource(R.string.unlock_manual_time)
                    budget.remainingMinutes == 0 -> stringResource(
                        R.string.unlock_none_left,
                        timeOf(budget.nextResetMs),
                    )
                    else -> null
                }
            when {
                blocker != null -> Card { Body(blocker) }
                form.selected == null -> ChooseApp(budget, form.apps, onChoose)
                else -> ChooseMinutes(
                    budget,
                    form,
                    onChangeApp,
                    onDraftChange,
                    ReasonActions(onReasonChange, onNoteChange),
                    onRequest,
                )
            }
        }
    }
}

@Composable
private fun ChooseApp(budget: BudgetSummary, apps: List<UnlockApp>, onChoose: (String) -> Unit) {
    Text(
        text = stringResource(R.string.unlock_choose_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
    )
    Body(
        text =
        stringResource(R.string.unlock_choose_body) + " " +
            pluralStringResource(R.plurals.minutes_left, budget.remainingMinutes, budget.remainingMinutes) + ".",
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
    )
    apps.forEach { app ->
        Surface(
            onClick = { onChoose(app.packageName) },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (app.blocks.isNotEmpty()) Body(app.blocks, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ChooseMinutes(
    budget: BudgetSummary,
    form: UnlockFormState,
    onChangeApp: () -> Unit,
    onDraftChange: (Int) -> Unit,
    reasonActions: ReasonActions,
    onRequest: () -> Unit,
) {
    val app = form.selected ?: return
    Card {
        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Body(
            text =
            if (app.blocks.isNotEmpty()) {
                stringResource(R.string.unlock_app_body, app.blocks)
            } else {
                stringResource(R.string.unlock_app_body_generic)
            },
            modifier = Modifier.padding(top = 6.dp),
        )
        val minutes = form.shown(budget.remainingMinutes)
        MinutesPicker(
            minutes = minutes,
            range = 1..budget.remainingMinutes,
            presets = UNLOCK_PRESETS,
            unit = stringResource(R.string.unlock_unit),
            description = pluralStringResource(R.plurals.minutes, minutes, minutes),
            onChange = onDraftChange,
            modifier = Modifier.padding(top = 20.dp),
        )
        ReasonPicker(form, reasonActions, Modifier.padding(top = 24.dp))
        PrimaryButton(
            text = stringResource(R.string.unlock_request, minutes),
            onClick = onRequest,
            enabled = !form.busy && form.reasonReady,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (!form.reasonReady) {
            Text(
                text =
                stringResource(
                    if (form.reason ==
                        UnlockReason.Other
                    ) {
                        R.string.unlock_reason_note_missing
                    } else {
                        R.string.unlock_reason_missing
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        form.error?.let {
            Text(
                text =
                stringResource(
                    when (it) {
                        Denial.NotEnoughBudget -> R.string.unlock_error_not_enough
                        Denial.AlreadyUnlocked -> R.string.unlock_already
                        else -> R.string.unlock_error
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (form.canChooseApp) {
        TextButton(onClick = onChangeApp, enabled = !form.busy, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.unlock_change_app))
        }
    }
}

private class ReasonActions(val onReason: (UnlockReason) -> Unit, val onNote: (String) -> Unit)

/** «¿Para qué?»: un motivo obligatorio y, con «Otro», un texto corto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonPicker(form: UnlockFormState, actions: ReasonActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.unlock_reason_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            UnlockReason.entries.forEach { reason ->
                FilterChip(
                    selected = form.reason == reason,
                    onClick = { actions.onReason(reason) },
                    enabled = !form.busy,
                    label = { Text(stringResource(reasonLabel(reason))) },
                )
            }
        }
        if (form.reason == UnlockReason.Other) {
            OutlinedTextField(
                value = form.note,
                onValueChange = actions.onNote,
                enabled = !form.busy,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.unlock_reason_note_hint)) },
                supportingText = { Text("${form.note.length}/${StatsPolicy.NOTE_MAX_LENGTH}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
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

private val previewApps =
    listOf(
        UnlockApp("com.instagram.android", "Instagram", "Reels"),
        UnlockApp("com.zhiliaoapp.musically", "TikTok", "Para ti y Comunidad"),
    )

private fun previewBudget() = BudgetSummary(
    limitMinutes = 30,
    usedMinutes = 10,
    remainingMinutes = 20,
    pendingLimitMinutes = null,
    pendingFrom = null,
    autoTime = true,
    nextResetMs = System.currentTimeMillis() + 3_600_000,
    onboardingDone = true,
    unlock = null,
    unlockRemainingMs = 0,
    refundMinutes = 0,
)

@Preview(showBackground = true)
@Composable
private fun ChooseAppPreview() {
    TouchGrassTheme { UnlockContent(previewBudget(), UnlockFormState(apps = previewApps), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun ChooseMinutesPreview() {
    val form = UnlockFormState(apps = previewApps, selected = previewApps.first(), reason = UnlockReason.Other)
    TouchGrassTheme { UnlockContent(previewBudget(), form, {}, {}, {}, {}, {}, {}, {}) }
}
