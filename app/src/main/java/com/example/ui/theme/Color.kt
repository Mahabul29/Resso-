package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val EvaBackground = Color(0xFF0E0C13)
val EvaSurface = Color(0xFF17141F)
val EvaSurfaceElevated = Color(0xFF211C2D)
val EvaSurfaceGlass = Color(0xCC17141F)
val EvaOrange = Color(0xFFFF7A45)
val EvaPinkRed = Color(0xFFFF2D5F)
val EvaTextPrimary = Color(0xFFFFFFFF)
val EvaTextSecondary = Color(0xFFA7A1B8)
val EvaTextMuted = Color(0xFF6F6980)
val EvaOutline = Color(0xFF2C263B)
val EvaSuccess = Color(0xFF34D399)

val EvaAccentGradient = Brush.horizontalGradient(
    colors = listOf(EvaOrange, EvaPinkRed)
)

val EvaAccentDiagonalGradient = Brush.linearGradient(
    colors = listOf(EvaOrange, EvaPinkRed)
)
