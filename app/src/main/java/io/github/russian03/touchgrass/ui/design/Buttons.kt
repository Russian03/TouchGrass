package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Al pulsar, las píldoras encogen al 96 % y sus esquinas pasan de redondas a 16 dp. */
private const val PRESSED_SCALE = 0.96f
private val PressedShape = RoundedCornerShape(16.dp)

/** Botón principal: píldora negra (lima en modo noche) con texto crema, como «Continue». */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = PressedShape),
        enabled = enabled,
        colors =
        ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.25f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction).pillWidth(fillWidth),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón secundario: píldora con borde de tinta, como «Update Goals». */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = PressedShape),
        enabled = enabled,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction).pillWidth(fillWidth),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Encoge un poco mientras se pulsa, con el muelle rápido del tema. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = PRESSED_SCALE): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private fun Modifier.pillWidth(fill: Boolean) = (if (fill) fillMaxWidth() else this).heightIn(min = 56.dp)

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun PillsPreview() {
    TouchGrassTheme {
        Column {
            InkPillButton("Continuar", {})
            OutlinePillButton("Ver estadísticas", {})
        }
    }
}
