package io.github.russian03.touchgrass.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.AccessibilityStatus
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.design.InkPillButton
import io.github.russian03.touchgrass.ui.design.OutlinePillButton
import io.github.russian03.touchgrass.ui.design.PulsingDot
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.theme.LimeDeep

/** Estado real de la protección, que se vuelve a comprobar al volver de los ajustes del sistema. */
@Composable
fun rememberProtectionState(): ProtectionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AccessibilityStatus.state(context)) }
    LifecycleResumeEffect(Unit) {
        state = AccessibilityStatus.state(context)
        onPauseOrDispose { }
    }
    return state
}

/** Título, detalle y acción de cada estado de la protección. */
private data class ProtectionTexts(@StringRes val title: Int, @StringRes val detail: Int, @StringRes val action: Int)

private fun ProtectionState.texts() = when (this) {
    ProtectionState.Running ->
        ProtectionTexts(R.string.home_service_on, R.string.home_service_on_detail, R.string.home_service_settings)
    ProtectionState.Stopped ->
        ProtectionTexts(
            R.string.home_service_stopped,
            R.string.home_service_stopped_detail,
            R.string.home_service_restart,
        )
    ProtectionState.Off ->
        ProtectionTexts(R.string.home_service_off, R.string.home_service_off_detail, R.string.home_service_enable)
}

/** Verde cuando protege; rojo cuando no. */
@Composable
private fun ProtectionState.color(): Color =
    if (this == ProtectionState.Running) LimeDeep else MaterialTheme.colorScheme.error

/** Tarjeta con el estado de la protección y el botón para ir a Accesibilidad. */
@Composable
fun ProtectionCard(state: ProtectionState, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier = Modifier) {
    val texts = state.texts()
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(state.color())
            Text(
                text = stringResource(texts.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = stringResource(texts.detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (state == ProtectionState.Running) {
            OutlinePillButton(
                text = stringResource(texts.action),
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            InkPillButton(
                text = stringResource(texts.action),
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** La protección como fila de lista: punto que late, estado y, si no protege, la acción para arreglarlo. */
@Composable
fun ProtectionRow(
    state: ProtectionState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    val texts = state.texts()
    StatusRow(
        title = stringResource(texts.title),
        detail = stringResource(texts.detail),
        divider = divider,
        leading = { PulsingDot(state.color()) },
        modifier = modifier,
        trailing = {
            if (state != ProtectionState.Running) {
                TextButton(onClick = onOpenAccessibilitySettings) {
                    Text(stringResource(R.string.home_service_enable_short))
                }
            }
        },
    )
}

/** Consejo para que HyperOS/MIUI no cierre la app. Solo abre la ficha de la app en Ajustes. */
@Composable
fun XiaomiTipRow(onOpenAppSettings: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    StatusRow(
        title = stringResource(R.string.home_xiaomi_tip_title),
        detail = stringResource(R.string.home_xiaomi_tip),
        divider = divider,
        modifier = modifier,
        leading = { PulsingDot(MaterialTheme.colorScheme.outline, size = 8.dp) },
        trailing = { TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.home_open_short)) } },
    )
}
