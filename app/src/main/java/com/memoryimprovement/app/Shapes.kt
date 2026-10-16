package com.memoryimprovement.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(StudioSpacing.sm),
    small = RoundedCornerShape(StudioSpacing.sm),
    medium = RoundedCornerShape(StudioSpacing.dp14),
    large = RoundedCornerShape(StudioSpacing.dp20),
    extraLarge = RoundedCornerShape(StudioSpacing.dp28),
)
