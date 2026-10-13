package io.github.russian03.touchgrass.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Límites que se pueden elegir desde la app. */
val LIMIT_RANGE = 0..120
private const val LIMIT_STEP = 5
private val LIMIT_PRESETS = listOf(15, 30, 45, 60, 90)

/** Selector del límite diario: número grande con − y +, y atajos debajo. */
@Composable
fun LimitPicker(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.minutes_per_day, minutes, minutes)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                symbol = "−",
                description = stringResource(R.string.limit_less),
                enabled = minutes > LIMIT_RANGE.first,
                onClick = { onChange((minutes - LIMIT_STEP).coerceIn(LIMIT_RANGE)) },
            )
            Text(
                text = minutes.toString(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(140.dp).semantics { contentDescription = label },
            )
            StepButton(
                symbol = "+",
                description = stringResource(R.string.limit_more),
                enabled = minutes < LIMIT_RANGE.last,
                onClick = { onChange((minutes + LIMIT_STEP).coerceIn(LIMIT_RANGE)) },
            )
        }
        Text(
            text = stringResource(R.string.limit_unit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            LIMIT_PRESETS.forEach { preset ->
                FilterChip(
                    selected = preset == minutes,
                    onClick = { onChange(preset) },
                    label = { Text(stringResource(R.string.minutes_short, preset)) },
                )
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(52.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = symbol, fontSize = 26.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LimitPickerPreview() {
    TouchGrassTheme { LimitPicker(minutes = 30, onChange = {}) }
}
