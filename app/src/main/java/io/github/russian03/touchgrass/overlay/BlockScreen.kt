package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.R

private val SkyTop = Color(0xFF8FD3F4)
private val SkyBottom = Color(0xFFE6F7D9)
private val Ink = Color(0xFF12361A)
private val ButtonGreen = Color(0xFF2E7D32)

@Composable
fun BlockScreen(reason: String, animateGrass: Boolean, onDismiss: () -> Unit) {
    // El texto aparece cuando el césped ya ha crecido (o al instante si no hay animación).
    val textAlpha = remember { Animatable(if (animateGrass) 0f else 1f) }
    LaunchedEffect(animateGrass) {
        if (animateGrass) textAlpha.animateTo(1f, tween(durationMillis = 700, delayMillis = 1_200))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        GrassScene(
            grow = animateGrass,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f),
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(top = 120.dp)
                .alpha(textAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.block_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.block_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ButtonGreen, contentColor = Color.White),
                modifier = Modifier.padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.block_dismiss))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun BlockScreenPreview() {
    BlockScreen(reason = "Pestaña de Reels", animateGrass = false, onDismiss = {})
}
