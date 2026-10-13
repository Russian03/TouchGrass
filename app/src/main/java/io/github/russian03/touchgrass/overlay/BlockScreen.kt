package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.Grass
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkMuted
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.Paper
import kotlinx.coroutines.delay

/**
 * Pantalla de bloqueo que aparece encima de un reel. Entra con un fundido de [fadeInMs]
 * mientras lo de detrás se desenfoca; desde el primer instante se traga los toques, así que
 * ya no se puede hacer scroll. El candado se cierra en su sitio y después sale "Volver atrás";
 * el enlace para pedir tiempo llega aún más tarde y discreto, para no invitar a usarlo.
 * Si el sistema no desenfoca lo de detrás ([blurredBehind] a false), el velo es casi opaco.
 */
/** Pausa extra tras el fundido antes de mostrar "Volver atrás". */
private const val BUTTONS_EXTRA_DELAY_MS = 1_000L

/** Pausa extra tras "Volver atrás" antes de mostrar el enlace para pedir tiempo. */
private const val LINK_EXTRA_DELAY_MS = 1_000L

private const val BUTTONS_FADE_MS = 600

@Composable
fun BlockScreen(blurredBehind: Boolean, fadeInMs: Long, onBack: () -> Unit, onOpenApp: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(fadeInMs.toInt(), easing = FastOutSlowInEasing)) }

    // Los botones salen un rato después del fundido, para no volver por impulso.
    val back = remember { Animatable(0f) }
    val link = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(fadeInMs + BUTTONS_EXTRA_DELAY_MS)
        back.animateTo(1f, tween(BUTTONS_FADE_MS))
        delay(LINK_EXTRA_DELAY_MS)
        link.animateTo(1f, tween(BUTTONS_FADE_MS))
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(Paper.copy(alpha = if (blurredBehind) 0.55f else 0.94f))
            // Se tragan los toques: el reel de debajo no se puede tocar ni desplazar.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ClosingLock(delayMs = fadeInMs / 2)
                Spacer(Modifier.height(40.dp))
                Text(
                    text = stringResource(R.string.block_title),
                    color = Ink,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Separated(stringResource(R.string.block_subtitle))
                Spacer(Modifier.height(40.dp))
                PulseDots()
            }

            // Ocupan su sitio desde el principio para que el candado no se mueva al aparecer.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                TextLink(
                    text = stringResource(R.string.block_open_app),
                    enabled = link.value == 1f,
                    onClick = onOpenApp,
                    modifier = Modifier.graphicsLayer { alpha = link.value },
                )
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = stringResource(R.string.block_dismiss),
                    icon = "←",
                    enabled = back.value == 1f,
                    onClick = onBack,
                    modifier = Modifier.graphicsLayer { alpha = back.value },
                )
            }
        }
    }
}

@Composable
private fun Separated(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).height(1.dp).background(Ink.copy(alpha = 0.19f)))
        Text(
            text = text,
            color = InkMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp).weight(1f, fill = false),
        )
        Box(Modifier.width(32.dp).height(1.dp).background(Ink.copy(alpha = 0.19f)))
    }
}

/** Tres puntos que laten en secuencia, como un pulso tranquilo. */
@Composable
private fun PulseDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec =
                infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 230),
                ),
                label = "dot$i",
            )
            Box(Modifier.size(6.dp).graphicsLayer { this.alpha = alpha }.background(Grass, CircleShape))
        }
    }
}

/**
 * Texto suelto, sin fondo ni forma, que se pulsa como un enlace: al apretarlo no sale ningún
 * recuadro, solo se resalta el texto.
 */
@Composable
private fun TextLink(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = text,
        color = if (pressed) Grass else InkSoft,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.Underline,
        modifier =
        modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PillButton(
    text: String,
    icon: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Black.copy(alpha = 0.06f),
        shape = RoundedCornerShape(32.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(text = icon, color = InkSoft, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            Text(text = text, color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun BlockScreenPreview() {
    BlockScreen(blurredBehind = false, fadeInMs = 0, onBack = {}, onOpenApp = {})
}
