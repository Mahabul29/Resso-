package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface EvaRoute {
    @Serializable
    data object MusicTab : EvaRoute

    @Serializable
    data object ExploreTab : EvaRoute

    @Serializable
    data object MeTab : EvaRoute

    @Serializable
    data object Settings : EvaRoute

    @Serializable
    data object MusicLanguage : EvaRoute

    @Serializable
    data object AddArtist : EvaRoute

    @Serializable
    data object Equalizer : EvaRoute

    @Serializable
    data class UserProfile(
        val uid: String
    ) : EvaRoute

    @Serializable
    data class Artist(
        val artistName: String,
        val imageUrl: String = ""
    ) : EvaRoute
}
