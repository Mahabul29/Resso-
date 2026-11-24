package com.example.data.repository

import android.content.Context
import com.example.data.firebase.FirestoreMusicRepository
import com.example.data.local.AppDatabase
import com.example.data.local.FollowedArtistEntity
import com.example.data.local.HistorySongEntity
import com.example.data.local.LikedSongEntity
import com.example.data.local.LocalCommentEntity
import com.example.data.local.RecentSearchEntity
import com.example.data.local.TrendingCache
import com.example.data.local.UserPreferencesDataStore
import com.example.data.model.ArtistInfo
import com.example.data.model.Lyrics
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.data.model.SongStats
import com.example.data.remote.JioSaavnApi
import com.example.data.remote.JioSaavnParser
import com.example.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.random.Random
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val trendingCache = TrendingCache(appContext)

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

    suspend fun searchSongs(query: String, limit: Int = 30, page: Int = 1): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val primaryResponse = try {
                api.searchSongs(trimmed, limit, page)
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

            val fallbackResponse = api.searchFallback(trimmed, limit, page)
            if (fallbackResponse.isSuccessful) {
                val fallbackSongs = JioSaavnParser.parseSongs(fallbackResponse.body()?.string().orEmpty())
                return@withContext Result.success(fallbackSongs)
            }
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Today's date as yyyy-MM-dd (device time zone). */
    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * "Trending Now" that changes every day:
     *  - built once per day and cached (so it is stable during the day),
     *  - mixes the live trending endpoint with a search that rotates daily,
     *  - shuffled with a day-based seed so the order is different every day.
     * If the network fails we fall back to the last cached list instead of showing an error.
     */
    /**
     * Keeps only songs in the languages picked by the listener. Songs whose language the API did
     * not report are kept, so a missing field can never empty the queue. No pick = no filtering.
     */
    suspend fun onlySelectedLanguages(songs: List<Song>): List<Song> =
        filterByLanguages(songs, prefs.musicLanguages.first())

    private fun filterByLanguages(songs: List<Song>, languages: Set<String>): List<Song> {
        if (languages.isEmpty()) return songs
        return songs.filter { song ->
            song.language.isBlank() || languages.any { it.equals(song.language, ignoreCase = true) }
        }
    }

    suspend fun fetchDailyTrending(limit: Int = 30, forceRefresh: Boolean = false): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            val today = todayKey()
            val cached = trendingCache.load()
            if (!forceRefresh && cached != null && cached.first == today) {
                return@withContext Result.success(cached.second)
            }

            val epochDay = System.currentTimeMillis() / 86_400_000L
            val languages = prefs.musicLanguages.first()
            val queries = if (languages.isEmpty()) {
                AppConstants.DAILY_TRENDING_QUERIES
            } else {
                AppConstants.languageTrendingQueries(languages)
            }
            val query = queries[(epochDay % queries.size).toInt()]
            val page = (epochDay % 3).toInt() + 1

            val base = fetchTrendingSongs(limit).getOrDefault(emptyList())
            val extra = searchSongs(query, limit, page).getOrDefault(emptyList())
            // With languages picked, also pull "Top <language> songs" for each of them (max 4).
            val perLanguage = languages.sorted().take(4).flatMap { lang ->
                searchSongs("Top $lang songs", limit, page).getOrDefault(emptyList())
            }

            val pool = filterByLanguages(base + extra + perLanguage, languages).distinctBy { it.id }
            if (pool.isEmpty()) {
                return@withContext if (cached != null) {
                    Result.success(cached.second)
                } else {
                    Result.failure(IllegalStateException("No trending songs available right now."))
                }
            }

            val daily = pool.shuffled(Random(epochDay)).take(limit)
            trendingCache.save(today, daily)
            Result.success(daily)
        }

    /**
     * Artist search for "Add artist". Uses the artist endpoint first; if the API has none (or
     * returns nothing) the artists are collected from song search results instead.
     */
    suspend fun searchArtists(query: String, limit: Int = 20): Result<List<ArtistInfo>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            try {
                val direct = try {
                    val response = api.searchArtists(trimmed, limit)
                    if (response.isSuccessful) {
                        JioSaavnParser.parseArtists(response.body()?.string().orEmpty())
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                if (direct.isNotEmpty()) return@withContext Result.success(direct.take(limit))

                val songs = searchSongs(trimmed, 30).getOrElse { return@withContext Result.failure<List<ArtistInfo>>(it) }
                val fromSongs = LinkedHashMap<String, ArtistInfo>()
                songs.forEach { song ->
                    val name = song.firstArtist
                    if (name.isNotBlank() && !name.equals("Music", ignoreCase = true) &&
                        !name.equals("Unknown Artist", ignoreCase = true)
                    ) {
                        fromSongs.putIfAbsent(
                            name.lowercase(),
                            ArtistInfo(name = name, imageUrl = song.primaryArtistImage.ifBlank { song.image })
                        )
                    }
                }
                val all = fromSongs.values.toList()
                val matching = all.filter { it.name.contains(trimmed, ignoreCase = true) }
                Result.success((matching.ifEmpty { all }).take(limit))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private val lyricsCache = HashMap<String, Lyrics>()

    /**
     * Lyrics for a song: the time-synced version first, plain text if that has nothing.
     * Songs without lyrics give an empty [Lyrics] (success); only a network failure is an error.
     */
    suspend fun fetchLyrics(songId: String): Result<Lyrics> = withContext(Dispatchers.IO) {
        synchronized(lyricsCache) { lyricsCache[songId] }?.let { return@withContext Result.success(it) }

        var lastError: Exception? = null
        var answered = false
        suspend fun load(call: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): Lyrics? {
            return try {
                val response = call()
                answered = true
                if (response.isSuccessful) {
                    JioSaavnParser.parseLyrics(response.body()?.string().orEmpty()).takeIf { !it.isEmpty }
                } else {
                    null
                }
            } catch (e: Exception) {
                lastError = e
                null
            }
        }

        val lyrics = load { api.getSyncedLyrics(songId) } ?: load { api.getLyrics(songId) }
        when {
            lyrics != null -> {
                synchronized(lyricsCache) { lyricsCache[songId] = lyrics }
                Result.success(lyrics)
            }
            !answered && lastError != null -> Result.failure(lastError!!)
            else -> Result.success(Lyrics(emptyList()))
        }
    }

    private suspend fun fetchSuggestions(songId: String, limit: Int): List<Song> {
        return try {
            val response = api.getSongSuggestions(songId, limit)
            if (response.isSuccessful) {
                JioSaavnParser.parseSongs(response.body()?.string().orEmpty())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Songs related to [song]. Order of preference:
     *  1. the API's own suggestions for this song,
     *  2. more songs by the same artist(s),
     *  3. the same album.
     * Anything in [excludeIds] (already in the queue) is skipped so the queue never repeats.
     */
    suspend fun fetchSimilarSongs(
        song: Song,
        excludeIds: Set<String> = emptySet(),
        page: Int = 1
    ): List<Song> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Song>()
        fun collect(list: List<Song>) {
            list.forEach { s ->
                if (s.id != song.id && s.id !in excludeIds) found.putIfAbsent(s.id, s)
            }
        }

        collect(fetchSuggestions(song.id, 25))

        if (found.size < 15) {
            val artists = song.artist.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .take(2)
            for (artist in artists) {
                collect(searchSongs(artist, 25, page).getOrDefault(emptyList()))
                if (found.size >= 25) break
            }
        }

        if (found.size < 10 && song.album.isNotBlank() && !song.album.equals("Single", ignoreCase = true)) {
            collect(searchSongs(song.album, 20, page).getOrDefault(emptyList()))
        }

        filterByLanguages(found.values.toList(), prefs.musicLanguages.first())
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

    /** Delete account: removes everything this app stored on the device. */
    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        prefs.clearAll()
        trendingCache.clear()
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
