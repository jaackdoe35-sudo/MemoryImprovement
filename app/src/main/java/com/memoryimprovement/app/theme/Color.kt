package com.memoryimprovement.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Calm Editorial Workspace Color Palette.
 *
 * Warm paper tones, deep botanical green, restrained terracotta clay accents.
 */
object StudioPalette {
    // Warm paper tones
    val PaperBackgroundLight = Color(0xFFF6F4EF)
    val PaperSurfaceLight = Color(0xFFFFFEFA)
    val PaperSurfaceVariantLight = Color(0xFFEAE9E1)
    val OnPaperDark = Color(0xFF202820)
    val OnPaperVariantDark = Color(0xFF59635C)

    // Deep botanical green
    val GreenPrimary = Color(0xFF245B4A)
    val GreenOnPrimary = Color(0xFFFFFFFF)
    val GreenContainer = Color(0xFFDCEBE2)
    val GreenOnContainer = Color(0xFF173E31)

    // Terracotta / Clay
    val ClaySecondary = Color(0xFF98552F)
    val ClayOnSecondary = Color(0xFFFFFFFF)
    val ClayContainer = Color(0xFFF6DFCF)
    val ClayOnContainer = Color(0xFF40200F)

    // Slate / Muted blue-grey
    val SlateTertiary = Color(0xFF536B8A)
    val SlateOnTertiary = Color(0xFFFFFFFF)

    // Outlines & Dividers
    val Outline = Color(0xFF858D84)
    val OutlineVariant = Color(0xFFD4D7CE)

    // Errors
    val ErrorRed = Color(0xFFB3261E)
    val OnErrorWhite = Color(0xFFFFFFFF)
    val ErrorContainerRed = Color(0xFFF9DEDC)
    val OnErrorContainerRed = Color(0xFF410E0B)

    // Dark Mode Colors
    val DarkBackground = Color(0xFF151A17)
    val DarkSurface = Color(0xFF1D241F)
    val DarkSurfaceVariant = Color(0xFF303832)
    val DarkOnBackground = Color(0xFFE5E9E2)
    val DarkOnSurfaceVariant = Color(0xFFC0C9C0)
    val DarkGreenPrimary = Color(0xFFA5D4BC)
    val DarkGreenOnPrimary = Color(0xFF123D2E)
    val DarkGreenContainer = Color(0xFF245B4A)
    val DarkGreenOnContainer = Color(0xFFDCEBE2)
    val DarkClaySecondary = Color(0xFFF0B995)
    val DarkClayOnSecondary = Color(0xFF542B16)
    val DarkClayContainer = Color(0xFF713F25)
    val DarkClayOnContainer = Color(0xFFFFDBC5)
    val DarkSlateTertiary = Color(0xFFB8CBE8)
    val DarkSlateOnTertiary = Color(0xFF24364F)
    val DarkOutline = Color(0xFF89958B)
    val DarkOutlineVariant = Color(0xFF424B44)
}

val StudioLightColorScheme = lightColorScheme(
    primary = StudioPalette.GreenPrimary,
    onPrimary = StudioPalette.GreenOnPrimary,
    primaryContainer = StudioPalette.GreenContainer,
    onPrimaryContainer = StudioPalette.GreenOnContainer,
    secondary = StudioPalette.ClaySecondary,
    onSecondary = StudioPalette.ClayOnSecondary,
    secondaryContainer = StudioPalette.ClayContainer,
    onSecondaryContainer = StudioPalette.ClayOnContainer,
    tertiary = StudioPalette.SlateTertiary,
    onTertiary = StudioPalette.SlateOnTertiary,
    background = StudioPalette.PaperBackgroundLight,
    onBackground = StudioPalette.OnPaperDark,
    surface = StudioPalette.PaperSurfaceLight,
    onSurface = StudioPalette.OnPaperDark,
    surfaceVariant = StudioPalette.PaperSurfaceVariantLight,
    onSurfaceVariant = StudioPalette.OnPaperVariantDark,
    outline = StudioPalette.Outline,
    outlineVariant = StudioPalette.OutlineVariant,
    error = StudioPalette.ErrorRed,
    onError = StudioPalette.OnErrorWhite,
    errorContainer = StudioPalette.ErrorContainerRed,
    onErrorContainer = StudioPalette.OnErrorContainerRed,
)

val StudioDarkColorScheme = darkColorScheme(
    primary = StudioPalette.DarkGreenPrimary,
    onPrimary = StudioPalette.DarkGreenOnPrimary,
    primaryContainer = StudioPalette.DarkGreenContainer,
    onPrimaryContainer = StudioPalette.DarkGreenOnContainer,
    secondary = StudioPalette.DarkClaySecondary,
    onSecondary = StudioPalette.DarkClayOnSecondary,
    secondaryContainer = StudioPalette.DarkClayContainer,
    onSecondaryContainer = StudioPalette.DarkClayOnContainer,
    tertiary = StudioPalette.DarkSlateTertiary,
    onTertiary = StudioPalette.DarkSlateOnTertiary,
    background = StudioPalette.DarkBackground,
    onBackground = StudioPalette.DarkOnBackground,
    surface = StudioPalette.DarkSurface,
    onSurface = StudioPalette.DarkOnBackground,
    surfaceVariant = StudioPalette.DarkSurfaceVariant,
    onSurfaceVariant = StudioPalette.DarkOnSurfaceVariant,
    outline = StudioPalette.DarkOutline,
    outlineVariant = StudioPalette.DarkOutlineVariant,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
