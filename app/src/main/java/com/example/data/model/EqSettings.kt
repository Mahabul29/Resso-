package com.example.data.model

/**
 * Saved equalizer state. [levels] are band gains in millibels (1 dB = 100 mB).
 * An empty list means "flat" (all bands at 0).
 */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = "Normal",
    val levels: List<Int> = emptyList()
)
