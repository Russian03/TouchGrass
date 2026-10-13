package io.github.russian03.touchgrass.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.ui.common.Card
import io.github.russian03.touchgrass.ui.common.firstDayOf
import io.github.russian03.touchgrass.ui.common.isTomorrow
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Saldo de hoy: un anillo con lo que queda y cuándo se renueva. */
@Composable
fun TodayCard(budget: BudgetSummary, modifier: Modifier = Modifier) {
    val remainingLabel = pluralStringResource(R.plurals.minutes_left, budget.remainingMinutes, budget.remainingMinutes)
    val reset = timeOf(budget.nextResetMs)
    Card(modifier = modifier) {
        Text(
            text = stringResource(R.string.today_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            BudgetRing(
                remaining = budget.remainingMinutes,
                limit = budget.limitMinutes,
                modifier = Modifier.semantics { contentDescription = remainingLabel },
            )
            Column(Modifier.padding(start = 20.dp)) {
                Text(
                    text = stringResource(R.string.today_of_limit, budget.limitMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                    if (isTomorrow(budget.nextResetMs)) {
                        stringResource(R.string.today_resets_tomorrow, reset)
                    } else {
                        stringResource(R.string.today_resets_at, reset)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val pending = budget.pendingLimitMinutes
        val from = budget.pendingFrom
        if (pending != null && from != null) {
            Text(
                text = stringResource(R.string.limit_pending, firstDayOf(from), pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Anillo que se vacía a medida que se gastan minutos, con los que quedan en el centro. */
@Composable
private fun BudgetRing(remaining: Int, limit: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(if (limit > 0) remaining.toFloat() / limit else 0f, label = "ring")
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = size.copy(width = size.width - stroke.width, height = size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (fraction > 0f) {
                drawArc(
                    fill,
                    -90f,
                    360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(remaining.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
            Text(
                stringResource(R.string.minutes_unit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayCardPreview() {
    TouchGrassTheme { TodayCard(previewBudget(), Modifier.padding(16.dp)) }
}
