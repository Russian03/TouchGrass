package io.github.russian03.touchgrass.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.DisclosurePoints
import io.github.russian03.touchgrass.ui.common.LIMIT_RANGE
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.ProtectionRow
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTipRow
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.design.BumpCardShape
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.PointingHand
import io.github.russian03.touchgrass.ui.design.Sparkles
import io.github.russian03.touchgrass.ui.design.Sprout
import io.github.russian03.touchgrass.ui.design.SproutGeometry
import io.github.russian03.touchgrass.ui.design.SproutMood
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.fadingEdges
import io.github.russian03.touchgrass.ui.design.handAppear
import io.github.russian03.touchgrass.ui.theme.CardBeige
import io.github.russian03.touchgrass.ui.theme.CardBeigeEdge
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.LimeDeep
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pasos de los primeros pasos, en orden. El aviso del servicio va justo antes de activarlo, como
 * pide Google Play; si se rechaza, se salta la activación y se va al final.
 */
private enum class Step { Welcome, Name, HowItWorks, Limit, Notifications, Disclosure, Activate, Done }

/** Cada punto de «Cómo funciona» se queda en primer plano este tiempo antes de pasar al siguiente. */
private const val HOW_CYCLE_MS = 2_600L

/** Bulto de la tarjeta: radio del círculo y cuánto sobresale por encima del borde. */
private val BumpRadius = 46.dp
private val BumpRise = 30.dp

/** Sitio por encima de la tarjeta para la ramita. */
private val TwigRoom = 14.dp

/**
 * Ancho del personaje y su posición: el centro del cuerpo queda [SproutLift] por encima del centro
 * del bulto, así que asoma por arriba. El texto de la tarjeta empieza por debajo del cuerpo.
 */
private val SproutWidth = 64.dp
private val SproutHeight = SproutWidth * SproutGeometry.ASPECT
private val SproutLift = 14.dp
private val SproutTop = TwigRoom + BumpRadius - SproutLift - SproutHeight * SproutGeometry.BODY_CENTRE_Y
private val ContentTop = SproutTop - TwigRoom + SproutHeight * SproutGeometry.BODY_BOTTOM_Y + 12.dp

/** Letras con las que los ojos llegan al otro lado al escribir el nombre. */
private const val NAME_LOOK_CHARS = 14

/** Lo que se queda triste antes de pasar al final, y cada cuánto salta al celebrar. */
private const val SAD_MS = 700L
private const val CELEBRATE_MS = 1_400L

/** Los avisos se piden en su paso solo en Android 13+ y si aún no hay permiso. */
private fun needsNotificationStep(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    return permission != PackageManager.PERMISSION_GRANTED
}

/** Lo que hace cada botón de los primeros pasos (para no pasar una docena de parámetros sueltos). */
private class SetupActions(
    val onNameChange: (String) -> Unit,
    val onSaveName: (onSaved: () -> Unit) -> Unit,
    val onLimitChange: (Int) -> Unit,
    val onSaveLimit: (onSaved: () -> Unit) -> Unit,
    val onRequestNotifications: (onDone: () -> Unit) -> Unit,
    val onAccept: (onSaved: () -> Unit) -> Unit,
    val onDecline: () -> Unit,
    val onFinish: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenAppSettings: () -> Unit,
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val steps =
        remember {
            Step.entries.filter { it != Step.Notifications || needsNotificationStep(context) }
        }
    // Se acepte o no el permiso de avisos, se sigue al paso siguiente.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            afterPermission?.invoke()
            afterPermission = null
        }
    OnboardingContent(
        state = state,
        protection = rememberProtectionState(),
        showXiaomiTip = SystemSettings.isXiaomi,
        steps = steps,
        actions =
        SetupActions(
            onNameChange = viewModel::onNameChange,
            onSaveName = viewModel::saveName,
            onLimitChange = viewModel::onLimitChange,
            onSaveLimit = viewModel::saveLimit,
            onRequestNotifications = { onDone ->
                // El paso solo sale en Android 13+, que es donde existe el permiso.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    afterPermission = onDone
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onDone()
                }
            },
            onAccept = viewModel::acceptAccessibility,
            onDecline = viewModel::declineAccessibility,
            onFinish = { viewModel.finish(onFinished) },
            onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
            onOpenAppSettings = { SystemSettings.openAppDetails(context) },
        ),
    )
}

