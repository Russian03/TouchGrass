package io.github.russian03.touchgrass.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.Sprout
import io.github.russian03.touchgrass.ui.design.SproutMood
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkEdge
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlinx.coroutines.delay

/** Pausa tras el fundido antes de que salga «Volver atrás», para no volver por impulso. */
private const val BUTTONS_EXTRA_DELAY_MS = 1_000L

/** Pausa extra tras «Volver atrás» antes del enlace para pedir minutos, para no invitar a usarlo. */
private const val LINK_EXTRA_DELAY_MS = 1_000L

private const val BUTTONS_FADE_MS = 400

/** Entre el logo y el texto, al entrar en cascada. */
private const val ENTER_STEP_MS = 150L

/**
 * Pantalla de bloqueo que aparece encima del scroll: lima, con el logo en silueta abajo a la
 * izquierda negando con la cabeza, «Respira.» y, más pequeño, «El scroll está bloqueado». Entra con un fundido de
 * [fadeInMs] y desde el primer instante se traga los toques, así que ya no se puede hacer scroll.
 * Un segundo después del fundido sale, abajo a la izquierda, el único botón: «Volver atrás», en
 * negro. Un segundo más tarde, encima, el enlace discreto para pedir más minutos (abre la app).
 */
@Composable
fun BlockScreen(fadeInMs: Long, onBack: () -> Unit, onOpenApp: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(fadeInMs.toInt(), easing = FastOutSlowInEasing)) }

    // Primero «Volver atrás» y, aún más tarde, el enlace para pedir minutos.
    val back = remember { Animatable(0f) }
    val link = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(fadeInMs + BUTTONS_EXTRA_DELAY_MS)
        back.animateTo(1f, tween(BUTTONS_FADE_MS, easing = FastOutSlowInEasing))
        delay(LINK_EXTRA_DELAY_MS)
        // Entra desde la izquierda con un rebote suave.
        link.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f))
    }
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    val slide = with(LocalDensity.current) { (-24).dp.toPx() }

    // La ventana del servicio no tiene tema: los botones y el título lo necesitan.
    TouchGrassTheme(darkTheme = false) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(Lime)
                // Se tragan los toques: lo de debajo no se puede tocar ni desplazar.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp)
                    .padding(top = 40.dp, bottom = 64.dp),
            ) {
                Sprout(
                    mood = SproutMood.Calm,
                    faceColor = Ink,
                    outline = true,
                    shaking = true,
                    modifier = Modifier.width(96.dp).staggerIn(0, stepMs = ENTER_STEP_MS),
                )
                MixedTitle(
                    text = stringResource(R.string.block_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = Ink,
                    modifier = Modifier.padding(top = 10.dp).staggerIn(1, stepMs = ENTER_STEP_MS),
                )
                Text(
                    text = stringResource(R.string.block_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 2.dp).staggerIn(2, stepMs = ENTER_STEP_MS),
                )
                // Ocupan su sitio desde el principio para que nada se mueva al aparecer. Hasta que se
                // ven del todo, no hacen nada.
                TextLink(
                    text = stringResource(R.string.block_open_app),
                    onClick = { if (link.value > 0.9f) onOpenApp() },
                    modifier = Modifier.appear(link.value, rise, slide),
                )
                ChunkyPillButton(
                    text = stringResource(R.string.block_dismiss),
                    onClick = { if (back.value == 1f) onBack() },
                    containerColor = Ink,
                    edgeColor = InkEdge,
                    contentColor = Lime,
                    compact = true,
                    modifier = Modifier.appear(back.value, rise),
                )
            }
        }
    }
}

/**
 * Entra con fundido según [progress] (de 0 a 1; con rebote puede pasarse un poco), subiendo
 * [rise] px y, si se da, viniendo de [slide] px a un lado y creciendo desde su borde izquierdo.
 */
private fun Modifier.appear(progress: Float, rise: Float, slide: Float = 0f): Modifier = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    translationY = (1f - progress) * rise
    translationX = (1f - progress) * slide
    if (slide != 0f) {
        val scale = 0.85f + 0.15f * progress
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0.5f)
    }
}

/** Enlace discreto, sin fondo: al pulsarlo solo se oscurece el texto. */
@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (pressed) Ink else InkSoft,
        textDecoration = TextDecoration.Underline,
        modifier =
        modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(top = 4.dp, bottom = 6.dp),
    )
}

@Preview(showSystemUi = true)
@Composable
private fun BlockScreenPreview() {
    BlockScreen(fadeInMs = 0, onBack = {}, onOpenApp = {})
}
