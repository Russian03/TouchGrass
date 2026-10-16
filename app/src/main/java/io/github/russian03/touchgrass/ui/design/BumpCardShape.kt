package io.github.russian03.touchgrass.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tarjeta con un bulto redondo en el centro del borde de arriba, donde se asoma el personaje. El
 * bulto es un círculo de radio [bump] que toca el borde de arriba de la forma; la tarjeta empieza
 * [rise] más abajo. Las uniones con el borde se suavizan con un poco de curva ([fillet]).
 */
class BumpCardShape(
    private val bump: Dp = 56.dp,
    private val rise: Dp = 40.dp,
    private val corner: Dp = 28.dp,
    private val fillet: Dp = 14.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path =
            with(density) {
                bumpPath(size, bump.toPx(), rise.toPx(), corner.toPx(), fillet.toPx())
            }
        return Outline.Generic(path)
    }
}

private fun bumpPath(size: Size, r: Float, rise: Float, corner: Float, fillet: Float): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = r
    // Donde el círculo corta el borde de arriba de la tarjeta.
    val dx = sqrt((r * r - (cy - rise) * (cy - rise)).coerceAtLeast(0f))
    val left = Math.toDegrees(atan2((rise - cy).toDouble(), (-dx).toDouble())).toFloat().let {
        if (it <
            0
        ) {
            it + 360
        } else {
            it
        }
    }
    val right = Math.toDegrees(atan2((rise - cy).toDouble(), dx.toDouble())).toFloat().let {
        if (it <
            0
        ) {
            it + 360
        } else {
            it
        }
    }
    // Se empieza y se acaba el arco un poco dentro, para la curva de unión.
    val inset = Math.toDegrees((fillet / r).toDouble()).toFloat()
    fun onCircle(deg: Float) = Offset(
        cx + r * cos(Math.toRadians(deg.toDouble())).toFloat(),
        cy + r * sin(Math.toRadians(deg.toDouble())).toFloat(),
    )
    val start = onCircle(left + inset)
    val circle = Rect(cx - r, cy - r, cx + r, cy + r)
    return Path().apply {
        moveTo(0f, rise + corner)
        arcTo(Rect(0f, rise, corner * 2, rise + corner * 2), 180f, 90f, false)
        lineTo(cx - dx - fillet, rise)
        quadraticTo(cx - dx, rise, start.x, start.y)
        arcTo(circle, left + inset, (right - inset) - (left + inset), false)
        quadraticTo(cx + dx, rise, cx + dx + fillet, rise)
        lineTo(w - corner, rise)
        arcTo(Rect(w - corner * 2, rise, w, rise + corner * 2), 270f, 90f, false)
        lineTo(w, h - corner)
        arcTo(Rect(w - corner * 2, h - corner * 2, w, h), 0f, 90f, false)
        lineTo(corner, h)
        arcTo(Rect(0f, h - corner * 2, corner * 2, h), 90f, 90f, false)
        close()
    }
}
