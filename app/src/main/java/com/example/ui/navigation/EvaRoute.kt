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
    data class Artist(
        val artistName: String,
        val imageUrl: String = ""
    ) : EvaRoute
}
