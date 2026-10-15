package io.github.russian03.touchgrass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Ink,
        onPrimary = Cream,
        primaryContainer = Lime,
        onPrimaryContainer = Ink,
        secondary = LimeDeep,
        onSecondary = Ink,
        secondaryContainer = LimeSoft,
        onSecondaryContainer = Ink,
        tertiary = LimeDeep,
        background = Cream,
        onBackground = Ink,
        surface = Cream,
        onSurface = Ink,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = LimeSoft,
        onSurfaceVariant = InkSoft,
        outline = InkMuted,
        outlineVariant = InkMuted.copy(alpha = 0.3f),
        error = Alert,
    )

private val DarkColors =
    darkColorScheme(
        primary = Lime,
        onPrimary = Ink,
        primaryContainer = Lime,
        onPrimaryContainer = Ink,
        secondary = Lime,
        onSecondary = Ink,
        secondaryContainer = LimeDeep.copy(alpha = 0.35f),
        onSecondaryContainer = NightInk,
        tertiary = Lime,
        background = Night,
        onBackground = NightInk,
        surface = Night,
        onSurface = NightInk,
        surfaceContainerLowest = NightSurface,
        surfaceContainerLow = NightSurface,
        surfaceContainer = NightSurface,
        surfaceContainerHigh = NightSurface,
        surfaceContainerHighest = NightSurface,
        onSurfaceVariant = NightInkSoft,
        outline = InkMuted,
        outlineVariant = InkMuted.copy(alpha = 0.3f),
        error = Alert,
    )

/**
 * Tema de la app: Material 3 Expressive con la paleta lima, Montserrat, esquinas grandes y
 * movimiento con muelles ([MotionScheme.expressive]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TouchGrassTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        shapes = TouchGrassShapes,
        typography = TouchGrassTypography,
        content = content,
    )
}
