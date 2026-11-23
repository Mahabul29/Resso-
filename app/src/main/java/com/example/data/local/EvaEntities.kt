package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Song

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val image: String,
    val url: String,
    val primaryArtistImage: String = "",
    val likedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        image = image,
        url = url,
        primaryArtistImage = primaryArtistImage
    )

    companion object {
        fun fromSong(song: Song): LikedSongEntity = LikedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            image = song.image,
            url = song.url,
            primaryArtistImage = song.primaryArtistImage
        )
    }
}

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val artistName: String,
    val imageUrl: String,
    val followedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class HistorySongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val image: String,
    val url: String,
    val primaryArtistImage: String = "",
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        image = image,
        url = url,
        primaryArtistImage = primaryArtistImage
    )

    companion object {
        fun fromSong(song: Song): HistorySongEntity = HistorySongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            image = song.image,
            url = song.url,
            primaryArtistImage = song.primaryArtistImage
        )
    }
}

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_comments")
data class LocalCommentEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val text: String,
    val name: String,
    val uid: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
