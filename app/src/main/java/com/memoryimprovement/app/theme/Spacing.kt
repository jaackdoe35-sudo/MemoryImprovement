package com.memoryimprovement.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design Tokens for Layout & Spacing.
 *
 * Enforces consistent metrics across all screens and components.
 */
object Spacing {
    val none: Dp = 0.dp
    val micro: Dp = 2.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val normal: Dp = 16.dp
    val large: Dp = 20.dp
    val extraLarge: Dp = 24.dp
    val huge: Dp = 32.dp

    // Design System Specific Constants
    val outlineWidth: Dp = 1.dp
    val cardCornerRadius: Dp = 16.dp
    val buttonCornerRadius: Dp = 12.dp
    val chipCornerRadius: Dp = 8.dp
    val iconSizeSmall: Dp = 18.dp
    val iconSizeMedium: Dp = 24.dp
    val iconSizeLarge: Dp = 32.dp
    val progressRingSize: Dp = 48.dp
    val progressRingStrokeWidth: Dp = 4.dp
}
