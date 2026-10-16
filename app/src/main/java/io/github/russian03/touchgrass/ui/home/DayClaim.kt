package io.github.russian03.touchgrass.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.ArrayRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.DayFace
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.ui.common.onWeekday
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.DayReveal
import io.github.russian03.touchgrass.ui.design.LocalSplashGone
import io.github.russian03.touchgrass.ui.design.OutlinePillButton
import io.github.russian03.touchgrass.ui.design.mixedWeights
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Momentos de la secuencia, en ms.
private const val START_DELAY_MS = 450L
private const val AFTER_ZOOM_MS = 200L
private const val SCROLL_MS = 350L
private const val MOUTH_AFTER_EYES_MS = 280L
private const val GREEN_AFTER_MOUTH_MS = 320L
private const val GREEN_MS = 380
private const val BETWEEN_DAYS_MS = 150L
private const val PHRASE_MS = 400
private const val BUTTONS_AFTER_PHRASE_MS = 500L
private const val LEAVE_MS = 650

/** Oscuridad del fondo con el pop-up. */
private const val SCRIM_ALPHA = 0.4f

/** Píldoras a la vista en el pop-up: menos que en Inicio, así que más grandes. */
private const val POPUP_PILLS = 5.25f
private val CardPadding = 20.dp

/** Margen del recuadro con los lados de la pantalla y de las píldoras con el recuadro. */
private val CardMargin = 16.dp
private val PopupEdge = 16.dp

/** Sitio dentro de la tira para que la píldora y los ojos, al crecer, no se corten; sale del margen del recuadro. */
private val GrowRoom = 10.dp
private val CardCorner = 28.dp

/**
 * Primer acceso del día: Inicio se queda quieto y «Mi semana» sale de su sitio ([fromTop], la
 * altura de la tira en Inicio) hacia el centro, dentro de un recuadro. Uno a uno, de más antiguo
 * a ayer, cada día que faltaba enseña su cara (ojos, boca y verde). Al final sale la frase según
 * cómo fue ayer y, medio segundo después, «Volver»; si se quitó la protección y sigue sin
 * funcionar, también «Activar protección».
 *
 * [onStart] avisa cuando el recuadro empieza a moverse (para esconder la tira de Inicio) y
 * [onDone], cuando ha vuelto a su sitio tras pulsar «Volver».
 */
@Composable
fun DayClaim(
    claim: ClaimState,
    week: List<WeekDay>,
    today: LocalDate,
    fromTop: Float?,
    protectionOff: Boolean,
    onEnableProtection: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
) {
    val inspection = LocalInspectionMode.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val start = if (inspection) 1f else 0f
    val zoom = remember { Animatable(start) }
    val phrase = remember { Animatable(start) }
    val buttons = remember { Animatable(start) }
    val reveals = remember(claim.days) { claim.days.associateWith { DayReveal(start, start, start) } }
    var focus by remember { mutableStateOf<LocalDate?>(null) }
    var visible by remember { mutableStateOf(inspection) }
    var leaving by remember { mutableStateOf(false) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState(Int.MAX_VALUE)
    // Las píldoras empiezan tras el margen del recuadro, que ya incluye el sitio para crecer.
    val stripOffset = with(LocalDensity.current) { CardPadding.toPx() }

    val splashGone = LocalSplashGone.current
    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        // Se espera a que se vaya la pantalla de carga y se deja entrar Inicio antes de que la tira salte.
        snapshotFlow { splashGone.value }.first { it }
        delay(START_DELAY_MS)
        visible = true
        onStart()
        zoom.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        delay(AFTER_ZOOM_MS)
        for (day in claim.days) {
            val reveal = reveals.getValue(day)
            focus = day
            delay(SCROLL_MS)
            coroutineScope {
                launch { reveal.eyes.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                delay(MOUTH_AFTER_EYES_MS)
                launch { reveal.mouth.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                delay(GREEN_AFTER_MOUTH_MS)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                reveal.green.animateTo(1f, tween(GREEN_MS, easing = FastOutSlowInEasing))
            }
            delay(BETWEEN_DAYS_MS)
        }
        phrase.animateTo(1f, tween(PHRASE_MS, easing = FastOutSlowInEasing))
        delay(BUTTONS_AFTER_PHRASE_MS)
        buttons.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }

    val done = buttons.value >= 0.5f
    val leave = {
        if (done && !leaving) {
            leaving = true
            scope.launch {
                // A la vez que la tira vuelve a su tamaño de Inicio, se desliza hasta el final, que
                // es donde está la de Inicio.
                val from = scroll.value
                val follow =
                    launch {
                        snapshotFlow { zoom.value }.collect { z ->
                            val t = 1f - z.coerceIn(0f, 1f)
                            scroll.scrollTo((from + (scroll.maxValue - from) * t).roundToInt())
                        }
                    }
                zoom.animateTo(0f, tween(LEAVE_MS, easing = FastOutSlowInEasing))
                follow.cancel()
                scroll.scrollTo(scroll.maxValue)
                onDone()
            }
        }
    }
    // Atrás no hace nada hasta que sale «Volver»; después, es lo mismo.
    BackHandler { leave() }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .fillMaxSize()
            // Se traga cualquier toque o deslizamiento: Inicio no se mueve mientras tanto.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * zoom.value.coerceIn(0f, 1f))) }
            .systemBarsPadding(),
    ) {
        // Todo el tamaño va con el zoom: a 0 la tira es igual que la de Inicio (de borde a borde)
        // y a 1 es la del recuadro, así que al salir y al volver no hay saltos.
        val z = zoom.value.coerceIn(0f, 1f)
        Box(
            Modifier
                .padding(horizontal = CardMargin * z)
                .fillMaxWidth()
                .onGloballyPositioned { cardTop = it.positionInRoot().y },
        ) {
            val background = MaterialTheme.colorScheme.background
            val corner = with(LocalDensity.current) { CardCorner.toPx() }
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier =
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    // Sale desde la tira de Inicio: su borde de arriba coincide con el de la tira.
                    val dy = fromTop?.let { it - (cardTop + stripOffset) } ?: 0f
                    translationY = dy * (1f - zoom.value)
                }
                    // El recuadro aparece con el zoom; la tira se ve desde el principio.
                    .drawBehind {
                        drawRoundRect(
                            background.copy(alpha = zoom.value.coerceIn(0f, 1f)),
                            cornerRadius = CornerRadius(corner),
                        )
                    },
            ) {
                ClaimCard(
                    claim = claim,
                    week = week,
                    today = today,
                    reveals = reveals,
                    focus = focus,
                    scroll = scroll,
                    leaving = leaving,
                    zoom = zoom,
                    phrase = phrase,
                    buttons = buttons,
                    protectionOff = protectionOff,
                    onEnableProtection = onEnableProtection,
                    onBack = leave,
                )
            }
        }
    }
}

