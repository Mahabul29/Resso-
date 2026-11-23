package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val EvaDarkColorScheme = darkColorScheme(
    primary = EvaPinkRed,
    onPrimary = EvaTextPrimary,
    primaryContainer = EvaSurfaceElevated,
    onPrimaryContainer = EvaTextPrimary,
    secondary = EvaOrange,
    onSecondary = EvaTextPrimary,
    secondaryContainer = EvaSurfaceElevated,
    onSecondaryContainer = EvaTextPrimary,
    tertiary = EvaOrange,
    background = EvaBackground,
    onBackground = EvaTextPrimary,
    surface = EvaSurface,
    onSurface = EvaTextPrimary,
    surfaceVariant = EvaSurfaceElevated,
    onSurfaceVariant = EvaTextSecondary,
    outline = EvaOutline,
    error = EvaPinkRed,
    onError = EvaTextPrimary
)

val EvaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EvaMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvaDarkColorScheme,
        typography = EvaTypography,
        shapes = EvaShapes,
        content = content
    )
}