/**
 * Primeros pasos con la paleta de la app: fondo crema y una sola tarjeta centrada con el personaje
 * en un bulto. Al avanzar no se cambia de página: el texto de la tarjeta se funde con el siguiente
 * y la tarjeta se adapta a su nuevo tamaño. Debajo, un punto por paso.
 */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    steps: List<Step>,
    actions: SetupActions,
    initialStep: Step = Step.Welcome,
) {
    // Solo se avanza con los botones: así el nombre y el límite se guardan antes de pasar de paso.
    var index by rememberSaveable { mutableIntStateOf(steps.indexOf(initialStep).coerceAtLeast(0)) }
    val current = steps[index]
    val goTo: (Step) -> Unit = { index = steps.indexOf(it) }
    val next = { index = (index + 1).coerceAtMost(steps.lastIndex) }
    // Desde el final se vuelve al aviso si se rechazó (la activación se saltó).
    val back = {
        val skippedActivate = current == Step.Done && state.declined
        goTo(if (skippedActivate) Step.Disclosure else steps[(index - 1).coerceAtLeast(0)])
    }
    BackHandler(enabled = index > 0) { back() }

    // Lo que el personaje recuerda entre pasos.
    val scope = rememberCoroutineScope()
    val inspection = LocalInspectionMode.current
    val on = protection == ProtectionState.Running
    val nameInteraction = remember { MutableInteractionSource() }
    val nameFocused by nameInteraction.collectIsFocusedAsState()
    var howActive by rememberSaveable { mutableIntStateOf(0) }
    var sad by remember { mutableStateOf(false) }
    var celebrations by remember { mutableIntStateOf(0) }
    var hops by remember { mutableIntStateOf(0) }
    // Salta al llegar a cada paso.
    LaunchedEffect(current) { hops++ }
    // Los tres puntos de «Cómo funciona» van pasando solos.
    LaunchedEffect(current, howActive) {
        if (current != Step.HowItWorks || inspection) return@LaunchedEffect
        delay(HOW_CYCLE_MS)
        howActive = (howActive + 1) % 3
    }
    // Con la protección puesta, al final celebra saltando cada poco.
    LaunchedEffect(current, on) {
        while (current == Step.Done && on) {
            delay(CELEBRATE_MS)
            celebrations++
        }
    }
    val sprout =
        when (current) {
            Step.Welcome -> SproutState(SproutMood.Joy, bounce = hops)
            // Mira el campo y sigue el final del texto; con nombre, sonríe; salta con cada letra.
            Step.Name ->
                SproutState(
                    mood = if (state.name.isBlank()) SproutMood.Curious else SproutMood.Happy,
                    look = nameLook(state.name, nameFocused),
                    bounce = hops + state.name.length,
                )
            // Mira hacia el punto destacado (más abajo cuanto más avanza) y asiente al cambiar.
            Step.HowItWorks ->
                SproutState(
                    SproutMood.Calm,
                    look = Offset(-0.4f + 0.4f * howActive, 0.4f + 0.3f * howActive),
                    bounce = hops + howActive,
                )
            // Pocos minutos le encantan; muchos le preocupan.
            Step.Limit ->
                SproutState(limitMood(state.limitMinutes), look = Offset(0f, 1f), bounce = hops + state.limitMinutes)
            Step.Notifications -> SproutState(SproutMood.Curious, look = Offset(0.3f, -1f), bounce = hops)
            Step.Disclosure -> SproutState(if (sad) SproutMood.Sad else SproutMood.Calm, bounce = hops)
            // Espera mirando a un lado; al activarse, salta de alegría.
            Step.Activate ->
                SproutState(
                    mood = if (on) SproutMood.Joy else SproutMood.Curious,
                    look = if (on) null else Offset(0.9f, 0.3f),
                    bounce = hops + if (on) 1 else 0,
                    sparkle = on,
                )
            Step.Done ->
                SproutState(
                    mood = if (on) SproutMood.Joy else SproutMood.Happy,
                    bounce = hops + celebrations,
                    sparkle = on,
                )
        }
    // Se pone triste un momento antes de pasar al final.
    val decline: () -> Unit = {
        actions.onDecline()
        scope.launch {
            sad = true
            delay(SAD_MS)
            goTo(Step.Done)
            sad = false
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                if (index > 0 && current != Step.Done) {
                    IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            }
            SetupCard(step = current, sprout = sprout, modifier = Modifier.weight(1f)) { step ->
                val card =
                    stepCard(
                        step = step,
                        state = state,
                        protection = protection,
                        showXiaomiTip = showXiaomiTip,
                        actions = actions,
                        nameInteraction = nameInteraction,
                        howActive = howActive,
                        onHowActive = { howActive = it },
                        next = next,
                        goTo = goTo,
                        decline = decline,
                    )
                CardBody(card, busy = state.saving, error = state.error)
            }
            PageDots(
                count = steps.size,
                current = index,
                label = stringResource(R.string.onboarding_step, index + 1, steps.size),
            )
        }
    }
}

