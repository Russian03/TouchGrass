package io.github.russian03.touchgrass.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.budget.UnlockSession
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.MinutesPicker
import io.github.russian03.touchgrass.ui.common.PrimaryButton
import io.github.russian03.touchgrass.ui.common.countdown
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

private val UNLOCK_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Pedir tiempo: con un desbloqueo en marcha muestra la cuenta atrás y deja terminarlo; si no,
 * deja elegir los minutos, nunca más de los que quedan hoy.
 */
@Composable
fun UnlockCard(
    budget: BudgetSummary,
    state: UnlockUiState,
    onDraftChange: (Int) -> Unit,
    onRequest: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        if (budget.unlock != null) {
            ActiveUnlock(budget, busy = state.busy, onEnd = onEnd)
        } else {
            RequestUnlock(budget, state, onDraftChange, onRequest)
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

@Composable
private fun RequestUnlock(
    budget: BudgetSummary,
    state: UnlockUiState,
    onDraftChange: (Int) -> Unit,
    onRequest: () -> Unit,
) {
    Text(
        text = stringResource(R.string.unlock_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
    )
    val blocker =
        when {
            !budget.autoTime -> stringResource(R.string.unlock_manual_time)
            budget.remainingMinutes == 0 -> stringResource(R.string.unlock_none_left, timeOf(budget.nextResetMs))
            else -> null
        }
    Text(
        text = blocker ?: stringResource(R.string.unlock_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (blocker != null) return

    val minutes = state.shown(budget.remainingMinutes)
    MinutesPicker(
        minutes = minutes,
        range = 1..budget.remainingMinutes,
        presets = UNLOCK_PRESETS,
        unit = stringResource(R.string.unlock_unit),
        description = pluralStringResource(R.plurals.minutes, minutes, minutes),
        onChange = onDraftChange,
        modifier = Modifier.padding(top = 20.dp),
    )
    PrimaryButton(
        text = stringResource(R.string.unlock_request, minutes),
        onClick = onRequest,
        enabled = !state.busy,
        modifier = Modifier.padding(top = 20.dp),
    )
}

@Composable
private fun ActiveUnlock(budget: BudgetSummary, busy: Boolean, onEnd: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.unlock_active_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = countdown(budget.unlockRemainingMs),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = stringResource(
            R.string.unlock_active_until,
            timeOf(System.currentTimeMillis() + budget.unlockRemainingMs),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { confirm = true },
        enabled = !busy,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.unlock_end))
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

@Preview(showBackground = true)
@Composable
private fun UnlockRequestPreview() {
    TouchGrassTheme { UnlockCard(previewBudget(used = 17), UnlockUiState(), {}, {}, {}, Modifier.padding(16.dp)) }
}

@Preview(showBackground = true)
@Composable
private fun UnlockActivePreview() {
    val budget =
        previewBudget().copy(
            unlock = UnlockSession("com.instagram.android", 15, 0, 0, 1, "2026-10-13"),
            unlockRemainingMs = 13 * 60_000L + 20_000,
            refundMinutes = 10,
        )
    TouchGrassTheme { UnlockCard(budget, UnlockUiState(), {}, {}, {}, Modifier.padding(16.dp)) }
}
