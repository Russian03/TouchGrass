package io.github.russian03.touchgrass.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.russian03.touchgrass.R

/** Aviso de que, con la hora manual, el saldo está congelado y no se puede desbloquear. */
@Composable
fun ManualTimeCard(onOpenDateSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Text(
            text = stringResource(R.string.manual_time_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.manual_time_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenDateSettings) { Text(stringResource(R.string.manual_time_open)) }
    }
}
