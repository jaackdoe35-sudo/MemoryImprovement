package com.memoryimprovement.app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Warm paper, botanical green, and clay accents. Keep palette values here. */
internal val StudioLightColors = lightColorScheme(
    primary = Color(0xFF2F5D50), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E8E1), onPrimaryContainer = Color(0xFF0F2A22),
    secondary = Color(0xFF8A5A3C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0DED1), onSecondaryContainer = Color(0xFF3D2414),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFEBE3), onSurfaceVariant = Color(0xFF5C5749),
    surfaceContainer = Color(0xFFF2EEE6), outline = Color(0xFFD6CFC2),
    outlineVariant = Color(0xFFE6E0D4), error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

internal val StudioDarkColors = darkColorScheme(
    primary = Color(0xFF9CCBB8), onPrimary = Color(0xFF0E3328),
    primaryContainer = Color(0xFF1F4A3D), onPrimaryContainer = Color(0xFFD7E8E1),
    secondary = Color(0xFFD9A783), onSecondary = Color(0xFF3D2414),
    secondaryContainer = Color(0xFF5A3B2A), onSecondaryContainer = Color(0xFFF0DED1),
    background = Color(0xFF121413), onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1D1B), onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF262A27), onSurfaceVariant = Color(0xFFB9B4A7),
    surfaceContainer = Color(0xFF1F2321), outline = Color(0xFF4A4F4B),
    outlineVariant = Color(0xFF343A36), error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
