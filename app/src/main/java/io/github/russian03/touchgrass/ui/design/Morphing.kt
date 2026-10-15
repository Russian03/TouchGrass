package io.github.russian03.touchgrass.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath as morphPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Forma de Material 3 Expressive que se transforma en [polygon] cada vez que cambia, girando
 * [turnDegrees] a la vez, con el muelle del tema. Las formas de `MaterialShapes` miden 1×1 y
 * aquí se escalan al tamaño del lienzo.
 *
 * El trazo de tinta ([outline]) se dibuja desplazado [misprint] respecto al relleno, como una
 * impresión mal registrada: el mismo gesto que el personaje.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingShape(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    outline: Color? = null,
    misprint: Dp = 4.dp,
    turnDegrees: Float = 45f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var from by remember { mutableStateOf(polygon) }
    var to by remember { mutableStateOf(polygon) }
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(polygon) {
        if (polygon === to) return@LaunchedEffect
        from = to
        to = polygon
        if (inspection) return@LaunchedEffect
        progress.snapTo(0f)
        val target = turn.value + turnDegrees
        coroutineScope {
            launch { turn.animateTo(target, spec) }
            progress.animateTo(1f, spec)
        }
    }

    val morph = remember(from, to) { Morph(from, to) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer { rotationZ = turn.value }) {
            path.rewind()
            morph.morphPath(progress.value.coerceIn(0f, 1f), path)
            matrix.reset()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            drawPath(path, color)
            if (outline != null) {
                val shift = misprint.toPx()
                path.translate(Offset(-shift, -shift))
                drawPath(path, outline, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        content()
    }
}
