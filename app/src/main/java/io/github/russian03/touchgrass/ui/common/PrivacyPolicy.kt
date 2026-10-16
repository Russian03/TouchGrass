package io.github.russian03.touchgrass.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/** Apartados de la política: título y texto. Es la misma que `docs/PRIVACIDAD.md`. */
private val Sections =
    listOf(
        R.string.privacy_service_title to R.string.privacy_service_body,
        R.string.privacy_data_title to R.string.privacy_data_body,
        R.string.privacy_share_title to R.string.privacy_share_body,
        R.string.privacy_delete_title to R.string.privacy_delete_body,
        R.string.privacy_contact_title to R.string.privacy_contact_body,
    )

/**
 * Política de privacidad a pantalla completa. Va dentro de la app (que no tiene Internet) y se abre
 * desde el aviso del servicio de accesibilidad y desde Ajustes.
 */
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        TouchGrassTheme { PrivacyPolicy(onBack = onDismiss) }
    }
}

@Composable
private fun PrivacyPolicy(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            TopBar(title = stringResource(R.string.privacy_open), onBack = onBack)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.privacy_updated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.privacy_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Sections.forEach { (title, body) ->
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp).semantics { heading() },
                    )
                    Text(
                        text = stringResource(body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PrivacyPolicyPreview() {
    TouchGrassTheme { PrivacyPolicy(onBack = {}) }
}
