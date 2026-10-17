package io.github.russian03.touchgrass.ui.settings

import android.content.res.Configuration
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.BuildConfig
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.BeigeCard
import io.github.russian03.touchgrass.ui.common.LimitPicker
import io.github.russian03.touchgrass.ui.common.PrivacyPolicyDialog
import io.github.russian03.touchgrass.ui.common.ProtectionActions
import io.github.russian03.touchgrass.ui.common.ProtectionCard
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.beigeCardColor
import io.github.russian03.touchgrass.ui.common.duration
import io.github.russian03.touchgrass.ui.common.firstDayOf
import io.github.russian03.touchgrass.ui.common.rememberActivateProtection
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.common.rememberShownProtection
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.SectionTitle
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.home.previewBudget
import io.github.russian03.touchgrass.ui.theme.CardBeigeEdge
import io.github.russian03.touchgrass.ui.theme.NightPointShade
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    focusProtection: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val protection = rememberProtectionState()
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    SettingsContent(
        state = state,
        protection = protection,
        focusProtection = focusProtection,
        showXiaomiTip = SystemSettings.isXiaomi,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onSaveName = viewModel::saveName,
        onDraftChange = viewModel::onDraftChange,
        onSave = viewModel::save,
        onCancelPending = viewModel::cancelPending,
        protectionActions =
        ProtectionActions(
            // Pasa antes por el aviso del servicio si aún no se ha aceptado.
            onOpenAccessibilitySettings = rememberActivateProtection(protection),
            onOpenAppSettings = { SystemSettings.openAppDetails(context) },
            onOpenDateSettings = { SystemSettings.openDateTime(context) },
        ),
        onOpenPrivacy = { showPolicy = true },
    )
    if (showPolicy) PrivacyPolicyDialog(onDismiss = { showPolicy = false })
}

/** Espera a que acabe el paso del banner antes de bajar hasta la protección, para que se vea bajar. */
private const val FOCUS_DELAY_MS = 450L
private const val FOCUS_SCROLL_MS = 800

/** Medio parpadeo de la tarjeta de la protección: muy lento, para que se vea que es lo que hay que tocar. */
private const val PULSE_HALF_MS = 1_600

