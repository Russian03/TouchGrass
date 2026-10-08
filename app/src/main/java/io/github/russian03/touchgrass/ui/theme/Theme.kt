package io.github.russian03.touchgrass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Grass,
        onPrimary = Color.White,
        primaryContainer = GrassLight,
        onPrimaryContainer = GrassDeep,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceContainer = Color.White,
        onSurfaceVariant = InkSoft,
        outline = InkMuted,
    )

private val DarkColors =
    darkColorScheme(
        primary = GrassLight,
        onPrimary = GrassDeep,
        primaryContainer = Grass,
        onPrimaryContainer = GrassLight,
        background = Night,
        onBackground = NightInk,
        surface = Night,
        onSurface = NightInk,
        surfaceContainer = NightSurface,
        onSurfaceVariant = NightInkSoft,
        outline = InkMuted,
    )

@Composable
fun TouchGrassTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
