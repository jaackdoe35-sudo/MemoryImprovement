package com.memoryimprovement.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MemoryImprovementTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StudioDarkColors else StudioLightColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
