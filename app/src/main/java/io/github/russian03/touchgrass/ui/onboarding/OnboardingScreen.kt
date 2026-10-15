package io.github.russian03.touchgrass.ui.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.ProtectionRow
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTipRow
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { Welcome, Name, HowItWorks, Disclosure, Limit, Activate }

/** Alto de la ilustración de cada paso, la mitad de arriba como en el diseño. */
private val HeroHeight = 300.dp

/** Cada punto de «Cómo funciona» se queda en primer plano este tiempo antes de pasar al siguiente. */
private const val HOW_CYCLE_MS = 2_600L

/** El gráfico entra un poco después que el texto de su página. */
private const val HERO_STEP_MS = 80L

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnboardingContent(
        state = state,
        protection = rememberProtectionState(),
        showXiaomiTip = SystemSettings.isXiaomi,
        onNameChange = viewModel::onNameChange,
        onSaveName = viewModel::saveName,
        onLimitChange = viewModel::onLimitChange,
        onSaveLimit = viewModel::saveLimit,
        onFinish = { viewModel.finish(onFinished) },
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
    )
}

/**
 * Primeros pasos: cinco páginas con la misma estructura que el diseño. Arriba, un gráfico grande
 * hecho en código; abajo, título con grosores mezclados, texto y la píldora de tinta. Una onda
 * arriba del todo marca el progreso.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    onNameChange: (String) -> Unit,
    onSaveName: (onSaved: () -> Unit) -> Unit,
    onLimitChange: (Int) -> Unit,
    onSaveLimit: (onSaved: () -> Unit) -> Unit,
    onFinish: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    initialStep: Step = Step.Welcome,
) {
    val steps = Step.entries
    // Solo se avanza con los botones: así el límite se guarda antes de pasar de página.
    val pager = rememberPagerState(initialPage = initialStep.ordinal) { steps.size }
    val scope = rememberCoroutineScope()
    val step = steps[pager.currentPage]
    val pageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val goTo: (Int) -> Unit = { page -> scope.launch { pager.animateScrollToPage(page, animationSpec = pageSpec) } }
    val next = { goTo(pager.currentPage + 1) }

    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    // La onda avanza con muelle al cambiar de paso.
    val inspection = LocalInspectionMode.current
    val target = (pager.currentPage + 1f) / steps.size
    val progress = remember { Animatable(if (inspection) target else 0f) }
    val progressSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) { progress.animateTo(target, progressSpec) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val stepLabel = stringResource(R.string.onboarding_step, pager.currentPage + 1, steps.size)
            LinearWavyProgressIndicator(
                progress = { progress.value.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.onBackground,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .semantics { contentDescription = stepLabel },
            )
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                val visible = pager.currentPage == page
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (steps[page]) {
                        Step.Welcome -> WelcomeStep(visible)
                        Step.Name -> NameStep(visible, state.name, onNameChange, onDone = { onSaveName(next) })
                        Step.HowItWorks -> HowItWorksStep(visible)
                        Step.Disclosure -> DisclosureStep(visible)
                        Step.Limit -> LimitStep(visible, state.limitMinutes, onLimitChange)
                        Step.Activate -> ActivateStep(
                            visible,
                            protection,
                            showXiaomiTip,
                            onOpenAccessibilitySettings,
                            onOpenAppSettings,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                if (state.error) {
                    Text(
                        text = stringResource(R.string.error_saving),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                val (label, action) =
                    when (step) {
                        Step.Welcome -> R.string.onboarding_start to next
                        Step.Name -> R.string.onboarding_next to { onSaveName(next) }
                        Step.HowItWorks -> R.string.onboarding_next to next
                        Step.Disclosure -> R.string.onboarding_disclosure_accept to next
                        Step.Limit -> R.string.onboarding_limit_save to { onSaveLimit(next) }
                        Step.Activate ->
                            (
                                if (protection == ProtectionState.Running) {
                                    R.string.onboarding_finish
                                } else {
                                    R.string.onboarding_finish_later
                                }
                                ) to onFinish
                    }
                InkPillButton(text = stringResource(label), onClick = action, enabled = !state.saving)
                // Ocupa su sitio siempre, para que la píldora no salte entre páginas.
                TextButton(onClick = { goTo(pager.currentPage - 1) }, enabled = pager.currentPage > 0) {
                    Text(if (pager.currentPage > 0) stringResource(R.string.back) else "")
                }
            }
        }
    }
}

/** Gráfico de arriba: entra después del texto, en cuanto su página se ve. */
@Composable
private fun Hero(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .staggerIn(index = 1, visible = visible, stepMs = HERO_STEP_MS, offsetY = 24.dp, fromScale = 0.9f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Title(text: String, visible: Boolean) {
    MixedTitle(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).staggerIn(index = 0, visible = visible),
    )
}

@Composable
private fun Body(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(top = 12.dp).staggerIn(index = 0, visible = visible),
    )
}

@Composable
private fun WelcomeStep(visible: Boolean) {
    Hero(visible) { GrassSprouts(visible = visible, modifier = Modifier.fillMaxSize()) }
    Title(stringResource(R.string.onboarding_welcome_title), visible)
    Body(stringResource(R.string.onboarding_welcome_body), visible)
}

/**
 * Nombre para el saludo de Inicio (opcional). Arriba, una forma lima con la inicial, que cambia
 * con un pequeño salto mientras se escribe.
 */
