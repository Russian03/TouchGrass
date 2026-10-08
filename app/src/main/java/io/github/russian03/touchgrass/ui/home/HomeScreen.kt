package io.github.russian03.touchgrass.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.AccessibilityStatus
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
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(if (serviceEnabled) R.string.home_service_on else R.string.home_service_off),
                style = MaterialTheme.typography.titleMedium,
                color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 32.dp),
            )
            if (serviceEnabled) {
                OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.home_service_settings))
                }
            } else {
                Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.padding(top = 16.dp)) {
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
