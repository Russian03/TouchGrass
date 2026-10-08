package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.Grass
import io.github.russian03.touchgrass.ui.theme.GrassGlow
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkMuted
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.Paper
import kotlinx.coroutines.delay

/** Tiempo antes de mostrar los botones: una pausa breve para no volver por impulso. */
private const val BUTTONS_DELAY_MS = 5_000L

@Composable
fun BlockScreen(reason: String, onDismiss: () -> Unit, onOpenApp: () -> Unit) {
    // Entrada suave del contenido central.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }

    var showButtons by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BUTTONS_DELAY_MS)
        showButtons = true
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        // Fondo: el logo como textura, desenfocado (en Android 12+) y velado.
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.08f,
            modifier = Modifier.fillMaxSize().blur(24.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = enter.value
                        translationY = (1f - enter.value) * 30.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LockedLogo()
                Spacer(Modifier.height(48.dp))
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
                Spacer(Modifier.height(48.dp))
                PulseDots()
                Spacer(Modifier.height(24.dp))
                Text(text = reason, color = InkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            AnimatedVisibility(
                visible = showButtons,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PillButton(stringResource(R.string.block_dismiss), "✕", onDismiss)
                    Spacer(Modifier.height(12.dp))
                    PillButton(stringResource(R.string.block_open_app), "⌂", onOpenApp)
                }
            }
        }
    }
}

/** Tarjeta con el logo que "se cierra" al entrar y un halo verde que respira detrás. */
@Composable
private fun LockedLogo() {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val breath by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                    alpha = 1.6f - breath
                }
                .background(GrassGlow.copy(alpha = 0.2f), CircleShape),
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            modifier =
            Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .shadow(4.dp, RoundedCornerShape(24.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
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

@Composable
private fun PillButton(text: String, icon: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.06f),
        shape = RoundedCornerShape(32.dp),
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
    BlockScreen(reason = "Pestaña de Reels", onDismiss = {}, onOpenApp = {})
}
