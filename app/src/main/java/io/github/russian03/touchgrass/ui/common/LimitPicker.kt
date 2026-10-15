package io.github.russian03.touchgrass.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.design.Sparkles
import io.github.russian03.touchgrass.ui.design.pressScale
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Límites que se pueden elegir desde la app. */
val LIMIT_RANGE = 0..120
private const val STEP = 5
private val LIMIT_PRESETS = listOf(15, 30, 45, 60, 90)

/** Selector del límite diario: número grande, − y + a los lados de la unidad y atajos debajo. */
@Composable
fun LimitPicker(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 72.sp,
    sparkles: Boolean = false,
) {
    MinutesPicker(
        minutes = minutes,
        range = LIMIT_RANGE,
        presets = LIMIT_PRESETS,
        unit = stringResource(R.string.limit_unit),
        description = pluralStringResource(R.plurals.minutes_per_day, minutes, minutes),
        onChange = onChange,
        modifier = modifier,
        numberSize = numberSize,
        sparkles = sparkles,
    )
}

/**
 * Número grande y, debajo, − y + a los lados de la unidad, con atajos al final. − y + saltan al
 * múltiplo de 5 anterior o siguiente (de 13 a 10 o a 15) sin salir de [range]; los atajos fuera
 * de [range] no se pueden elegir.
 *
 * El número sale hacia arriba al subir y hacia abajo al bajar, con muelle. Con [sparkles] lo
 * rodean destellos que dan un pulso en cada cambio, como el «12» del diseño.
 */
@Composable
fun MinutesPicker(
    minutes: Int,
    range: IntRange,
    presets: List<Int>,
    unit: String,
    description: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 72.sp,
    sparkles: Boolean = false,
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (sparkles) {
                val pulse = remember { Animatable(1f) }
                LaunchedEffect(minutes) {
                    pulse.snapTo(1.35f)
                    pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
                }
                Sparkles(boost = pulse.value, modifier = Modifier.matchParentSize())
            }
            AnimatedContent(
                targetState = minutes,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spatial) { h -> if (up) h else -h } + fadeIn(effects)) togetherWith
                        (slideOutVertically(spatial) { h -> if (up) -h else h } + fadeOut(effects))
                },
                label = "minutes",
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
            ) { value ->
                Text(
                    text = value.toString(),
                    fontSize = numberSize,
                    lineHeight = numberSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StepButton(
                symbol = "−",
                description = stringResource(R.string.limit_less),
                enabled = minutes > range.first,
                onClick = { onChange(((minutes - 1) / STEP * STEP).coerceIn(range)) },
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepButton(
                symbol = "+",
                description = stringResource(R.string.limit_more),
                enabled = minutes < range.last,
                onClick = { onChange(((minutes / STEP + 1) * STEP).coerceIn(range)) },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = preset == minutes,
                    enabled = preset in range,
                    onClick = { onChange(preset) },
                    label = { Text(stringResource(R.string.minutes_short, preset)) },
                    shape = CircleShape,
                    colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

/** Botón redondo lima: al pulsarlo encoge y sus esquinas se cierran de círculo a cuadrado blando. */
@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(
        targetValue = if (pressed) 28 else 50,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "stepCorner",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        color = Lime.copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = Ink,
        interactionSource = interaction,
        modifier = Modifier.size(52.dp).pressScale(interaction, 0.9f).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun LimitPickerPreview() {
    TouchGrassTheme { LimitPicker(minutes = 30, onChange = {}, numberSize = 112.sp, sparkles = true) }
}
