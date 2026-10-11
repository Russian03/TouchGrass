package io.github.russian03.touchgrass.ui.home

import android.content.Intent
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
import io.github.russian03.touchgrass.ui.theme.GrassDeep
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }

    // Se vuelve a comprobar cada vez que el usuario regresa de los ajustes del sistema.
    LifecycleResumeEffect(Unit) {
        serviceEnabled = AccessibilityStatus.isServiceEnabled(context)
        onPauseOrDispose { }
    }

    HomeContent(
        serviceEnabled = serviceEnabled,
        onOpenAccessibilitySettings = {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
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
                serviceEnabled = serviceEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 40.dp),
            )
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
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                    text = stringResource(if (serviceEnabled) R.string.home_service_on else R.string.home_service_off),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text =
                stringResource(
                    if (serviceEnabled) R.string.home_service_on_detail else R.string.home_service_off_detail,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (serviceEnabled) {
                OutlinedButton(
                    onClick = onOpenAccessibilitySettings,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.home_service_settings))
                }
            } else {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.home_service_enable))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeOffPreview() {
    TouchGrassTheme { HomeContent(serviceEnabled = false, onOpenAccessibilitySettings = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeOnPreview() {
    TouchGrassTheme { HomeContent(serviceEnabled = true, onOpenAccessibilitySettings = {}) }
}
