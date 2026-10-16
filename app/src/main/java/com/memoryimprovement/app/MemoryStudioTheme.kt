package com.memoryimprovement.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.memoryimprovement.app.theme.StudioDarkColorScheme
import com.memoryimprovement.app.theme.StudioLightColorScheme
import com.memoryimprovement.app.theme.StudioTypography

@Composable
fun MemoryImprovementTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StudioDarkColorScheme else StudioLightColorScheme,
        typography = StudioTypography,
        content = content,
    )
}