/** Lo que enseña la tarjeta en un paso: título, texto, lo propio del paso y los botones. */
private class StepCard(
    val title: String,
    val body: String?,
    val primary: Pair<String, () -> Unit>,
    val secondary: Pair<String, () -> Unit>? = null,
    val content: @Composable ColumnScope.() -> Unit = {},
)

@Composable
private fun stepCard(
    step: Step,
    state: OnboardingUiState,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    actions: SetupActions,
    nameInteraction: MutableInteractionSource,
    howActive: Int,
    onHowActive: (Int) -> Unit,
    next: () -> Unit,
    goTo: (Step) -> Unit,
    decline: () -> Unit,
): StepCard {
    val on = protection == ProtectionState.Running
    return when (step) {
        Step.Welcome ->
            StepCard(
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                primary = stringResource(R.string.onboarding_start) to next,
            )
        Step.Name ->
            StepCard(
                title = stringResource(R.string.onboarding_name_title),
                body = stringResource(R.string.onboarding_name_body),
                primary = stringResource(R.string.onboarding_next) to { actions.onSaveName(next) },
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions.onNameChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
                    keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { actions.onSaveName(next) }),
                    shape = CircleShape,
                    interactionSource = nameInteraction,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                Note(stringResource(R.string.onboarding_name_note))
            }
        Step.HowItWorks ->
            StepCard(
                title = stringResource(R.string.onboarding_how_title),
                body = null,
                primary = stringResource(R.string.onboarding_next) to next,
            ) {
                val points =
                    listOf(
                        R.string.onboarding_how_block_title to R.string.onboarding_how_block_body,
                        R.string.onboarding_how_budget_title to R.string.onboarding_how_budget_body,
                        R.string.onboarding_how_unlock_title to R.string.onboarding_how_unlock_body,
                    )
                points.forEachIndexed { i, (title, body) ->
                    Point(
                        title = title,
                        body = body,
                        highlighted = i == howActive,
                        onClick = { onHowActive(i) },
                        modifier = Modifier.padding(top = if (i == 0) 12.dp else 0.dp),
                    )
                }
            }
        Step.Limit ->
            StepCard(
                title = stringResource(R.string.onboarding_limit_title),
                body = stringResource(R.string.onboarding_limit_body),
                primary = stringResource(R.string.onboarding_limit_save) to { actions.onSaveLimit(next) },
            ) {
                LimitPicker(
                    minutes = state.limitMinutes,
                    onChange = actions.onLimitChange,
                    numberSize = 48.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Note(stringResource(R.string.onboarding_limit_note))
            }
        Step.Notifications ->
            StepCard(
                title = stringResource(R.string.onboarding_notify_title),
                body = stringResource(R.string.onboarding_notify_body),
                primary = stringResource(R.string.onboarding_notify_allow) to { actions.onRequestNotifications(next) },
                secondary = stringResource(R.string.onboarding_notify_later) to next,
            )
        Step.Disclosure ->
            StepCard(
                title = stringResource(R.string.onboarding_disclosure_title),
                body = stringResource(R.string.onboarding_disclosure_intro),
                primary = stringResource(R.string.onboarding_disclosure_accept) to {
                    actions.onAccept { goTo(Step.Activate) }
                },
                secondary = stringResource(R.string.onboarding_disclosure_decline) to decline,
            ) {
                DisclosurePoints()
            }
        Step.Activate ->
            StepCard(
                title = stringResource(R.string.onboarding_activate_title),
                body = stringResource(if (on) R.string.onboarding_protection_on else R.string.onboarding_activate_body),
                primary =
                if (on) {
                    stringResource(R.string.onboarding_next) to { goTo(Step.Done) }
                } else {
                    stringResource(R.string.onboarding_activate_open) to actions.onOpenAccessibilitySettings
                },
                secondary = if (on) null else stringResource(R.string.onboarding_finish_later) to { goTo(Step.Done) },
            ) {
                ActivateRows(protection, showXiaomiTip, actions)
            }
        Step.Done ->
            StepCard(
                title = stringResource(R.string.onboarding_done_title),
                body = stringResource(if (on) R.string.onboarding_done_on else R.string.onboarding_done_off),
                primary = stringResource(R.string.onboarding_finish) to actions.onFinish,
            )
    }
}

