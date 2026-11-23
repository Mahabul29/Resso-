package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvaDao {
    // Liked Songs
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun observeLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT id FROM liked_songs")
    fun observeLikedSongIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :songId")
    suspend fun deleteLikedSong(songId: String)

    // Followed Artists
    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun observeFollowedArtists(): Flow<List<FollowedArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(artist: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE artistName = :artistName")
    suspend fun deleteFollowedArtist(artistName: String)

    // Play History
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<HistorySongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorySong(song: HistorySongEntity)

    @Query("SELECT COUNT(*) FROM play_history")
    fun observeHistoryCount(): Flow<Int>

    // Recent Searches
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    // Local Comments Fallback
    @Query("SELECT * FROM local_comments WHERE songId = :songId ORDER BY createdAtMillis DESC LIMIT 100")
    fun observeLocalComments(songId: String): Flow<List<LocalCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalComment(comment: LocalCommentEntity)

    @Query("DELETE FROM local_comments WHERE id = :commentId")
    suspend fun deleteLocalComment(commentId: String)
}
