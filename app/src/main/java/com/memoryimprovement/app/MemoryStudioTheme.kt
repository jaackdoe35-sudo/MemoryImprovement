package com.memoryimprovement.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Memory Studio visual system.
 *
 * A quiet, editorial study workspace: warm paper surfaces, deep botanical green,
 * and restrained clay accents. Semantic colors stay centralized so light/dark
 * surfaces and component states remain coherent.
 */
private val StudioLightColors = lightColorScheme(
    primary = Color(0xFF245B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBE2),
    onPrimaryContainer = Color(0xFF173E31),
    secondary = Color(0xFF98552F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6DFCF),
    onSecondaryContainer = Color(0xFF40200F),
    tertiary = Color(0xFF536B8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF202820),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF202820),
    surfaceVariant = Color(0xFFEAE9E1),
    onSurfaceVariant = Color(0xFF59635C),
    outline = Color(0xFF858D84),
    outlineVariant = Color(0xFFD4D7CE),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val StudioDarkColors = darkColorScheme(
    primary = Color(0xFFA5D4BC),
    onPrimary = Color(0xFF123D2E),
    primaryContainer = Color(0xFF245B4A),
    onPrimaryContainer = Color(0xFFDCEBE2),
    secondary = Color(0xFFF0B995),
    onSecondary = Color(0xFF542B16),
    secondaryContainer = Color(0xFF713F25),
    onSecondaryContainer = Color(0xFFFFDBC5),
    tertiary = Color(0xFFB8CBE8),
    onTertiary = Color(0xFF24364F),
    background = Color(0xFF151A17),
    onBackground = Color(0xFFE5E9E2),
    surface = Color(0xFF1D241F),
    onSurface = Color(0xFFE5E9E2),
    surfaceVariant = Color(0xFF303832),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF89958B),
    outlineVariant = Color(0xFF424B44),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val StudioTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.25.sp),
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun MemoryImprovementTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) StudioDarkColors else StudioLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}

/** Shared spacing rhythm for every Compose screen and component. */
object StudioSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screenHorizontal = 20.dp
    val screenVertical = 16.dp
    val sectionGap = 18.dp
    val cardPadding = 18.dp
    val controlMinHeight = 48.dp
}
