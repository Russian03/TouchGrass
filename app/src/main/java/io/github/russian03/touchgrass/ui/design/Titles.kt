package io.github.russian03.touchgrass.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle

/**
 * Título que mezcla grosores, como «My **Week**»: lo que va entre asteriscos en [text]
 * («Mi \*día\*») sale en negrita y el resto, con el grosor fino de [style].
 */
@Composable
fun MixedTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val annotated = remember(text) { mixedWeights(text) }
    Text(
        text = annotated,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier.semantics { heading() },
    )
}

/** «Mi \*día\*» → «Mi » fino y «día» en negrita. Un asterisco sin pareja se queda tal cual. */
fun mixedWeights(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split('*')
    if (parts.size % 2 == 0) {
        append(text)
        return@buildAnnotatedString
    }
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
