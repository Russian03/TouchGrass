package io.github.russian03.touchgrass.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.Lime

/** Radio de las dos esquinas de abajo del banner. */
private val CornerRadius = 40.dp

/**
 * Borde inferior con las esquinas en sentidos opuestos: la derecha se redondea hacia arriba (como
 * una esquina normal) y la izquierda baja con una curva hacia dentro, prolongando el banner por
 * ese lado.
 */
class OppositeCornersShape(private val radius: Dp = CornerRadius) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }
        val bottom = h - r
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                // Derecha: esquina redondeada hacia arriba.
                lineTo(w, bottom - r)
                quadraticTo(w, bottom, w - r, bottom)
                lineTo(r, bottom)
                // Izquierda: curva hacia dentro que baja hasta el borde.
                quadraticTo(0f, bottom, 0f, h)
                close()
            }
        return Outline.Generic(path)
    }
}

/** Bloque lima que llega hasta arriba (bajo la barra de estado), con las esquinas de abajo en sentidos opuestos. */
@Composable
fun LimeHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Lime,
        contentColor = Ink,
        shape = OppositeCornersShape(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = CornerRadius + 24.dp),
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

/**
 * Título de sección sobre el fondo, con el grosor mezclado: «Mi \*semana\*». [top] es el aire
 * que lo separa de lo de arriba.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, top: Dp = 32.dp) {
    MixedTitle(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.fillMaxWidth().padding(top = top, bottom = 12.dp),
    )
}
