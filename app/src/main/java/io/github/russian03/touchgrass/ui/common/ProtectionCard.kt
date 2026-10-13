package io.github.russian03.touchgrass.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.AccessibilityStatus
import io.github.russian03.touchgrass.service.ProtectionState

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

@Composable
fun ProtectionCard(state: ProtectionState, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier = Modifier) {
    val running = state == ProtectionState.Running
    val accent = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val (title, detail, action) =
        when (state) {
            ProtectionState.Running ->
                Triple(R.string.home_service_on, R.string.home_service_on_detail, R.string.home_service_settings)
            ProtectionState.Stopped ->
                Triple(
                    R.string.home_service_stopped,
                    R.string.home_service_stopped_detail,
                    R.string.home_service_restart,
                )
            ProtectionState.Off ->
                Triple(R.string.home_service_off, R.string.home_service_off_detail, R.string.home_service_enable)
        }
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            text = stringResource(detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (running) {
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(action))
            }
        } else {
            Button(
                onClick = onOpenAccessibilitySettings,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(action))
            }
        }
    }
}

/** Consejo para que HyperOS/MIUI no cierre la app. Solo abre la ficha de la app en Ajustes. */
@Composable
fun XiaomiTip(onOpenAppSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.home_xiaomi_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOpenAppSettings) {
            Text(stringResource(R.string.home_xiaomi_tip_open))
        }
    }
}
