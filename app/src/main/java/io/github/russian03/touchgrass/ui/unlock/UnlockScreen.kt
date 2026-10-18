package io.github.russian03.touchgrass.ui.unlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.ui.common.BeigeCard
import io.github.russian03.touchgrass.ui.common.MinutesPicker
import io.github.russian03.touchgrass.ui.common.beigeCardColor
import io.github.russian03.touchgrass.ui.common.duration
import io.github.russian03.touchgrass.ui.common.reasonLabel
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.SectionTitle
import io.github.russian03.touchgrass.ui.design.Sprout
import io.github.russian03.touchgrass.ui.design.SproutMood
import io.github.russian03.touchgrass.ui.design.pressScale
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

private val UNLOCK_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Pedir tiempo: primero se elige la app y después los minutos (nunca más de los que quedan hoy)
 * y para qué. [onDone] se llama cuando el desbloqueo ya está concedido.
 */
@Composable
fun UnlockScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: UnlockViewModel = hiltViewModel()) {
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            LimeHeader {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                MixedTitle(
                    text = stringResource(R.string.unlock_header),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
                if (budget != null) {
                    Text(
                        text = stringResource(R.string.time_left, duration(budget.remainingMinutes)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
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
                    blocker != null -> Blocked(blocker)
                    form.selected == null -> ChooseApp(form.apps, onChoose)
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
}

/** Hoy no se puede pedir tiempo: el personaje preocupado y el porqué. */
@Composable
private fun Blocked(text: String) {
    BeigeCard(Modifier.padding(top = 4.dp).staggerIn(1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sprout(SproutMood.Worried, Modifier.width(56.dp), faceColor = beigeCardColor())
            Body(text, Modifier.padding(start = 20.dp).weight(1f))
        }
    }
}

@Composable
private fun ChooseApp(apps: List<UnlockApp>, onChoose: (String) -> Unit) {
    SectionTitle(stringResource(R.string.unlock_choose_title), Modifier.staggerIn(1), top = 4.dp)
    Body(stringResource(R.string.unlock_choose_body), Modifier.padding(bottom = 4.dp).staggerIn(1))
    apps.forEachIndexed { i, app ->
        AppCard(app, onClick = { onChoose(app.packageName) }, modifier = Modifier.padding(top = 12.dp).staggerIn(2 + i))
    }
}

/** Una app con su nombre y lo que se le bloquea; encoge un poco al pulsarla. */
@Composable
private fun AppCard(app: UnlockApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        color = beigeCardColor(),
        shape = MaterialTheme.shapes.large,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                if (app.blocks.isNotEmpty()) Body(app.blocks, Modifier.padding(top = 2.dp))
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
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
    val minutes = form.shown(budget.remainingMinutes)
    SectionTitle(stringResource(R.string.unlock_minutes_section), Modifier.staggerIn(1), top = 4.dp)
    BeigeCard(Modifier.staggerIn(2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (form.canChooseApp) {
                TextButton(onClick = onChangeApp, enabled = !form.busy) {
                    Text(stringResource(R.string.unlock_change_app))
                }
            }
        }
        Body(
            text =
            if (app.blocks.isNotEmpty()) {
                stringResource(R.string.unlock_app_body, app.blocks)
            } else {
                stringResource(R.string.unlock_app_body_generic)
            },
            modifier = Modifier.padding(top = 4.dp),
        )
        MinutesPicker(
            minutes = minutes,
            range = 1..budget.remainingMinutes,
            presets = UNLOCK_PRESETS,
            unit = stringResource(R.string.unlock_unit),
            description = duration(minutes),
            onChange = onDraftChange,
            modifier = Modifier.padding(top = 20.dp),
        )
    }

    SectionTitle(stringResource(R.string.unlock_reason_title), Modifier.staggerIn(3))
    BeigeCard(Modifier.staggerIn(4)) {
        ReasonPicker(form, reasonActions)
        ChunkyPillButton(
            text = stringResource(R.string.unlock_request, duration(minutes)),
            onClick = onRequest,
            enabled = !form.busy && form.reasonReady,
            modifier = Modifier.padding(top = 24.dp),
        )
        val hint =
            when {
                form.error != null -> null
                form.reasonReady -> null
                form.reason == UnlockReason.Other -> R.string.unlock_reason_note_missing
                else -> R.string.unlock_reason_missing
            }
        hint?.let { Body(stringResource(it), Modifier.padding(top = 12.dp)) }
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
}

private class ReasonActions(val onReason: (UnlockReason) -> Unit, val onNote: (String) -> Unit)

/** Un motivo obligatorio, con las píldoras de los atajos de minutos, y con «Otro», un texto corto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonPicker(form: UnlockFormState, actions: ReasonActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnlockReason.entries.forEach { reason ->
            FilterChip(
                selected = form.reason == reason,
                onClick = { actions.onReason(reason) },
                enabled = !form.busy,
                label = { Text(stringResource(reasonLabel(reason))) },
                shape = CircleShape,
                colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
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
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
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
private fun BlockedPreview() {
    TouchGrassTheme {
        UnlockContent(previewBudget().copy(remainingMinutes = 0), UnlockFormState(apps = previewApps), {
        }, {}, {}, {}, {}, {}, {})
    }
}

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
