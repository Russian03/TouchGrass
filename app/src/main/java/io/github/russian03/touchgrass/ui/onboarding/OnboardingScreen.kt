package io.github.russian03.touchgrass.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.DarkChunky
import io.github.russian03.touchgrass.ui.common.DarkChunkyEdge
import io.github.russian03.touchgrass.ui.common.DisclosurePoints
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.ProtectionRow
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTipRow
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
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

/** El gráfico entra un poco después que la tarjeta de su página. */
private const val HERO_STEP_MS = 80L

/** Lo más alto que puede ser la tarjeta: el resto es para el gráfico. */
private const val CARD_MAX_SHARE = 0.66f

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
 * Primeros pasos con el estilo de la referencia: fondo lima entero, el gráfico arriba y, abajo, una
 * tarjeta oscura con el título, el texto y los botones con relieve. Debajo, un punto por paso.
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
    // Solo se avanza con los botones: así el nombre y el límite se guardan antes de pasar de página.
    val pager = rememberPagerState(initialPage = steps.indexOf(initialStep).coerceAtLeast(0)) { steps.size }
    val scope = rememberCoroutineScope()
    val pageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val goTo: (Step) -> Unit = { target ->
        scope.launch { pager.animateScrollToPage(steps.indexOf(target), animationSpec = pageSpec) }
    }
    val current = steps[pager.currentPage]
    val next = { goTo(steps[(pager.currentPage + 1).coerceAtMost(steps.lastIndex)]) }
    // Desde el final se vuelve al aviso si se rechazó (la activación se saltó).
    val back = {
        val skippedActivate = current == Step.Done && state.declined
        goTo(if (skippedActivate) Step.Disclosure else steps[(pager.currentPage - 1).coerceAtLeast(0)])
    }
    BackHandler(enabled = pager.currentPage > 0) { back() }

    Surface(color = Lime, contentColor = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                if (pager.currentPage > 0 && current != Step.Done) {
                    IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            }
            HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
                val visible = pager.currentPage == page
                StepPage(steps[page], visible, state, protection, showXiaomiTip, actions, next, goTo)
            }
            PageDots(
                count = steps.size,
                current = pager.currentPage,
                label = stringResource(R.string.onboarding_step, pager.currentPage + 1, steps.size),
            )
        }
    }
}

