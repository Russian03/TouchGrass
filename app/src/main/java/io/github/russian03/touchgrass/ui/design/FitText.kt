package io.github.russian03.touchgrass.ui.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Lo más pequeño que se deja encoger un texto que se ajusta. */
private val MIN_FIT_SIZE = 12.sp

/**
 * Texto de una sola línea que, si no cabe, encoge hasta caber en lugar de partirse. Es para los
 * números y tiempos grandes («7 h 30 min»): la etiqueta que los acompaña va siempre debajo, en su
 * propia línea, nunca al lado.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val max: TextUnit = if (style.fontSize.isSpecified) style.fontSize else LocalTextStyle.current.fontSize
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    BasicText(
        text = text,
        style = style.copy(color = tint, textAlign = textAlign, lineHeight = TextUnit.Unspecified),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_FIT_SIZE, maxFontSize = max),
        modifier = modifier,
    )
}
