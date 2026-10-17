package io.github.russian03.touchgrass.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.BeigeCard
import io.github.russian03.touchgrass.ui.common.beigeCardColor
import io.github.russian03.touchgrass.ui.common.duration
import io.github.russian03.touchgrass.ui.common.firstDayOf
import io.github.russian03.touchgrass.ui.common.isTomorrow
import io.github.russian03.touchgrass.ui.common.timeOf
import io.github.russian03.touchgrass.ui.design.FitText
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.Sprout
import io.github.russian03.touchgrass.ui.design.SproutMood
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Por debajo de esta fracción del saldo, la onda del anillo se aplana: queda poco. */
private const val CALM_BELOW = 0.2f

/**
 * «Mi día»: un anillo ondulado con lo que queda y cuándo se renueva. Si la protección no funciona
 * ([protection], ya con el margen de los cortes), el anillo pasa a tinta y sin onda, en lugar de los
 * minutos sale el personaje en tinta y a la derecha se avisa, con el botón para arreglarlo en
 * Ajustes ([onFixProtection]). Quitada en Accesibilidad, con los ojos en cruz; en un corte que
 * dura, preocupado.
 */
@Composable
fun TodayCard(
    budget: BudgetSummary,
    protection: ProtectionState,
    onFixProtection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingLabel = stringResource(R.string.time_left, duration(budget.remainingMinutes))
    val offLabel = stringResource(protection.warningTitle())
    BeigeCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BudgetRing(
                remaining = budget.remainingMinutes,
                limit = budget.limitMinutes,
                protection = protection,
                modifier =
                Modifier.semantics {
                    contentDescription = if (protection == ProtectionState.Running) remainingLabel else offLabel
                },
            )
            AnimatedContent(
                targetState = protection,
                transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                label = "todayText",
                modifier = Modifier.padding(start = 20.dp),
            ) { shown ->
                if (shown == ProtectionState.Running) ResetText(budget) else ProtectionOffText(shown, onFixProtection)
            }
        }
        val pending = budget.pendingLimitMinutes
        val from = budget.pendingFrom
        if (pending != null && from != null) {
            Text(
                text = stringResource(R.string.limit_pending, firstDayOf(from), duration(pending)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private const val FADE_MS = 300

/** Ancho del texto del centro del anillo, para que no toque el trazo. */
private val RING_TEXT_WIDTH = 88.dp

/** Título del aviso: le faltan permisos (quitada) o se ha detenido (corte que dura). */
@StringRes
private fun ProtectionState.warningTitle(): Int =
    if (this == ProtectionState.Off) R.string.today_protection_off_title else R.string.today_protection_stopped_title

/** De cuánto es el límite y cuándo se renueva. */
@Composable
private fun ResetText(budget: BudgetSummary) {
    val reset = timeOf(budget.nextResetMs)
    Column {
        Text(
            text = stringResource(R.string.today_of_limit, duration(budget.limitMinutes)),
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

/** Aviso de que la protección no funciona ([protection]) y el botón que lleva a su apartado de Ajustes. */
@Composable
private fun ProtectionOffText(protection: ProtectionState, onFixProtection: () -> Unit) {
    Column {
        Text(
            text = stringResource(protection.warningTitle()),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
            stringResource(
                if (protection == ProtectionState.Off) {
                    R.string.today_protection_off_body
                } else {
                    R.string.today_protection_stopped_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        InkPillButton(
            text = stringResource(R.string.today_protection_off_action),
            onClick = onFixProtection,
            compact = true,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Anillo ondulado que se llena desde 0 al entrar y sigue al saldo con un muelle lento (unos
 * 800 ms). Cuando queda menos del 20 %, la onda se aplana. Sin protección, pasa a tinta y sin onda,
 * y en el centro sale el personaje en tinta con los ojos en cruz.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BudgetRing(remaining: Int, limit: Int, protection: ProtectionState, modifier: Modifier = Modifier) {
    val protected = protection == ProtectionState.Running
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
    // Sin protección, el anillo y el personaje van en tinta.
    val off = MaterialTheme.colorScheme.onSurface
    val color by animateColorAsState(
        targetValue = if (protected) MaterialTheme.colorScheme.secondary else off,
        animationSpec = tween(FADE_MS),
        label = "ringColor",
    )
    val stroke = with(LocalDensity.current) { Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round) }
    Box(modifier.size(124.dp), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { fill.value.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { progress -> if (!protected || progress < CALM_BELOW) 0f else 1f },
            modifier = Modifier.size(124.dp),
        )
        AnimatedContent(
            targetState = protection,
            transitionSpec = {
                (fadeIn(tween(FADE_MS)) + scaleIn(tween(FADE_MS), initialScale = 0.8f)) togetherWith
                    fadeOut(tween(FADE_MS))
            },
            label = "ringCentre",
            modifier = Modifier.clearAndSetSemantics { },
        ) { shown ->
            if (shown == ProtectionState.Running) {
                // Menos de 1 h: «45» y «min» debajo. Desde 1 h: «3 h» y los minutos que sobran debajo.
                val hours = remaining / 60
                val rest = remaining % 60
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = RING_TEXT_WIDTH),
                ) {
                    FitText(
                        text = if (hours == 0) remaining.toString() else "$hours h",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    )
                    if (hours == 0 || rest > 0) {
                        Text(
                            if (hours == 0) stringResource(R.string.minutes_unit) else duration(rest),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Sprout(
                    mood = if (shown == ProtectionState.Off) SproutMood.Off else SproutMood.Worried,
                    bodyColor = off,
                    faceColor = beigeCardColor(),
                    modifier = Modifier.width(62.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun TodayCardPreview() {
    TouchGrassTheme {
        TodayCard(previewBudget(), ProtectionState.Running, onFixProtection = {}, Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun TodayCardOffPreview() {
    TouchGrassTheme { TodayCard(previewBudget(), ProtectionState.Off, onFixProtection = {}, Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun TodayCardStoppedPreview() {
    TouchGrassTheme {
        TodayCard(previewBudget(), ProtectionState.Stopped, onFixProtection = {}, Modifier.padding(16.dp))
    }
}