@Composable
private fun StepPage(
    step: Step,
    visible: Boolean,
    state: OnboardingUiState,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    actions: SetupActions,
    next: () -> Unit,
    goTo: (Step) -> Unit,
) {
    val on = protection == ProtectionState.Running
    when (step) {
        Step.Welcome ->
            SetupPage(
                visible = visible,
                hero = { GrassSprouts(visible = visible, modifier = Modifier.fillMaxSize()) },
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                primary = stringResource(R.string.onboarding_start) to next,
            )
        Step.Name ->
            SetupPage(
                visible = visible,
                hero = {
                    NameShape(
                        initial = state.name.trim().firstOrNull()?.uppercaseChar(),
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                title = stringResource(R.string.onboarding_name_title),
                body = stringResource(R.string.onboarding_name_body),
                primary = stringResource(R.string.onboarding_next) to { actions.onSaveName(next) },
                busy = state.saving,
                error = state.error,
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
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                Note(stringResource(R.string.onboarding_name_note))
            }
        Step.HowItWorks -> HowItWorksPage(visible, next)
        Step.Limit ->
            SetupPage(
                visible = visible,
                hero = { MinutesShape(state.limitMinutes, Modifier.fillMaxSize()) },
                title = stringResource(R.string.onboarding_limit_title),
                body = stringResource(R.string.onboarding_limit_body),
                primary = stringResource(R.string.onboarding_limit_save) to { actions.onSaveLimit(next) },
                busy = state.saving,
                error = state.error,
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
            SetupPage(
                visible = visible,
                hero = { RingingBell(Modifier.fillMaxSize()) },
                title = stringResource(R.string.onboarding_notify_title),
                body = stringResource(R.string.onboarding_notify_body),
                primary = stringResource(R.string.onboarding_notify_allow) to { actions.onRequestNotifications(next) },
                secondary = stringResource(R.string.onboarding_notify_later) to next,
            )
        Step.Disclosure ->
            SetupPage(
                visible = visible,
                hero = { BreathingLock(Modifier.fillMaxSize()) },
                title = stringResource(R.string.onboarding_disclosure_title),
                body = stringResource(R.string.onboarding_disclosure_intro),
                primary = stringResource(R.string.onboarding_disclosure_accept) to {
                    actions.onAccept { goTo(Step.Activate) }
                },
                secondary = stringResource(R.string.onboarding_disclosure_decline) to {
                    actions.onDecline()
                    goTo(Step.Done)
                },
                busy = state.saving,
                error = state.error,
            ) {
                DisclosurePoints(visible = visible)
            }
        Step.Activate ->
            SetupPage(
                visible = visible,
                hero = {
                    ProtectionSwitch(
                        on = on,
                        alert = protection == ProtectionState.Stopped,
                        onClick = actions.onOpenAccessibilitySettings,
                    )
                },
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
            SetupPage(
                visible = visible,
                hero = { AllSetBadge(visible, Modifier.fillMaxSize()) },
                title = stringResource(R.string.onboarding_done_title),
                body = stringResource(if (on) R.string.onboarding_done_on else R.string.onboarding_done_off),
                primary = stringResource(R.string.onboarding_finish) to actions.onFinish,
                busy = state.saving,
                error = state.error,
            )
    }
}

/**
 * Esqueleto de cada paso: el gráfico ocupa lo que deja la tarjeta y la tarjeta (en el tema oscuro,
 * para que todo lo de dentro tenga buen contraste) va abajo, sin pasar de dos tercios del alto. Si
 * su contenido no cabe, se desliza dentro y los botones se quedan siempre a la vista.
 */
@Composable
private fun SetupPage(
    visible: Boolean,
    hero: @Composable () -> Unit,
    title: String,
    body: String?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    busy: Boolean = false,
    error: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardMax = maxHeight * CARD_MAX_SHARE
        Column(Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .staggerIn(index = 1, visible = visible, stepMs = HERO_STEP_MS, offsetY = 24.dp, fromScale = 0.9f),
            ) {
                TouchGrassTheme(darkTheme = false) { hero() }
            }
            TouchGrassTheme(darkTheme = true) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = MaterialTheme.shapes.large,
                    modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .heightIn(max = cardMax)
                        .staggerIn(index = 0, visible = visible, offsetY = 32.dp, fromScale = 1f),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        ) {
                            MixedTitle(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (body != null) {
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                            content()
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
                            text = primary.first,
                            onClick = { if (!busy) primary.second() },
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        if (secondary != null) {
                            ChunkyPillButton(
                                text = secondary.first,
                                onClick = { if (!busy) secondary.second() },
                                containerColor = DarkChunky,
                                edgeColor = DarkChunkyEdge,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
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
                    .background(Ink.copy(alpha = if (i == current) 1f else 0.3f), CircleShape),
            )
        }
    }
}

/** Los tres puntos van pasando solos; tocar uno lo pone delante. La forma de arriba lo sigue. */
@Composable
private fun HowItWorksPage(visible: Boolean, next: () -> Unit) {
    var active by rememberSaveable { mutableIntStateOf(0) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(visible, active) {
        if (!visible || inspection) return@LaunchedEffect
        delay(HOW_CYCLE_MS)
        active = (active + 1) % 3
    }
    SetupPage(
        visible = visible,
        hero = { HowShape(active = active, modifier = Modifier.fillMaxSize()) },
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
                mark = (i + 1).toString(),
                title = title,
                body = body,
                highlighted = i == active,
                onClick = { active = i },
                modifier = Modifier.padding(top = if (i == 0) 12.dp else 0.dp),
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

/** Punto de una lista: círculo con su marca y, si está [highlighted], fondo resaltado detrás. */
@Composable
private fun Point(
    mark: String,
    title: Int,
    body: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue =
        if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pointBackground",
    )
    Row(
        verticalAlignment = Alignment.Top,
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(background, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        MarkCircle(mark, highlighted)
        Column(Modifier.padding(start = 16.dp)) {
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
