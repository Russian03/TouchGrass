package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Difumina los bordes de algo que se desliza por el lado en que queda más por ver, para que se
 * entienda que hay más. Cada lado aparece o se va con un fundido según la posición del [scroll].
 * Va antes del `horizontalScroll`/`verticalScroll` en la cadena de modificadores.
 */
@Composable
fun Modifier.fadingEdges(scroll: ScrollState, orientation: Orientation, width: Dp = 40.dp): Modifier {
    // Solo importa si queda algo a cada lado, no la posición exacta: así no se recompone en cada píxel.
    val moreBefore by remember(scroll) { derivedStateOf { scroll.value > 0 } }
    val moreAfter by remember(scroll) { derivedStateOf { scroll.value < scroll.maxValue } }
    val start by animateFloatAsState(if (moreBefore) 1f else 0f, label = "fadeStart")
    val end by animateFloatAsState(if (moreAfter) 1f else 0f, label = "fadeEnd")
    val horizontal = orientation == Orientation.Horizontal
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = width.toPx()
            val length = if (horizontal) size.width else size.height
            fun brush(from: Color, to: Color, a: Float, b: Float) = if (horizontal) {
                Brush.horizontalGradient(listOf(from, to), startX = a, endX = b)
            } else {
                Brush.verticalGradient(listOf(from, to), startY = a, endY = b)
            }
            val band = if (horizontal) Size(fade, size.height) else Size(size.width, fade)
            // Se borra lo que queda debajo del degradado (DstOut): opaco en el borde, nada hacia dentro.
            if (start > 0f) {
                drawRect(
                    brush(Color.Black.copy(alpha = start), Color.Transparent, 0f, fade),
                    size = band,
                    blendMode = BlendMode.DstOut,
                )
            }
            if (end > 0f) {
                drawRect(
                    brush(Color.Transparent, Color.Black.copy(alpha = end), length - fade, length),
                    topLeft = if (horizontal) Offset(length - fade, 0f) else Offset(0f, length - fade),
                    size = band,
                    blendMode = BlendMode.DstOut,
                )
            }
        }
}
