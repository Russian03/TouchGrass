package io.github.russian03.touchgrass.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.AccessibilityStatus
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.theme.GrassDeep
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ProtectionState.Off) }

    // Se vuelve a comprobar cada vez que el usuario regresa de los ajustes del sistema.
    LifecycleResumeEffect(Unit) {
        state = AccessibilityStatus.state(context)
        onPauseOrDispose { }
    }

    HomeContent(
        state = state,
        // En Xiaomi/Redmi/POCO el sistema cierra apps en segundo plano con mucha facilidad.
        showXiaomiTip = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true),
        onOpenAccessibilitySettings = {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onOpenAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: ProtectionState,
    showXiaomiTip: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar() },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            StatusCard(
                state = state,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 40.dp),
            )
            if (showXiaomiTip && state != ProtectionState.Off) {
                XiaomiTip(onOpenAppSettings = onOpenAppSettings, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun TopBar() {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 2.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).align(Alignment.CenterStart),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primary else GrassDeep,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: ProtectionState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
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
}

/** Consejo para que HyperOS/MIUI no cierre la app. Solo abre la ficha de la app en Ajustes. */
@Composable
private fun XiaomiTip(onOpenAppSettings: () -> Unit, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun HomeOffPreview() {
    TouchGrassTheme { HomeContent(ProtectionState.Off, showXiaomiTip = false, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme { HomeContent(ProtectionState.Running, showXiaomiTip = true, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeStoppedPreview() {
    TouchGrassTheme { HomeContent(ProtectionState.Stopped, showXiaomiTip = true, {}, {}) }
}