@Composable
private fun NameStep(visible: Boolean, name: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Hero(visible) { NameShape(initial = name.trim().firstOrNull()?.uppercaseChar(), modifier = Modifier.fillMaxSize()) }
    Title(stringResource(R.string.onboarding_name_title), visible)
    Body(stringResource(R.string.onboarding_name_body), visible)
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
        keyboardOptions =
        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).staggerIn(index = 1, visible = visible),
    )
    Body(stringResource(R.string.onboarding_name_note), visible, modifier = Modifier.padding(bottom = 8.dp))
}

/** Los tres puntos van pasando solos; tocar uno lo pone delante. La forma de arriba lo sigue. */
@Composable
private fun HowItWorksStep(visible: Boolean) {
    var active by rememberSaveable { mutableIntStateOf(0) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(visible, active) {
        if (!visible || inspection) return@LaunchedEffect
        delay(HOW_CYCLE_MS)
        active = (active + 1) % 3
    }
    Hero(visible) { HowShape(active = active, modifier = Modifier.fillMaxSize()) }
    Title(stringResource(R.string.onboarding_how_title), visible)
    Spacer(Modifier.height(16.dp))
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
            modifier = Modifier.staggerIn(index = 1 + i, visible = visible),
        )
    }
}

/** Aviso destacado que exige Google Play antes de pedir el servicio de accesibilidad. */
@Composable
private fun DisclosureStep(visible: Boolean) {
    Hero(visible) { BreathingLock(Modifier.fillMaxSize()) }
    Title(stringResource(R.string.onboarding_disclosure_title), visible)
    Body(stringResource(R.string.onboarding_disclosure_intro), visible)
    Spacer(Modifier.height(12.dp))
    val points =
        listOf(
            Triple("✓", R.string.onboarding_disclosure_reads_title, R.string.onboarding_disclosure_reads_body),
            Triple("✕", R.string.onboarding_disclosure_never_title, R.string.onboarding_disclosure_never_body),
            Triple("⌂", R.string.onboarding_disclosure_local_title, R.string.onboarding_disclosure_local_body),
        )
    points.forEachIndexed { i, (mark, title, body) ->
        StatusRow(
            title = stringResource(title),
            detail = stringResource(body),
            divider = i < points.lastIndex,
            leading = { MarkCircle(mark, highlighted = true) },
            modifier = Modifier.staggerIn(index = 1 + i, visible = visible),
        )
    }
}

/** Como el «12 Days planned»: el número gigante es el gráfico, con destellos alrededor. */
@Composable
private fun LimitStep(visible: Boolean, minutes: Int, onChange: (Int) -> Unit) {
    LimitPicker(
        minutes = minutes,
        onChange = onChange,
        numberSize = 112.sp,
        sparkles = true,
        modifier = Modifier.padding(top = 8.dp).staggerIn(index = 1, visible = visible, stepMs = HERO_STEP_MS),
    )
    Title(stringResource(R.string.onboarding_limit_title), visible)
    Body(stringResource(R.string.onboarding_limit_body), visible)
    Body(stringResource(R.string.onboarding_limit_note), visible, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ActivateStep(
    visible: Boolean,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val on = protection == ProtectionState.Running
    Hero(visible) {
        ProtectionSwitch(
            on = on,
            alert = protection == ProtectionState.Stopped,
            onClick = onOpenAccessibilitySettings,
        )
    }
    Title(stringResource(R.string.onboarding_activate_title), visible)
    Body(
        stringResource(if (on) R.string.onboarding_protection_on else R.string.onboarding_activate_body),
        visible,
    )
    Spacer(Modifier.height(12.dp))
    // Android 13+ bloquea la accesibilidad a apps instaladas fuera de una tienda.
    val restricted = protection == ProtectionState.Off && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val xiaomi = showXiaomiTip && protection != ProtectionState.Off
    Column(Modifier.staggerIn(index = 2, visible = visible)) {
        ProtectionRow(
            state = protection,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            divider = restricted || xiaomi,
        )
        if (restricted) {
            StatusRow(
                title = stringResource(R.string.home_xiaomi_tip_open),
                detail = stringResource(R.string.onboarding_restricted_hint),
                divider = xiaomi,
                leading = { MarkCircle("⋮", highlighted = false) },
                trailing = {
                    TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.home_open_short)) }
                },
            )
        }
        if (xiaomi) XiaomiTipRow(onOpenAppSettings = onOpenAppSettings, divider = false)
    }
}

/** Punto de una lista: círculo con su marca y, si está [highlighted], fondo lima suave detrás. */
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

@Composable
private fun PreviewStep(step: Step, protection: ProtectionState = ProtectionState.Off) {
    TouchGrassTheme {
        OnboardingContent(
            state = OnboardingUiState(),
            protection = protection,
            showXiaomiTip = false,
            onNameChange = {},
            onSaveName = {},
            onLimitChange = {},
            onSaveLimit = {},
            onFinish = {},
            onOpenAccessibilitySettings = {},
            onOpenAppSettings = {},
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
private fun DisclosurePreview() = PreviewStep(Step.Disclosure)

@Preview(showSystemUi = true)
@Composable
private fun LimitPreview() = PreviewStep(Step.Limit)

@Preview(showSystemUi = true)
@Composable
private fun ActivatePreview() = PreviewStep(Step.Activate, ProtectionState.Running)