/**
 * Ajustes, con el estilo de Inicio: el banner lima con el título (el mismo de Inicio, que encoge al
 * llegar) y debajo, en cascada, los apartados en tarjetas beige. Con [focusProtection] se baja
 * hasta la protección y su tarjeta parpadea muy despacio hasta que vuelve a funcionar.
 */
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    protection: ProtectionState,
    focusProtection: Boolean,
    showXiaomiTip: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onDraftChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancelPending: () -> Unit,
    protectionActions: ProtectionActions,
    onOpenPrivacy: () -> Unit,
) {
    var confirmLower by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // La lista espera a tener los datos: si no, la tarjeta del límite aparecería de golpe a media
    // transición y la lista daría un salto (y la protección no estaría aún en su sitio).
    val loaded = state.budget != null
    // Altura del apartado de la protección (lo que ocupa el banner más su sitio en la lista), para
    // bajar hasta ahí. Se hace una sola vez, aunque se gire la pantalla.
    var listTop by remember { mutableIntStateOf(-1) }
    var protectionTop by remember { mutableIntStateOf(-1) }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusProtection, loaded) {
        if (!focusProtection || focused || !loaded) return@LaunchedEffect
        // Con los datos ya puestos, espera a que la lista tenga su altura de verdad.
        val target =
            snapshotFlow { Triple(listTop, protectionTop, scroll.maxValue) }
                .first { (list, section, max) -> list >= 0 && section >= 0 && max > 0 }
                .let { (list, section, max) -> (list + section).coerceAtMost(max) }
        delay(FOCUS_DELAY_MS)
        focused = true
        scroll.animateScrollTo(target, tween(FOCUS_SCROLL_MS, easing = FastOutSlowInEasing))
    }
    // Si se ha llegado para arreglar la protección, su tarjeta parpadea muy despacio hasta que funcione.
    // Con el margen de los cortes: uno breve se enseña como «Reconectando…» y no parpadea.
    val shown = rememberShownProtection(protection)
    val pulsing = focusProtection && shown != ProtectionState.Running
    val dark = isSystemInDarkTheme()
    val beige = beigeCardColor()
    val peak = if (dark) NightPointShade else CardBeigeEdge
    val pulse = rememberInfiniteTransition(label = "protectionPulse")
    val pulseColor by pulse.animateColor(
        initialValue = beige,
        targetValue = peak,
        animationSpec = infiniteRepeatable(tween(PULSE_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "protectionPulseColor",
    )
    val protectionColor = if (pulsing) pulseColor else beige
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding()) {
            LimeHeader {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                MixedTitle(
                    text = stringResource(R.string.settings_header),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
            }

            Column(
                Modifier
                    .onPlaced { listTop = it.positionInParent().y.toInt() }
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                var order = 1
                // Justo bajo el banner, el título empieza donde acaba su curva de la izquierda.
                SectionTitle(
                    stringResource(R.string.settings_name_section),
                    Modifier.staggerIn(order++, loaded),
                    top = 4.dp,
                )
                NameCard(state, onNameChange, onSaveName, Modifier.staggerIn(order++, loaded))

                SectionTitle(stringResource(R.string.settings_limit_section), Modifier.staggerIn(order++, loaded))
                LimitCard(
                    state = state,
                    onDraftChange = onDraftChange,
                    onSave = { if (state.lowers) confirmLower = true else onSave() },
                    onCancelPending = onCancelPending,
                    modifier = Modifier.staggerIn(order++, loaded),
                )

                SectionTitle(
                    stringResource(R.string.settings_protection_section),
                    Modifier
                        .onPlaced { protectionTop = it.positionInParent().y.toInt() }
                        .staggerIn(order++, loaded),
                )
                ProtectionCard(
                    state = shown,
                    reconnecting = protection == ProtectionState.Stopped && shown == ProtectionState.Running,
                    manualTime = state.budget?.autoTime == false,
                    showXiaomiTip = showXiaomiTip,
                    actions = protectionActions,
                    color = protectionColor,
                    modifier = Modifier.staggerIn(order++, loaded),
                )

                SectionTitle(stringResource(R.string.settings_privacy_section), Modifier.staggerIn(order++, loaded))
                BeigeCard(Modifier.staggerIn(order++, loaded)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChunkyPillButton(
                        text = stringResource(R.string.privacy_open),
                        onClick = onOpenPrivacy,
                        containerColor = MaterialTheme.colorScheme.background,
                        edgeColor = CardBeigeEdge,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).staggerIn(order, loaded),
                )
            }
        }
    }

    if (confirmLower) {
        AlertDialog(
            onDismissRequest = { confirmLower = false },
            title = { Text(stringResource(R.string.settings_lower_title, duration(state.shownLimit))) },
            text = { Text(stringResource(R.string.settings_lower_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLower = false
                    onSave()
                }) { Text(stringResource(R.string.settings_lower_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLower = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Nombre del saludo de Inicio: se escribe y se guarda; vacío lo quita. */
@Composable
private fun NameCard(
    state: SettingsUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BeigeCard(modifier) {
        OutlinedTextField(
            value = state.shownName,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
            keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canSaveName) onSave() }),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_name_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        ChunkyPillButton(
            text = stringResource(R.string.settings_name_save),
            onClick = onSave,
            enabled = state.canSaveName,
            modifier = Modifier.padding(top = 20.dp),
        )
        state.nameSaved?.let { ok ->
            Text(
                text = stringResource(if (ok) R.string.settings_name_saved else R.string.error_saving),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LimitCard(
    state: SettingsUiState,
    onDraftChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancelPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val budget = state.budget ?: return
    val nextMonth = firstDayOf(YearMonth.now().plusMonths(1))
    BeigeCard(modifier) {
        Text(
            text = stringResource(R.string.settings_limit_current, duration(budget.limitMinutes)),
            style = MaterialTheme.typography.bodyLarge,
        )
        LimitPicker(
            minutes = state.shownLimit,
            onChange = onDraftChange,
            modifier = Modifier.padding(top = 20.dp),
        )
        val hint =
            when {
                state.draft == null || state.draft == budget.limitMinutes -> stringResource(
                    R.string.settings_limit_rule,
                )
                state.lowers -> stringResource(R.string.settings_limit_applies_now)
                else -> stringResource(R.string.settings_limit_applies_from, nextMonth)
            }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        state.result?.let { result ->
            val colors = MaterialTheme.colorScheme
            Text(
                // Al programar un cambio, la fecha buena es la que ha guardado la política.
                text = resultText(result, budget.pendingFrom?.let(::firstDayOf) ?: nextMonth),
                style = MaterialTheme.typography.bodySmall,
                color = if (result is LimitChange.Failed) colors.error else colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        ChunkyPillButton(
            text = stringResource(R.string.settings_limit_save),
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.padding(top = 20.dp),
        )
        val pending = budget.pendingLimitMinutes
        val from = budget.pendingFrom
        if (pending != null && from != null) {
            Text(
                text = stringResource(R.string.limit_pending, firstDayOf(from), duration(pending)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp),
            )
            ChunkyPillButton(
                text = stringResource(R.string.settings_limit_cancel_pending),
                onClick = onCancelPending,
                enabled = !state.saving,
                containerColor = MaterialTheme.colorScheme.background,
                edgeColor = CardBeigeEdge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun resultText(result: LimitChange, nextMonth: String): String = when (result) {
    LimitChange.Applied -> stringResource(R.string.settings_limit_applied)
    LimitChange.Scheduled -> stringResource(R.string.settings_limit_scheduled, nextMonth)
    LimitChange.Cancelled -> stringResource(R.string.settings_limit_cancelled)
    is LimitChange.Failed ->
        when (result.reason) {
            Denial.ManualTime -> stringResource(R.string.settings_limit_manual_time)
            else -> stringResource(R.string.error_saving)
        }
}

@Preview(showSystemUi = true, heightDp = 1600)
@Composable
private fun SettingsPreview() {
    TouchGrassTheme {
        SettingsContent(
            state = SettingsUiState(budget = previewBudget(autoTime = false), draft = 45),
            protection = ProtectionState.Off,
            focusProtection = false,
            showXiaomiTip = false,
            onBack = {},
            onNameChange = {},
            onSaveName = {},
            onDraftChange = {},
            onSave = {},
            onCancelPending = {},
            protectionActions = ProtectionActions({}, {}, {}),
            onOpenPrivacy = {},
        )
    }
}

@Preview(showSystemUi = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPendingPreview() {
    TouchGrassTheme {
        SettingsContent(
            state = SettingsUiState(
                budget = previewBudget().copy(
                    pendingLimitMinutes = BudgetPolicy.MAX_LIMIT_MINUTES / 2,
                    pendingFrom = YearMonth.now().plusMonths(1),
                ),
            ),
            protection = ProtectionState.Off,
            focusProtection = false,
            showXiaomiTip = false,
            onBack = {},
            onNameChange = {},
            onSaveName = {},
            onDraftChange = {},
            onSave = {},
            onCancelPending = {},
            protectionActions = ProtectionActions({}, {}, {}),
            onOpenPrivacy = {},
        )
    }
}
