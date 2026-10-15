package io.github.russian03.touchgrass.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.budget.UnlockSession
import io.github.russian03.touchgrass.ui.common.countdown
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/**
 * Parte de la cabecera para pedir tiempo. Sin desbloqueo solo dice por qué no se puede pedir, si
 * es el caso (la píldora «Pedir tiempo» va arriba, junto a las comillas). Con un desbloqueo en
 * marcha, la cabecera cambia de contenido y de alto: cuenta atrás con dígitos que ruedan, una onda
 * que se vacía y «Terminar ya».
 */
@Composable
fun UnlockSection(
    budget: BudgetSummary,
    state: UnlockUiState,
    appNames: Map<String, String>,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Column(modifier) {
        AnimatedContent(
            targetState = budget.unlock,
            contentKey = { it != null },
            transitionSpec = {
                (fadeIn(enter) + slideInVertically { it / 3 }) togetherWith fadeOut(exit)
            },
            label = "unlockHeader",
        ) { unlock ->
            if (unlock != null) {
                ActiveUnlock(budget, unlock, appNames[unlock.packageName] ?: unlock.packageName, state.busy, onEnd)
            } else {
                RequestUnlock(budget)
            }
        }
        state.error?.let {
            Text(
                text =
                stringResource(
                    if (it == Denial.NotEnoughBudget) R.string.unlock_error_not_enough else R.string.unlock_error,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Por qué no se puede pedir tiempo ahora (hora manual o saldo agotado), o `null` si se puede. */
@Composable
fun unlockBlocker(budget: BudgetSummary): String? = when {
    !budget.autoTime -> stringResource(R.string.unlock_manual_time)
    budget.remainingMinutes == 0 -> stringResource(R.string.unlock_none_left, timeOf(budget.nextResetMs))
    else -> null
}

/** Sin desbloqueo, la píldora está arriba (junto a las comillas); aquí solo va el motivo si no se puede pedir. */
@Composable
private fun RequestUnlock(budget: BudgetSummary) {
    val blocker = unlockBlocker(budget) ?: return
    Text(
        text = blocker,
        style = MaterialTheme.typography.bodySmall,
        color = InkSoft,
        modifier = Modifier.padding(top = 12.dp, end = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveUnlock(
    budget: BudgetSummary,
    unlock: UnlockSession,
    appName: String,
    busy: Boolean,
    onEnd: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val totalMs = unlock.minutes * 60_000L
    val left = countdown(budget.unlockRemainingMs)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, end = 12.dp)) {
        Text(
            text = stringResource(R.string.unlock_active_title, appName),
            style = MaterialTheme.typography.titleMedium,
        )
        RollingText(
            text = left,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            description = stringResource(R.string.home_unlock_left, left),
            modifier = Modifier.padding(top = 4.dp),
        )
        LinearWavyProgressIndicator(
            progress = { if (totalMs > 0) (budget.unlockRemainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f },
            color = Ink,
            trackColor = Ink.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.unlock_active_until,
                    timeOf(
                        System.currentTimeMillis() + budget.unlockRemainingMs,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                modifier = Modifier.weight(1f),
            )
            InkPillButton(
                text = stringResource(R.string.unlock_end),
                onClick = { confirm = true },
                enabled = !busy,
                containerColor = Ink,
                contentColor = Cream,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.unlock_end_title)) },
            text = {
                Text(
                    if (budget.refundMinutes > 0) {
                        stringResource(R.string.unlock_end_refund, budget.refundMinutes)
                    } else {
                        stringResource(R.string.unlock_end_no_refund)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onEnd()
                }) { Text(stringResource(R.string.unlock_end_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * Texto cuyos caracteres ruedan al cambiar: el nuevo entra desde arriba y el viejo sale por
 * abajo, como un contador mecánico. Las posiciones se cuentan desde la derecha para que los
 * segundos sigan siendo los segundos aunque cambie el largo («10:00» → «9:59»).
 */
@Composable
fun RollingText(text: String, style: TextStyle, description: String, modifier: Modifier = Modifier) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(modifier.clearAndSetSemantics { contentDescription = description }) {
        text.forEachIndexed { i, char ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(spatial) { -it } + fadeIn(effects)) togetherWith
                            (slideOutVertically(spatial) { it } + fadeOut(effects))
                    },
                    label = "rolling",
                ) { c -> Text(c.toString(), style = style) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RequestPreview() {
    TouchGrassTheme {
        LimeHeader { UnlockSection(previewBudget(used = 30), UnlockUiState(), emptyMap(), {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActivePreview() {
    val budget =
        previewBudget().copy(
            unlock = UnlockSession("com.instagram.android", 15, 0, 0, 1, "2026-10-13"),
            unlockRemainingMs = 13 * 60_000L + 20_000,
            refundMinutes = 10,
        )
    TouchGrassTheme {
        LimeHeader {
            UnlockSection(budget, UnlockUiState(), mapOf("com.instagram.android" to "Instagram"), {})
        }
    }
}