@Composable
private fun ClaimCard(
    claim: ClaimState,
    week: List<WeekDay>,
    today: LocalDate,
    reveals: Map<LocalDate, DayReveal>,
    focus: LocalDate?,
    scroll: ScrollState,
    leaving: Boolean,
    zoom: Animatable<Float, *>,
    phrase: Animatable<Float, *>,
    buttons: Animatable<Float, *>,
    protectionOff: Boolean,
    onEnableProtection: () -> Unit,
    onBack: () -> Unit,
) {
    val z = zoom.value.coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = CardPadding - GrowRoom, bottom = CardPadding),
    ) {
        WeekStrip(
            week = week,
            today = today,
            pending = claim.days.toSet(),
            reveals = reveals,
            focus = focus,
            visiblePills = VISIBLE_PILLS + (POPUP_PILLS - VISIBLE_PILLS) * z,
            edge = lerp(ScreenEdge, PopupEdge, z),
            bleed = false,
            growRoom = GrowRoom,
            interactive = false,
            scroll = scroll,
            appearing = leaving,
            appear = { 1f - zoom.value },
        )
        // El texto y los botones ocupan su sitio desde el principio, para que el recuadro no salte.
        Text(
            text = mixedWeights(claimPhrase(claim, today)),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp - GrowRoom)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .graphicsLayer {
                    alpha = phrase.value * zoom.value.coerceIn(0f, 1f)
                    translationY = (1f - phrase.value) * 12.dp.toPx()
                },
        )
        Column(
            modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp)
                .graphicsLayer {
                    alpha = buttons.value.coerceIn(0f, 1f) * zoom.value.coerceIn(0f, 1f)
                    val scale = 0.9f + 0.1f * buttons.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            if (protectionOff && claim.disabledDay != null) {
                OutlinePillButton(
                    text = stringResource(R.string.claim_enable),
                    onClick = onEnableProtection,
                    fillWidth = true,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            ChunkyPillButton(text = stringResource(R.string.claim_back), onClick = onBack)
        }
    }
}

/**
 * Frase de debajo de la tira. Si alguno de los días se quitó la protección, va sobre eso; si no,
 * según la cara de ayer. Cada día sale una distinta.
 */
@Composable
private fun claimPhrase(claim: ClaimState, today: LocalDate): String {
    val yesterday = today.minusDays(1)
    val disabled = claim.disabledDay
    val day = claim.yesterday
    val seed = yesterday.toEpochDay()
    return when {
        disabled == yesterday -> pick(R.array.claim_disabled, seed)
        disabled != null -> String.format(Locale.ROOT, pick(R.array.claim_disabled_before, seed), onWeekday(disabled))
        day == null -> ""
        else ->
            String.format(
                Locale.ROOT,
                pick(phrases(day.face), seed),
                day.unlockedMinutes,
                day.limitMinutes ?: 0,
                day.streak,
            )
    }
}

@Composable
private fun pick(@ArrayRes array: Int, seed: Long): String {
    val items = stringArrayResource(array)
    return items[Math.floorMod(seed, items.size)]
}

@ArrayRes
private fun phrases(face: DayFace): Int = when (face) {
    DayFace.Mythic -> R.array.claim_mythic
    DayFace.Legend -> R.array.claim_legend
    DayFace.Cool -> R.array.claim_cool
    DayFace.Free, DayFace.Untracked -> R.array.claim_free
    DayFace.Happy -> R.array.claim_happy
    DayFace.Calm -> R.array.claim_calm
    DayFace.Neutral -> R.array.claim_neutral
    DayFace.Tight -> R.array.claim_tight
    DayFace.Sad -> R.array.claim_sad
    DayFace.Disabled -> R.array.claim_disabled
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun DayClaimPreview() {
    val today = LocalDate.of(2026, 10, 14)
    val week = previewWeek()
    TouchGrassTheme {
        DayClaim(
            claim = ClaimState(listOf(today.minusDays(1)), week.first { it.day == today.minusDays(1) }, null),
            week = week,
            today = today,
            fromTop = null,
            protectionOff = false,
            onEnableProtection = {},
            onStart = {},
            onDone = {},
        )
    }
}
