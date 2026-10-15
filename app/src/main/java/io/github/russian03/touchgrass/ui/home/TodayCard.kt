package io.github.russian03.touchgrass.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
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

/** Por debajo de esta fracción del saldo, la onda del anillo se aplana: queda poco. */
private const val CALM_BELOW = 0.2f

/** «Mi día»: un anillo ondulado con lo que queda y cuándo se renueva. */
@Composable
fun TodayCard(budget: BudgetSummary, modifier: Modifier = Modifier) {
    val remainingLabel = pluralStringResource(R.plurals.minutes_left, budget.remainingMinutes, budget.remainingMinutes)
    val reset = timeOf(budget.nextResetMs)
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(
                remaining = budget.remainingMinutes,
                limit = budget.limitMinutes,
                modifier = Modifier.semantics { contentDescription = remainingLabel },
            )
            Column(Modifier.padding(start = 20.dp)) {
                Text(
                    text = stringResource(R.string.today_of_limit, budget.limitMinutes),
                    style = MaterialTheme.typography.titleMedium,
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
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Anillo ondulado que se llena desde 0 al entrar y sigue al saldo con un muelle lento (unos
 * 800 ms). Cuando queda menos del 20 %, la onda se aplana.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BudgetRing(remaining: Int, limit: Int, modifier: Modifier = Modifier) {
    val target = if (limit > 0) remaining.toFloat() / limit else 0f
    val fill = remember { Animatable(0f) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(target) {
        if (inspection) {
            fill.snapTo(target)
        } else {
            fill.animateTo(
                target,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
            )
        }
    }
    val stroke = with(LocalDensity.current) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) }
    Box(modifier.size(124.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { fill.value.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { progress -> if (progress < CALM_BELOW) 0f else 1f },
            modifier = Modifier.size(124.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(remaining.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.minutes_unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun TodayCardPreview() {
    TouchGrassTheme { TodayCard(previewBudget(), Modifier.padding(16.dp)) }
}
