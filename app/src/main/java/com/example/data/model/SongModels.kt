package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val image: String,
    val url: String,
    val primaryArtistImage: String = "",
    /** Language reported by the music API ("hindi", "english", "bengali"...). Blank if unknown. */
    val language: String = ""
) {
    val firstArtist: String
        get() = artist.split(",")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.ifBlank { "Music" }
            ?: "Music"

    val firestoreId: String
        get() = id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "unknown_song" }
}

@Keep
@IgnoreExtraProperties
data class SongStats(
    @DocumentId val id: String = "",
    val title: String = "",
    val artist: String = "",
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SongComment(
    @DocumentId val id: String = "",
    val text: String = "",
    val name: String = "",
    val uid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val localCreatedAtMillis: Long = 0L
)

data class ArtistInfo(
    val name: String,
    val imageUrl: String,
    val isFollowed: Boolean = false
)
