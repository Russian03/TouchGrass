package io.github.russian03.touchgrass.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R

/** Montserrat va dentro de la app: no se descarga nada. */
val Montserrat =
    FontFamily(
        Font(R.font.montserrat_light, FontWeight.Light),
        Font(R.font.montserrat_regular, FontWeight.Normal),
        Font(R.font.montserrat_semibold, FontWeight.SemiBold),
        Font(R.font.montserrat_bold, FontWeight.Bold),
    )

private val base = Typography()

private fun TextStyle.montserrat(weight: FontWeight, letterSpacing: Float? = null) = copy(
    fontFamily = Montserrat,
    fontWeight = weight,
    letterSpacing = letterSpacing?.sp ?: this.letterSpacing,
)

/** Títulos grandes finos o gruesos (se mezclan en «Mi **día**»), y el resto en Regular o SemiBold. */
val TouchGrassTypography =
    Typography(
        displayLarge = base.displayLarge.montserrat(FontWeight.Bold, -1.5f),
        displayMedium = base.displayMedium.montserrat(FontWeight.Light, -1f),
        displaySmall = base.displaySmall.montserrat(FontWeight.Light, -0.5f),
        headlineLarge = base.headlineLarge.montserrat(FontWeight.Light, -0.5f),
        headlineMedium = base.headlineMedium.montserrat(FontWeight.Light, -0.5f),
        headlineSmall = base.headlineSmall.montserrat(FontWeight.Light),
        titleLarge = base.titleLarge.montserrat(FontWeight.SemiBold),
        titleMedium = base.titleMedium.montserrat(FontWeight.SemiBold),
        titleSmall = base.titleSmall.montserrat(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.montserrat(FontWeight.Normal),
        bodyMedium = base.bodyMedium.montserrat(FontWeight.Normal),
        bodySmall = base.bodySmall.montserrat(FontWeight.Normal),
        labelLarge = base.labelLarge.montserrat(FontWeight.SemiBold),
        labelMedium = base.labelMedium.montserrat(FontWeight.SemiBold),
        labelSmall = base.labelSmall.montserrat(FontWeight.Normal),
    )