/**
 * Adónde mira mientras se escribe el nombre: hacia abajo, al campo, y de izquierda a derecha según
 * lo largo que va el texto. Sin foco ni texto, mira a su aire.
 */
private fun nameLook(name: String, focused: Boolean): Offset? {
    if (!focused && name.isEmpty()) return null
    val progress = name.length.coerceAtMost(NAME_LOOK_CHARS) / NAME_LOOK_CHARS.toFloat()
    return Offset(-0.8f + 1.6f * progress, 1f)
}

/** Con pocos minutos, alegría; en la parte alta del rango, preocupación. */
private fun limitMood(minutes: Int): SproutMood {
    val share = (minutes - LIMIT_RANGE.first).toFloat() / (LIMIT_RANGE.last - LIMIT_RANGE.first)
    return when {
        share <= 0.25f -> SproutMood.Joy
        share <= 0.5f -> SproutMood.Happy
        share <= 0.75f -> SproutMood.Calm
        else -> SproutMood.Worried
    }
}

/** Lo que hace el personaje en una página: cómo está, adónde mira, cuándo salta y si hay destellos. */
private class SproutState(
    val mood: SproutMood,
    val look: Offset? = null,
    val bounce: Int = 0,
    val sparkle: Boolean = false,
)

/**
 * La tarjeta de todos los pasos: beige más oscuro que el fondo, centrada, con un bulto arriba donde
 * se asoma el personaje (la ramita sale por encima). Al cambiar de [step], el contenido se funde
 * con el nuevo y la tarjeta se estira o encoge con muelle hasta su nuevo tamaño.
 */
@Composable
private fun SetupCard(
    step: Step,
    sprout: SproutState,
    modifier: Modifier = Modifier,
    body: @Composable (Step) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cardMax = maxHeight
        val cardColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer else CardBeige
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val resize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = cardMax)) {
            Surface(
                color = cardColor,
                shape = BumpCardShape(bump = BumpRadius, rise = BumpRise),
                modifier = Modifier.padding(top = TwigRoom).fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = step,
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        (fadeIn(enter) + slideInVertically(tween(260)) { it / 16 }) togetherWith
                            fadeOut(exit) using SizeTransform(clip = false) { _, _ -> resize }
                    },
                    label = "setupCard",
                ) { shown -> body(shown) }
            }
            if (sprout.sparkle) {
                Sparkles(Modifier.align(Alignment.TopCenter).size(240.dp, 130.dp), color = LimeDeep, boost = 1.3f)
            }
            Sprout(
                mood = sprout.mood,
                look = sprout.look,
                bounce = sprout.bounce,
                faceColor = cardColor,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = SproutTop).width(SproutWidth),
            )
        }
    }
}

/**
 * Contenido de la tarjeta: el texto se desliza dentro si no cabe (con difuminado arriba y abajo) y
 * los botones se quedan siempre a la vista.
 */
