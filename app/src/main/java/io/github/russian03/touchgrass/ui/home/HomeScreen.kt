package io.github.russian03.touchgrass.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetSummary
import io.github.russian03.touchgrass.core.stats.WeekDay
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.common.ProtectionRow
import io.github.russian03.touchgrass.ui.common.SystemSettings
import io.github.russian03.touchgrass.ui.common.XiaomiTipRow
import io.github.russian03.touchgrass.ui.common.fullDay
import io.github.russian03.touchgrass.ui.common.monthName
import io.github.russian03.touchgrass.ui.common.rememberProtectionState
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.LimeHeader
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.OutlinePillButton
import io.github.russian03.touchgrass.ui.design.PulsingDot
import io.github.russian03.touchgrass.ui.design.SectionTitle
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.mixedWeights
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Cream
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenStats: (YearMonth?) -> Unit,
    onOpenUnlock: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val unlock by viewModel.unlock.collectAsStateWithLifecycle()
    val appNames by viewModel.appNames.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSummary.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val claim by viewModel.claim.collectAsStateWithLifecycle()
    val protection = rememberProtectionState()
    // Si la protección no funciona, hoy cuenta como día sin protección.
    LaunchedEffect(protection) {
        if (protection != ProtectionState.Running) viewModel.protectionOff()
    }
    HomeContent(
        protection = protection,
        budget = budget,
        name = name,
        quoteSeed = viewModel.quoteSeed,
        week = week,
        today = viewModel.today(),
        unlock = unlock,
        unlockActions = UnlockActions(appNames = appNames, onOpen = onOpenUnlock, onEnd = viewModel::endUnlock),
        showXiaomiTip = SystemSettings.isXiaomi,
        summary = SummaryActions(pendingSummary, onOpenStats),
        claim = ClaimActions(claim, viewModel::claim),
        onOpenSettings = onOpenSettings,
        onOpenAccessibilitySettings = { SystemSettings.openAccessibility(context) },
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
        onOpenDateSettings = { SystemSettings.openDateTime(context) },
    )
}

/** Resumen del mes listo para ver ([month]) y cómo abrir las estadísticas. */
private class SummaryActions(val month: YearMonth?, val onOpen: (YearMonth?) -> Unit)

/** Días por enseñar en el primer acceso del día ([state]) y cómo guardar que ya se han visto. */
private class ClaimActions(val state: ClaimState?, val onClaimed: () -> Unit)

private class UnlockActions(val appNames: Map<String, String>, val onOpen: () -> Unit, val onEnd: () -> Unit)

/**
 * Inicio. Arriba, la cabecera lima (fecha, título, pedir tiempo o la cuenta atrás); debajo, en
 * cascada: el resumen del mes si está listo, «Mi día», «Mi semana» y «Mi protección».
 */
@Composable
private fun HomeContent(
    protection: ProtectionState,
    budget: BudgetSummary?,
    name: String?,
    quoteSeed: Int,
    week: List<WeekDay>,
    today: LocalDate,
    unlock: UnlockUiState,
    unlockActions: UnlockActions,
    showXiaomiTip: Boolean,
    summary: SummaryActions,
    claim: ClaimActions,
    onOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDateSettings: () -> Unit,
) {
    val openStats = { summary.onOpen(null) }
    // Primer acceso del día: los días por enseñar, si la tira ya ha saltado al pop-up y si ya ha
    // vuelto. Se recuerda para qué días, así que al día siguiente vuelve a salir.
    val claimDays = claim.state?.days.orEmpty()
    var startedFor by remember { mutableStateOf<List<LocalDate>?>(null) }
    var finishedFor by remember { mutableStateOf<List<LocalDate>?>(null) }
    var stripTop by remember { mutableStateOf<Float?>(null) }
    val claiming = claimDays.isNotEmpty() && finishedFor != claimDays
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // La cabecera baja desde arriba; el resto entra en cascada detrás.
            LimeHeader(Modifier.staggerIn(index = 0, offsetY = (-24).dp, fromScale = 1f)) {
                HeaderTop(onOpenStats = openStats, onOpenSettings = onOpenSettings)
                MixedTitle(
                    text =
                    if (name != null) {
                        stringResource(R.string.home_greeting, name)
                    } else {
                        stringResource(R.string.home_greeting_anonymous)
                    },
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                )
                // Las comillas y «Pedir tiempo» van a la misma altura; debajo, la frase del día.
                QuoteRow(
                    budget = budget,
                    onOpenUnlock = unlockActions.onOpen,
                    modifier = Modifier.padding(top = 12.dp, end = 12.dp),
                )
                val quotes = stringArrayResource(R.array.home_quotes)
                Text(
                    text = mixedWeights(quotes[Math.floorMod(quoteSeed, quotes.size)]),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
                if (budget != null) {
                    UnlockSection(
                        budget = budget,
                        state = unlock,
                        appNames = unlockActions.appNames,
                        onEnd = unlockActions.onEnd,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                var order = 1
                summary.month?.let { month ->
                    SummaryReadyCard(
                        month,
                        onOpen = { summary.onOpen(month) },
                        modifier = Modifier.padding(top = 24.dp).staggerIn(order++),
                    )
                }
                if (budget != null) {
                    // Justo bajo el banner, el título empieza donde acaba su curva de la izquierda.
                    SectionTitle(
                        stringResource(R.string.home_my_day),
                        Modifier.staggerIn(order++),
                        top = if (summary.month == null) 4.dp else 32.dp,
                    )
                    TodayCard(budget = budget, modifier = Modifier.staggerIn(order++))
                }
                if (week.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.home_my_week), Modifier.staggerIn(order++))
                    WeekStrip(
                        week = week,
                        today = today,
                        pending = if (claiming) claimDays.toSet() else emptySet(),
                        modifier =
                        Modifier
                            .staggerIn(order++)
                            .onGloballyPositioned { stripTop = it.positionInRoot().y }
                            // Mientras está en el pop-up, aquí no se ve.
                            .graphicsLayer { alpha = if (claiming && startedFor == claimDays) 0f else 1f },
                    )
                }
                SectionTitle(stringResource(R.string.home_my_protection), Modifier.staggerIn(order++))
                Column(Modifier.staggerIn(order++)) {
                    val manualTime = budget?.autoTime == false
                    val xiaomi = showXiaomiTip && protection != ProtectionState.Off
                    ProtectionRow(
                        state = protection,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        divider = manualTime || xiaomi,
                    )
                    if (manualTime) {
                        StatusRow(
                            title = stringResource(R.string.manual_time_title),
                            detail = stringResource(R.string.manual_time_body),
                            divider = xiaomi,
                            leading = { PulsingDot(MaterialTheme.colorScheme.error) },
                            trailing = {
                                TextButton(onClick = onOpenDateSettings) {
                                    Text(stringResource(R.string.home_service_enable_short))
                                }
                            },
                        )
                    }
                    if (xiaomi) XiaomiTipRow(onOpenAppSettings = onOpenAppSettings, divider = false)
                }
                OutlinePillButton(
                    text = stringResource(R.string.home_open_stats),
                    onClick = openStats,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp).staggerIn(order),
                )
            }
        }
        val claimState = claim.state
        if (claiming && claimState != null && week.isNotEmpty()) {
            DayClaim(
                claim = claimState,
                week = week,
                today = today,
                fromTop = stripTop,
                protectionOff = protection != ProtectionState.Running,
                onEnableProtection = onOpenAccessibilitySettings,
                onStart = { startedFor = claimDays },
                onDone = {
                    finishedFor = claimDays
                    claim.onClaimed()
                },
            )
        }
    }
}

