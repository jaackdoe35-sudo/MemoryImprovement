package com.memoryimprovement.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central design tokens for Memory Studio. Remote font failures fall back to
 * Compose's default system typeface; font requests are asynchronous.
 */
private val StudioLightColors = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E8E1),
    onPrimaryContainer = Color(0xFF0F2A22),
    secondary = Color(0xFF8A5A3C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFEBE3),
    onSurfaceVariant = Color(0xFF5C5749),
    surfaceContainer = Color(0xFFF2EEE6),
    outline = Color(0xFFD6CFC2),
    outlineVariant = Color(0xFFE6E0D4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val StudioDarkColors = darkColorScheme(
    primary = Color(0xFF9CCBB8),
    onPrimary = Color(0xFF0E3328),
    primaryContainer = Color(0xFF1F4A3D),
    onPrimaryContainer = Color(0xFFD7E8E1),
    secondary = Color(0xFFD9A783),
    onSecondary = Color(0xFF3D2414),
    background = Color(0xFF121413),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1D1B),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF262A27),
    onSurfaceVariant = Color(0xFFB9B4A7),
    surfaceContainer = Color(0xFF1F2321),
    outline = Color(0xFF4A4F4B),
    outlineVariant = Color(0xFF343A36),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val GoogleFontsProvider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Newsreader = GoogleFont("Newsreader")
private val Inter = GoogleFont("Inter")

private val EditorialHeading = FontFamily(
    Font(googleFont = Newsreader, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = Newsreader, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
)

private val InterfaceBody = FontFamily(
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
)

private val StudioTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = EditorialHeading,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = EditorialHeading,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterfaceBody,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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

/** Shared 4 dp spacing scale. */
object StudioSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screenHorizontalPhone = 20.dp
    val screenHorizontalTablet = 32.dp
    val screenVertical = 16.dp
    val controlMinHeight = 48.dp
    val actionHeight = 48.dp
}
