package com.memoryimprovement.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import androidx.compose.ui.unit.sp

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

internal val StudioTypography = Typography(
    displaySmall = TextStyle(fontFamily = EditorialHeading, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = EditorialHeading, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = InterfaceBody, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)
