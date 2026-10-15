package io.github.russian03.touchgrass.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime

/** Bloque lima que llega hasta arriba (bajo la barra de estado) con las esquinas de abajo muy redondas. */
@Composable
fun LimeHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Lime,
        contentColor = Ink,
        shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
            content = content,
        )
    }
}

/**
 * Fila de lista con un icono a la izquierda, título y detalle, y una acción a la derecha, como
 * en «My Goals». Con [divider] se separa de la siguiente con una línea fina.
 */
@Composable
fun StatusRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    divider: Boolean = true,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
            Box(Modifier.padding(end = 12.dp), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing()
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Título de sección sobre el fondo, con el grosor mezclado: «Mi \*semana\*». */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    MixedTitle(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp),
    )
}
