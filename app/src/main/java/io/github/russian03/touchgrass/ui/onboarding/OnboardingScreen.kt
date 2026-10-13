package io.github.russian03.touchgrass.ui.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.PrimaryButton
import io.github.russian03.touchgrass.ui.common.ProtectionCard
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTip
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import kotlinx.coroutines.launch

private enum class Step { Welcome, HowItWorks, Disclosure, Limit, Activate }

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnboardingContent(
        state = state,
        protection = rememberProtectionState(),
        showXiaomiTip = SystemSettings.isXiaomi,
        onLimitChange = viewModel::onLimitChange,
        onSaveLimit = viewModel::saveLimit,
        onFinish = { viewModel.finish(onFinished) },
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    protection: ProtectionState,
    showXiaomiTip: Boolean,
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
    val goTo: (Int) -> Unit = { page -> scope.launch { pager.animateScrollToPage(page) } }
    val next = { goTo(pager.currentPage + 1) }

    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            StepDots(count = steps.size, current = pager.currentPage, modifier = Modifier.padding(top = 24.dp))
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (steps[page]) {
                        Step.Welcome -> WelcomeStep()
                        Step.HowItWorks -> HowItWorksStep()
                        Step.Disclosure -> DisclosureStep()
                        Step.Limit -> LimitStep(state.limitMinutes, onLimitChange)
                        Step.Activate -> ActivateStep(
                            protection,
                            showXiaomiTip,
                            onOpenAccessibilitySettings,
                            onOpenAppSettings,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
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
                PrimaryButton(text = stringResource(label), onClick = action, enabled = !state.saving)
                // Ocupa su sitio siempre, para que el botón principal no salte entre páginas.
                TextButton(
                    onClick = { goTo(pager.currentPage - 1) },
                    enabled = pager.currentPage > 0,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (pager.currentPage > 0) stringResource(R.string.back) else "")
                }
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, current + 1, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            this.contentDescription = description
        },
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 20.dp else 6.dp, label = "dot$i")
            val color by animateColorAsState(
                if (i <=
                    current
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                label = "dotColor$i",
            )
            Box(Modifier.height(6.dp).width(width).background(color, CircleShape))
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(top = 12.dp),
    )
}

@Composable
private fun WelcomeStep() {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        modifier = Modifier.size(72.dp),
    )
    Spacer(Modifier.height(32.dp))
    Title(stringResource(R.string.onboarding_welcome_title))
    Body(stringResource(R.string.onboarding_welcome_body))
}

@Composable
private fun HowItWorksStep() {
    Title(stringResource(R.string.onboarding_how_title))
    Spacer(Modifier.height(24.dp))
    Point("1", R.string.onboarding_how_block_title, R.string.onboarding_how_block_body)
    Point("2", R.string.onboarding_how_budget_title, R.string.onboarding_how_budget_body)
    Point("3", R.string.onboarding_how_unlock_title, R.string.onboarding_how_unlock_body)
}

/** Aviso destacado que exige Google Play antes de pedir el servicio de accesibilidad. */
@Composable
private fun DisclosureStep() {
    Title(stringResource(R.string.onboarding_disclosure_title))
    Body(stringResource(R.string.onboarding_disclosure_intro))
    Spacer(Modifier.height(20.dp))
    Point("✓", R.string.onboarding_disclosure_reads_title, R.string.onboarding_disclosure_reads_body)
    Point("✕", R.string.onboarding_disclosure_never_title, R.string.onboarding_disclosure_never_body)
    Point("⌂", R.string.onboarding_disclosure_local_title, R.string.onboarding_disclosure_local_body)
}

@Composable
private fun LimitStep(minutes: Int, onChange: (Int) -> Unit) {
    Title(stringResource(R.string.onboarding_limit_title))
    Body(stringResource(R.string.onboarding_limit_body))
    Spacer(Modifier.height(28.dp))
    LimitPicker(minutes = minutes, onChange = onChange)
    Body(stringResource(R.string.onboarding_limit_note), modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ActivateStep(
    protection: ProtectionState,
    showXiaomiTip: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Title(stringResource(R.string.onboarding_activate_title))
    Body(stringResource(R.string.onboarding_activate_body))
    ProtectionCard(
        state = protection,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        modifier = Modifier.padding(top = 24.dp),
    )
    // Android 13+ bloquea la accesibilidad a apps instaladas fuera de una tienda.
    if (protection == ProtectionState.Off && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Text(
            text = stringResource(R.string.onboarding_restricted_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.home_xiaomi_tip_open)) }
    }
    if (showXiaomiTip && protection != ProtectionState.Off) {
        XiaomiTip(onOpenAppSettings = onOpenAppSettings, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun Point(mark: String, title: Int, body: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                mark,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
private fun PreviewStep(step: Step) {
    TouchGrassTheme {
        OnboardingContent(
            state = OnboardingUiState(),
            protection = ProtectionState.Off,
            showXiaomiTip = false,
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
private fun DisclosurePreview() = PreviewStep(Step.Disclosure)

@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LimitPreview() = PreviewStep(Step.Limit)
