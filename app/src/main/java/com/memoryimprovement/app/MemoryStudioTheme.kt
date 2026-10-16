package com.memoryimprovement.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import com.memoryimprovement.app.theme.Spacing
import com.memoryimprovement.app.theme.StudioDarkColorScheme
import com.memoryimprovement.app.theme.StudioLightColorScheme
import com.memoryimprovement.app.theme.StudioTypography

/**
 * Memory Studio visual system.
 *
 * A quiet, editorial study workspace: warm paper surfaces, deep botanical green,
 * and restrained clay accents. Semantic colors stay centralized so light/dark
 * surfaces and component states remain coherent.
 */
private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(Spacing.small),
    small = RoundedCornerShape(Spacing.medium),
    medium = RoundedCornerShape(Spacing.normal),
    large = RoundedCornerShape(Spacing.extraLarge),
    extraLarge = RoundedCornerShape(Spacing.huge),
)

@Composable
fun MemoryImprovementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
