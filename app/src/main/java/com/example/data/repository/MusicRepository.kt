package com.example.data.repository

import android.content.Context
import com.example.data.firebase.FirestoreMusicRepository
import com.example.data.local.AppDatabase
import com.example.data.local.FollowedArtistEntity
import com.example.data.local.HistorySongEntity
import com.example.data.local.LikedSongEntity
import com.example.data.local.LocalCommentEntity
import com.example.data.local.RecentSearchEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.ArtistInfo
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.data.model.SongStats
import com.example.data.remote.JioSaavnApi
import com.example.data.remote.JioSaavnParser
import com.example.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.UUID
import java.util.concurrent.TimeUnit

class MusicRepository(
    context: Context,
    private val firestoreRepo: FirestoreMusicRepository = FirestoreMusicRepository(context)
) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.evaDao()
    val prefs = UserPreferencesDataStore(appContext)

    private val api: JioSaavnApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(AppConstants.JIOSAAVN_BASE_URL)
            .client(client)
            .build()
            .create(JioSaavnApi::class.java)
    }

    // --- Remote JioSaavn API ---

    suspend fun fetchTrendingSongs(limit: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val primaryResponse = try {
                api.getTrendingSongs(limit)
            } catch (_: Exception) {
                null
            }
            val primaryBody = if (primaryResponse != null && primaryResponse.isSuccessful) {
                primaryResponse.body()?.string().orEmpty()
            } else {
                ""
            }
            val primarySongs = if (primaryBody.isNotBlank()) {
                JioSaavnParser.parseSongs(primaryBody)
            } else {
                emptyList()
            }
            if (primarySongs.isNotEmpty()) {
                return@withContext Result.success(primarySongs)
            }

            val fallbackResponse = api.getTrendingFallback(limit)
            if (fallbackResponse.isSuccessful) {
                val fallbackSongs = JioSaavnParser.parseSongs(fallbackResponse.body()?.string().orEmpty())
                if (fallbackSongs.isNotEmpty()) {
                    return@withContext Result.success(fallbackSongs)
                }
            }

            // Fallback to a broad popular search if trending endpoint returns empty
            val backupSearch = searchSongs("Top Bollywood hits", limit).getOrDefault(emptyList())
            if (backupSearch.isNotEmpty()) {
                Result.success(backupSearch)
            } else {
                Result.failure(IllegalStateException("No trending songs available right now."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val primaryResponse = try {
                api.searchSongs(trimmed, limit)
            } catch (_: Exception) {
                null
            }
            val primaryBody = if (primaryResponse != null && primaryResponse.isSuccessful) {
                primaryResponse.body()?.string().orEmpty()
            } else {
                ""
            }
            val primarySongs = if (primaryBody.isNotBlank()) {
                JioSaavnParser.parseSongs(primaryBody)
            } else {
                emptyList()
            }
            if (primarySongs.isNotEmpty()) {
                return@withContext Result.success(primarySongs)
            }

            val fallbackResponse = api.searchFallback(trimmed, limit)
            if (fallbackResponse.isSuccessful) {
                val fallbackSongs = JioSaavnParser.parseSongs(fallbackResponse.body()?.string().orEmpty())
                return@withContext Result.success(fallbackSongs)
            }
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSimilarSongs(song: Song): List<Song> {
        val artistQuery = song.firstArtist
        val results = searchSongs(artistQuery, 20).getOrDefault(emptyList())
        return results.filter { it.id != song.id }
    }

    // --- Local Persistence (Room + DataStore) ---

    val likedSongs: Flow<List<Song>> = dao.observeLikedSongs().map { list -> list.map { it.toSong() } }
    val likedSongIds: Flow<Set<String>> = dao.observeLikedSongIds().map { it.toSet() }
    val historySongs: Flow<List<Song>> = dao.observeHistory().map { list -> list.map { it.toSong() } }
    val historyCount: Flow<Int> = dao.observeHistoryCount()
    val followedArtists: Flow<List<FollowedArtistEntity>> = dao.observeFollowedArtists()
    val recentSearches: Flow<List<String>> = dao.observeRecentSearches().map { list -> list.map { it.query } }

    val meArtists: Flow<List<ArtistInfo>> = combine(
        dao.observeFollowedArtists(),
        dao.observeLikedSongs()
    ) { followed, liked ->
        val map = LinkedHashMap<String, ArtistInfo>()
        followed.forEach { f ->
            map[f.artistName.lowercase()] = ArtistInfo(
                name = f.artistName,
                imageUrl = f.imageUrl,
                isFollowed = true
            )
        }
        liked.forEach { s ->
            val firstName = s.artist.split(",").firstOrNull()?.trim().orEmpty()
            if (firstName.isNotBlank() && !map.containsKey(firstName.lowercase())) {
                map[firstName.lowercase()] = ArtistInfo(
                    name = firstName,
                    imageUrl = s.primaryArtistImage.ifBlank { s.image },
                    isFollowed = false
                )
            }
        }
        map.values.toList()
    }

    suspend fun toggleLike(
        song: Song,
        currentlyLiked: Boolean,
        currentRemoteLikeCount: Long
    ): Boolean {
        val nowLiked = !currentlyLiked
        if (nowLiked) {
            dao.insertLikedSong(LikedSongEntity.fromSong(song))
        } else {
            dao.deleteLikedSong(song.id)
        }
        if (firestoreRepo.isAuthenticated()) {
            firestoreRepo.syncSongLike(song, nowLiked, currentRemoteLikeCount)
        }
        return nowLiked
    }

    suspend fun toggleFollowArtist(artistName: String, imageUrl: String, currentlyFollowed: Boolean): Boolean {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return currentlyFollowed
        val nowFollowed = !currentlyFollowed
        if (nowFollowed) {
            dao.insertFollowedArtist(FollowedArtistEntity(artistName = cleanName, imageUrl = imageUrl))
        } else {
            dao.deleteFollowedArtist(cleanName)
        }
        return nowFollowed
    }

    suspend fun recordHistory(song: Song) {
        dao.insertHistorySong(HistorySongEntity.fromSong(song))
    }

    suspend fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            dao.insertRecentSearch(RecentSearchEntity(query = trimmed))
        }
    }

    suspend fun clearRecentSearches() {
        dao.clearRecentSearches()
    }

    // --- Comments & Stats (Firestore when signed in, Room fallback otherwise) ---

    fun isFirebaseAuthReady(): Boolean = firestoreRepo.isAuthenticated()
    fun currentFirebaseUid(): String? = firestoreRepo.currentUserId()

    fun observeSongStats(song: Song): Flow<SongStats?> = firestoreRepo.observeSongStats(song)

    fun observeComments(song: Song): Flow<List<SongComment>> {
        return if (firestoreRepo.isAuthenticated()) {
            firestoreRepo.observeComments(song)
        } else {
            dao.observeLocalComments(song.id).map { list ->
                list.map {
                    SongComment(
                        id = it.id,
                        text = it.text,
                        name = it.name,
                        uid = it.uid,
                        createdAt = null,
                        localCreatedAtMillis = it.createdAtMillis
                    )
                }
            }
        }
    }

    suspend fun addComment(
        song: Song,
        text: String,
        displayName: String
    ): Result<Boolean> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 300) {
            return Result.failure(IllegalArgumentException("Comment must be 1-300 characters."))
        }
        return if (firestoreRepo.isAuthenticated()) {
            val res = firestoreRepo.addComment(song, trimmed, displayName)
            if (res.isSuccess) {
                Result.success(true)
            } else {
                // Save locally as fallback if Firestore write failed (e.g. offline/network error)
                saveLocalComment(song.id, trimmed, displayName)
                Result.success(false)
            }
        } else {
            saveLocalComment(song.id, trimmed, displayName)
            Result.success(false)
        }
    }

    private suspend fun saveLocalComment(songId: String, text: String, displayName: String) {
        dao.insertLocalComment(
            LocalCommentEntity(
                id = UUID.randomUUID().toString(),
                songId = songId,
                text = text,
                name = displayName.trim().ifBlank { "Listener" },
                uid = "local_user"
            )
        )
    }

    suspend fun deleteComment(
        song: Song,
        comment: SongComment,
        currentRemoteCommentCount: Long
    ): Result<Unit> {
        return if (firestoreRepo.isAuthenticated() && comment.uid == firestoreRepo.currentUserId()) {
            firestoreRepo.deleteComment(song, comment.id, currentRemoteCommentCount)
        } else if (comment.uid == "local_user") {
            dao.deleteLocalComment(comment.id)
            Result.success(Unit)
        } else {
            Result.failure(SecurityException("You can only delete your own comments."))
        }
    }
}
