package io.github.russian03.touchgrass.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.data.profile.ProfileRepository
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.design.staggerIn
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Botón secundario sobre fondo oscuro: gris noche con su canto, como «Volver» en el pop-up del día. */
val DarkChunky = Color(0xFF2E302B)
val DarkChunkyEdge = Color(0xFF0B0C0A)

/**
 * Aviso destacado que exige Google Play antes de pedir el servicio de accesibilidad: qué mira, qué
 * nunca lee y que nada sale del móvil, con el enlace a la política de privacidad.
 */
@Composable
fun DisclosurePoints(visible: Boolean = true, firstIndex: Int = 1) {
    var showPolicy by rememberSaveable { mutableStateOf(false) }
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
            leading = { MarkDot(mark) },
            modifier = Modifier.staggerIn(index = firstIndex + i, visible = visible),
        )
    }
    Text(
        text = stringResource(R.string.onboarding_disclosure_consent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).staggerIn(index = firstIndex + points.size, visible),
    )
    TextButton(
        onClick = { showPolicy = true },
        modifier = Modifier.fillMaxWidth().staggerIn(index = firstIndex + points.size, visible = visible),
    ) {
        Text(stringResource(R.string.privacy_open))
    }
    if (showPolicy) PrivacyPolicyDialog(onDismiss = { showPolicy = false })
}

/** Círculo lima con la marca de cada punto del aviso. */
@Composable
private fun MarkDot(mark: String) {
    Box(Modifier.size(32.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
        Text(mark, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Consentimiento del servicio de accesibilidad: lo pide cualquier «Activar» de la app si aún no se
 * ha dado. Ir atrás cuenta como no aceptar.
 */
@Composable
fun ConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        TouchGrassTheme(darkTheme = true) { ConsentContent(onAccept, onDecline) }
    }
}

@Composable
private fun ConsentContent(onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MixedTitle(
                    text = stringResource(R.string.onboarding_disclosure_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_disclosure_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                DisclosurePoints()
            }
            ChunkyPillButton(
                text = stringResource(R.string.onboarding_disclosure_decline),
                onClick = onDecline,
                containerColor = DarkChunky,
                edgeColor = DarkChunkyEdge,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )
            ChunkyPillButton(
                text = stringResource(R.string.onboarding_disclosure_accept),
                onClick = onAccept,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Si ya se ha aceptado el aviso del servicio de accesibilidad. */
@HiltViewModel
class ConsentViewModel @Inject constructor(private val profile: ProfileRepository) : ViewModel() {

    /** `null` mientras se carga. */
    val consent: StateFlow<Boolean?> =
        profile.profile
            .map { it.accessibilityConsent }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun accept() {
        viewModelScope.launch { profile.acceptAccessibility() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Lo que hace cualquier «Activar» de la protección: con el aviso ya aceptado, abre Accesibilidad;
 * si no, enseña antes el aviso y solo abre Accesibilidad al aceptarlo. Quien ya tiene la
 * protección funcionando lo aceptó en una versión anterior: se guarda sin volver a preguntar.
 */
@Composable
fun rememberActivateProtection(protection: ProtectionState): () -> Unit {
    val viewModel: ConsentViewModel = hiltViewModel()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(protection, consent) {
        if (protection == ProtectionState.Running && consent == false) viewModel.accept()
    }
    if (asking) {
        ConsentDialog(
            onAccept = {
                asking = false
                viewModel.accept()
                SystemSettings.openAccessibility(context)
            },
            onDecline = { asking = false },
        )
    }
    return {
        if (consent == true) SystemSettings.openAccessibility(context) else asking = true
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ConsentPreview() {
    TouchGrassTheme(darkTheme = true) { ConsentContent({}, {}) }
}
