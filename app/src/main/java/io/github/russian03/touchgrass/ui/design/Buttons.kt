package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.ui.theme.Dull
import io.github.russian03.touchgrass.ui.theme.DullEdge
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkMuted
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.LimeDeep
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Al pulsar, las píldoras encogen al 96 % y sus esquinas pasan de redondas a 16 dp. */
private const val PRESSED_SCALE = 0.96f
private val PressedShape = RoundedCornerShape(16.dp)

/** Botón principal: píldora negra (lima en modo noche) con texto crema, como «Continue». [compact], más pequeña. */
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
    compact: Boolean = false,
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
        contentPadding =
        if (compact) {
            PaddingValues(
                horizontal = 22.dp,
                vertical = 10.dp,
            )
        } else {
            PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        },
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction).pillWidth(fillWidth, minHeight = if (compact) 44.dp else 56.dp),
    ) {
        Text(text, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
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

/** Alto del canto de [ChunkyPillButton] y su esquina, como el botón de la referencia. */
private val ChunkyEdge = 4.dp
private val ChunkyShape = RoundedCornerShape(16.dp)

/**
 * Botón con relieve, como «Share my progress»: ancho, con el texto en mayúsculas y un canto más
 * oscuro debajo. Al pulsarlo baja y tapa el canto, como una tecla. Sin [enabled] sale gris y
 * no se puede pulsar.
 */
@Composable
fun ChunkyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Lime,
    edgeColor: Color = LimeDeep,
    contentColor: Color = Ink,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (enabled) containerColor else Dull, label = "chunkyColor")
    val edge by animateColorAsState(if (enabled) edgeColor else DullEdge, label = "chunkyEdge")
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateDpAsState(
        targetValue = if (pressed) ChunkyEdge else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chunkyDrop",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(ChunkyHeight + ChunkyEdge)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        // Canto: asoma por debajo del botón.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ChunkyHeight)
                .background(edge, ChunkyShape),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
            Modifier
                .offset { IntOffset(0, drop.roundToPx()) }
                .fillMaxWidth()
                .height(ChunkyHeight)
                .background(container, ChunkyShape),
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = if (enabled) contentColor else InkMuted,
            )
        }
    }
}

private val ChunkyHeight = 52.dp

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

private fun Modifier.pillWidth(fill: Boolean, minHeight: Dp = 56.dp) =
    (if (fill) fillMaxWidth() else this).heightIn(min = minHeight)

@Preview(showBackground = true, backgroundColor = 0xFFFBF8EF)
@Composable
private fun PillsPreview() {
    TouchGrassTheme {
        Column {
            InkPillButton("Continuar", {})
            OutlinePillButton("Ver estadísticas", {})
            ChunkyPillButton("Volver", {})
        }
    }
}
