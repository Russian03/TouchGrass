package io.github.russian03.touchgrass.overlay

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkMuted
import io.github.russian03.touchgrass.ui.theme.Paper

/**
 * Tapa una zona de la app (p. ej. la cuadrícula de Explorar): lo que había debajo se ve
 * desenfocado y un candado se cierra encima. [backdrop] es una captura ya reducida de
 * esa zona; si no hay (Android < 11 o la captura falló) se usa un velo liso.
 */
@Composable
fun CoverScreen(backdrop: Bitmap?) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(450, easing = LinearOutSlowInEasing)) }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Paper)
            .graphicsLayer { alpha = enter.value }
            // Se tragan los toques: la cuadrícula de debajo no se puede abrir ni desplazar.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (backdrop != null) {
            Image(
                bitmap = remember(backdrop) { backdrop.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
                modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(12.dp) else Modifier),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (backdrop != null) 0.45f else 0.85f)))

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
                .graphicsLayer { translationY = (1f - enter.value) * 24.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClosingLock()
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.cover_title),
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.cover_subtitle),
                color = InkMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 720)
@Composable
private fun CoverScreenPreview() {
    CoverScreen(backdrop = null)
}