/** Fecha de hoy a la izquierda; estadísticas y ajustes a la derecha. */
@Composable
private fun HeaderTop(onOpenStats: () -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = fullDay(LocalDate.now()),
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenStats) {
            Icon(painterResource(R.drawable.ic_stats), contentDescription = stringResource(R.string.stats_title))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
        }
    }
}

/**
 * Comillas grandes a la izquierda, como la cita del diseño, y la píldora «Pedir tiempo» a la
 * derecha, a la misma altura. Con un desbloqueo en marcha la píldora no sale (la cabecera muestra
 * la cuenta atrás) y, si no se puede pedir tiempo, sale apagada.
 */
@Composable
private fun QuoteRow(budget: BudgetSummary?, onOpenUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = "“",
            fontSize = 52.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Bold,
            // El trazo de las comillas está en la parte alta de la línea: se baja un poco más que el centro.
            modifier = Modifier.weight(1f).graphicsLayer { translationY = 20.dp.toPx() },
        )
        if (budget != null && budget.unlock == null) {
            InkPillButton(
                text = stringResource(R.string.unlock_title),
                onClick = onOpenUnlock,
                enabled = unlockBlocker(budget) == null,
                containerColor = Ink,
                contentColor = Cream,
                compact = true,
            )
        }
    }
}

/** El resumen del mes anterior está listo: se queda en Inicio hasta que se abre. */
@Composable
private fun SummaryReadyCard(month: YearMonth, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            MixedTitle(
                text = stringResource(R.string.home_summary_title, "*${monthName(month)}*"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            )
            Text(
                text = stringResource(R.string.home_summary_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            InkPillButton(
                text = stringResource(R.string.home_summary_open),
                onClick = onOpen,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

internal fun previewBudget(used: Int = 10, autoTime: Boolean = true) = BudgetSummary(
    limitMinutes = 30,
    usedMinutes = used,
    remainingMinutes = 30 - used,
    pendingLimitMinutes = null,
    pendingFrom = null,
    autoTime = autoTime,
    nextResetMs = System.currentTimeMillis() + 3_600_000,
    onboardingDone = true,
    unlock = null,
    unlockRemainingMs = 0,
    refundMinutes = 0,
)

private val noActions = UnlockActions(emptyMap(), {}, {})

private val noSummary = SummaryActions(null) {}

private val noClaim = ClaimActions(null) {}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme {
        HomeContent(
            ProtectionState.Running,
            previewBudget(),
            "Jan",
            0,
            previewWeek(),
            LocalDate.of(2026, 10, 14),
            UnlockUiState(),
            noActions,
            true,
            noSummary,
            noClaim,
            {},
            {},
            {},
            {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeStoppedPreview() {
    TouchGrassTheme {
        HomeContent(
            ProtectionState.Stopped,
            previewBudget(used = 30, autoTime = false),
            null,
            1,
            previewWeek(),
            LocalDate.of(2026, 10, 14),
            UnlockUiState(),
            noActions,
            false,
            SummaryActions(YearMonth.of(2026, 9)) {},
            noClaim,
            {},
            {},
            {},
            {},
        )
    }
}