@Composable
private fun CardBody(card: StepCard, busy: Boolean, error: Boolean) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = ContentTop)) {
        val textScroll = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
            Modifier
                .weight(1f, fill = false)
                .fadingEdges(textScroll, Orientation.Vertical, 24.dp)
                .verticalScroll(textScroll),
        ) {
            MixedTitle(
                text = card.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (card.body != null) {
                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            card.content(this)
        }
        if (error) {
            Text(
                text = stringResource(R.string.error_saving),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        ChunkyPillButton(
            text = card.primary.first,
            onClick = { if (!busy) card.primary.second() },
            modifier = Modifier.padding(top = 20.dp),
        )
        card.secondary?.let { (label, action) ->
            ChunkyPillButton(
                text = label,
                onClick = { if (!busy) action() },
                containerColor = MaterialTheme.colorScheme.background,
                edgeColor = CardBeigeEdge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

/** Un punto por paso: el actual se alarga con muelle, como en la referencia. */
@Composable
private fun PageDots(count: Int, current: Int, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label },
    ) {
        val dotColor = MaterialTheme.colorScheme.onBackground
        repeat(count) { i ->
            val width by animateDpAsState(
                targetValue = if (i == current) 24.dp else 8.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "dot",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .background(dotColor.copy(alpha = if (i == current) 1f else 0.3f), CircleShape),
            )
        }
    }
}

/** Estado del servicio y, si hacen falta, los avisos de ajustes restringidos y de Xiaomi. */
@Composable
private fun ActivateRows(protection: ProtectionState, showXiaomiTip: Boolean, actions: SetupActions) {
    // Android 13+ bloquea la accesibilidad a apps instaladas fuera de una tienda.
    val restricted = protection == ProtectionState.Off && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val xiaomi = showXiaomiTip && protection != ProtectionState.Off
    Column(Modifier.padding(top = 8.dp)) {
        ProtectionRow(
            state = protection,
            onOpenAccessibilitySettings = actions.onOpenAccessibilitySettings,
            divider = restricted || xiaomi,
        )
        if (restricted) {
            StatusRow(
                title = stringResource(R.string.home_xiaomi_tip_open),
                detail = stringResource(R.string.onboarding_restricted_hint),
                divider = xiaomi,
                leading = { MarkCircle("⋮", highlighted = false) },
                trailing = {
                    TextButton(onClick = actions.onOpenAppSettings) { Text(stringResource(R.string.home_open_short)) }
                },
            )
        }
        if (xiaomi) XiaomiTipRow(onOpenAppSettings = actions.onOpenAppSettings, divider = false)
    }
}

/**
 * Apartado de «Cómo funciona». El que toca se resalta en un beige más oscuro que la tarjeta y lleva
 * a su izquierda la mano señalándolo, que aparece creciendo con un pequeño giro.
 */
@Composable
private fun Point(title: Int, body: Int, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val shade = if (dark) Color.White.copy(alpha = 0.08f) else CardBeigeEdge.copy(alpha = 0.6f)
    val background by animateColorAsState(
        targetValue = if (highlighted) shade else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pointBackground",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(background, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (highlighted) {
                PointingHand(
                    fill = if (dark) MaterialTheme.colorScheme.surfaceContainer else CardBeige,
                    modifier = Modifier.handAppear(),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MarkCircle(mark: String, highlighted: Boolean) {
    val color by animateColorAsState(
        targetValue = if (highlighted) Lime else MaterialTheme.colorScheme.outlineVariant,
        label = "markColor",
    )
    Box(Modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(mark, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

private val noActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun PreviewStep(step: Step, protection: ProtectionState = ProtectionState.Off) {
    TouchGrassTheme {
        OnboardingContent(
            state = OnboardingUiState(),
            protection = protection,
            showXiaomiTip = false,
            steps = Step.entries,
            actions = noActions,
            initialStep = step,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun WelcomePreview() = PreviewStep(Step.Welcome)

@Preview(showSystemUi = true)
@Composable
private fun NamePreview() = PreviewStep(Step.Name)

@Preview(showSystemUi = true)
@Composable
private fun HowPreview() = PreviewStep(Step.HowItWorks)

@Preview(showSystemUi = true)
@Composable
private fun LimitPreview() = PreviewStep(Step.Limit)

@Preview(showSystemUi = true)
@Composable
private fun NotificationsPreview() = PreviewStep(Step.Notifications)

@Preview(showSystemUi = true)
@Composable
private fun DisclosurePreview() = PreviewStep(Step.Disclosure)

@Preview(showSystemUi = true)
@Composable
private fun ActivatePreview() = PreviewStep(Step.Activate, ProtectionState.Running)

@Preview(showSystemUi = true)
@Composable
private fun DonePreview() = PreviewStep(Step.Done, ProtectionState.Running)
