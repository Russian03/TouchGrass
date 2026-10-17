package io.github.russian03.touchgrass.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Recuadro de color de «Mis logros» y de las estadísticas: esquinas de 28 dp, como las tarjetas, y
 * el contenido en [contentColor]. Con [onClick] se puede pulsar entero y encoge un poco al hacerlo;
 * [interaction] deja saber desde fuera si se está pulsando.
 */
@Composable
fun Tile(
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val press =
        if (onClick == null) {
            Modifier
        } else {
            Modifier
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier
                .then(press)
                .clip(MaterialTheme.shapes.large)
                .background(color)
                .padding(20.dp),
            content = content,
        )
    }
}
