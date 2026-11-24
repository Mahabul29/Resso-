package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EvaMusicApplication
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.firebaseAuthOrNull
import com.example.data.model.ArtistInfo
import com.example.data.model.EqSettings
import com.example.data.model.Lyrics
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.data.model.SongStats
import com.example.data.model.UserProfile
import com.example.data.model.ViewedProfile
import com.example.data.repository.MusicRepository
import com.example.player.EqualizerManager
import com.example.player.PlayerManager
import com.example.util.AppConstants
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EvaMusicViewModel(
    private val repository: MusicRepository,
    val playerManager: PlayerManager
) : ViewModel() {

    // Null when Firebase is not configured (no google-services.json): the app then runs in
    // local mode instead of crashing.
    private val firebaseAuth = firebaseAuthOrNull()

    val authUser: StateFlow<FirebaseUser?> =
        (firebaseAuth?.authStateFlow() ?: flowOf<FirebaseUser?>(null))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = firebaseAuth?.currentUser
            )

    private val _feedState = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<Song>>> = _feedState.asStateFlow()

    private val _trendingState = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val trendingState: StateFlow<UiState<List<Song>>> = _trendingState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchResultsState = MutableStateFlow<UiState<List<Song>>>(UiState.Success(emptyList()))
    val searchResultsState: StateFlow<UiState<List<Song>>> = _searchResultsState.asStateFlow()

    private val _artistSongsState = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val artistSongsState: StateFlow<UiState<List<Song>>> = _artistSongsState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _activeCommentsSong = MutableStateFlow<Song?>(null)
    val activeCommentsSong: StateFlow<Song?> = _activeCommentsSong.asStateFlow()

    val displayName: StateFlow<String> = repository.prefs.displayName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = "Listener"
        )

    val username: StateFlow<String> = repository.prefs.username
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    // People + artists shown above the song results while searching in Explore.
    private val _exploreArtists = MutableStateFlow<List<ArtistInfo>>(emptyList())
    val exploreArtists: StateFlow<List<ArtistInfo>> = _exploreArtists.asStateFlow()

    private val _exploreUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val exploreUsers: StateFlow<List<UserProfile>> = _exploreUsers.asStateFlow()

    private var peopleSearchJob: Job? = null

    private val _viewedProfileState = MutableStateFlow<UiState<ViewedProfile>>(UiState.Loading)
    val viewedProfileState: StateFlow<UiState<ViewedProfile>> = _viewedProfileState.asStateFlow()

    val musicLanguages: StateFlow<Set<String>> = repository.prefs.musicLanguages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _artistSearchState = MutableStateFlow<UiState<List<ArtistInfo>>>(UiState.Success(emptyList()))
    val artistSearchState: StateFlow<UiState<List<ArtistInfo>>> = _artistSearchState.asStateFlow()

    private val _activeLyricsSong = MutableStateFlow<Song?>(null)
    val activeLyricsSong: StateFlow<Song?> = _activeLyricsSong.asStateFlow()

    private val _lyricsState = MutableStateFlow<UiState<Lyrics>>(UiState.Loading)
    val lyricsState: StateFlow<UiState<Lyrics>> = _lyricsState.asStateFlow()

    private var lyricsJob: Job? = null

    val sleepTimerEndMs: StateFlow<Long?> = playerManager.sleepTimerEndMs

    val hasSwipedMusicFeed: StateFlow<Boolean> = repository.prefs.hasSwipedMusicFeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val likedSongs: StateFlow<List<Song>> = repository.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val likedSongIds: StateFlow<Set<String>> = repository.likedSongIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val historySongs: StateFlow<List<Song>> = repository.historySongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val historyCount: StateFlow<Int> = repository.historyCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val meArtists: StateFlow<List<ArtistInfo>> = repository.meArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val followedArtistNames: StateFlow<Set<String>> = repository.followedArtists
        .map { list -> list.map { it.artistName.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val recentSearches: StateFlow<List<String>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val currentSongStats: StateFlow<SongStats?> = combine(
        playerManager.currentSong,
        authUser
    ) { song, user -> song to user?.uid }
        .flatMapLatest { (song, uid) ->
            if (song != null && !uid.isNullOrBlank()) {
                repository.observeSongStats(song)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val commentsState: StateFlow<UiState<List<SongComment>>> = combine(
        _activeCommentsSong,
        authUser
    ) { song, user -> song to user?.uid }
        .flatMapLatest { (song, _) ->
            if (song == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeComments(song)
                    .map<List<SongComment>, UiState<List<SongComment>>> { UiState.Success(it) }
                    .catch { error ->
                        if (error is CancellationException && error.cause == null) throw error
                        emit(UiState.Error(error.localizedMessage ?: "Failed to load comments"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private var loadMoreJob: Job? = null
    private var rotatingQueryIndex = 0
    /** False while the user plays a fixed list (liked songs, playlist...): its order is kept. */
    private var suggestRelatedAfterListen = true
    private var relatedHandledFor: String? = null
    private var trendingDay: String? = null

    init {
        viewModelScope.launch {
            repository.prefs.ensureInitialDisplayName()
        }

        // Whenever someone signs in, merge their cloud-saved liked songs with this phone.
        viewModelScope.launch {
            authUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (!uid.isNullOrBlank()) {
                        try {
                            repository.syncProfileWithCloud()
                            repository.syncLikedSongsWithCloud()
                            repository.syncFollowedArtistsWithCloud()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
        }

        playerManager.onQueueReplaced = {
            // A new queue started: whatever "more songs" request was running is now outdated.
            loadMoreJob?.cancel()
        }
        observeListenedSongs()
        playerManager.onNeedMoreSongs = {
            loadMoreSongsForQueue()
        }
        playerManager.onSongPlayed = { song ->
            viewModelScope.launch {
                repository.recordHistory(song)
            }
        }
        playerManager.onSongFailedSkipped = { song ->
            _snackbarEvents.tryEmit("Skipped unplayable track: ${song.title}")
        }

        // 1) If something was playing last time, continue from that same song.
        // 2) Only if there is nothing to restore, start the trending feed.
        viewModelScope.launch {
            playerManager.restoreSavedPlayback()
            loadInitialFeedAndTrending()
        }
        observeDebouncedSearch()
    }

    /** Call when the app comes to the foreground: rebuilds "Trending Now" if the day changed. */
    fun refreshTrendingIfNewDay() {
        val today = repository.todayKey()
        if (trendingDay != null && trendingDay != today) {
            viewModelScope.launch {
                repository.fetchDailyTrending(30).onSuccess { songs ->
                    trendingDay = today
                    _trendingState.value = UiState.Success(songs)
                }
            }
        }
    }

    fun loadInitialFeedAndTrending(forceRefresh: Boolean = false, replaceQueue: Boolean = false) {
        viewModelScope.launch {
            _feedState.value = UiState.Loading
            _trendingState.value = UiState.Loading
            val result = repository.fetchDailyTrending(30, forceRefresh)
            result.onSuccess { songs ->
                trendingDay = repository.todayKey()
                _trendingState.value = UiState.Success(songs)
                _feedState.value = UiState.Success(songs)
                if ((replaceQueue || playerManager.queue.value.isEmpty()) && songs.isNotEmpty()) {
                    suggestRelatedAfterListen = true
                    playerManager.setQueueAndPlay(
                        songs = songs,
                        startIndex = 0,
                        contextTitle = "Your Daily Mix",
                        fromList = false
                    )
                }
            }.onFailure { error ->
                val message = error.localizedMessage ?: "Couldn't load trending music."
                _trendingState.value = UiState.Error(message)
                _feedState.value = UiState.Error(message)
            }
        }
    }

    private fun observeDebouncedSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(400L)
                .map { it.trim() }
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isEmpty()) {
                        _searchResultsState.value = UiState.Success(emptyList())
                        clearPeopleResults()
                    } else {
                        executeSearch(query, saveHistory = true, includePeople = true)
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _selectedCategory.value = null
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
        _searchQuery.value = category
        clearPeopleResults()
        viewModelScope.launch {
            executeSearch(category, saveHistory = true, includePeople = false)
        }
    }

    fun onRecentSearchSelected(query: String) {
        _selectedCategory.value = null
        _searchQuery.value = query
        viewModelScope.launch {
            executeSearch(query, saveHistory = true, includePeople = true)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _searchResultsState.value = UiState.Success(emptyList())
        clearPeopleResults()
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    private fun clearPeopleResults() {
        peopleSearchJob?.cancel()
        _exploreArtists.value = emptyList()
        _exploreUsers.value = emptyList()
    }

    /** Looks up matching artists and people (by @username) next to the song search. */
    private fun searchPeopleAndArtists(query: String) {
        peopleSearchJob?.cancel()
        _exploreArtists.value = emptyList()
        _exploreUsers.value = emptyList()
        peopleSearchJob = viewModelScope.launch {
            val artistsDeferred = async {
                repository.searchArtists(query, 10).getOrNull().orEmpty()
            }
            val usersDeferred = async {
                repository.searchUsers(query).getOrNull().orEmpty()
            }
            // Show whichever is ready first, so a slow one doesn't block the other.
            _exploreUsers.value = usersDeferred.await()
            _exploreArtists.value = artistsDeferred.await()
        }
    }

    fun loadUserProfile(uid: String) {
        viewModelScope.launch {
            _viewedProfileState.value = UiState.Loading
            repository.fetchUserProfile(uid)
                .onSuccess { _viewedProfileState.value = UiState.Success(it) }
                .onFailure { _viewedProfileState.value = UiState.Error(it.localizedMessage ?: "Couldn't load profile") }
        }
    }

    private suspend fun executeSearch(query: String, saveHistory: Boolean, includePeople: Boolean = false) {
        _searchResultsState.value = UiState.Loading
        if (includePeople) searchPeopleAndArtists(query)
        if (saveHistory) {
            repository.saveRecentSearch(query)
        }
        val result = repository.searchSongs(query, 30)
        result.onSuccess { songs ->
            _searchResultsState.value = UiState.Success(songs)
        }.onFailure { error ->
            _searchResultsState.value = UiState.Error(error.localizedMessage ?: "Search failed")
        }
    }

    fun loadArtistSongs(artistName: String) {
        viewModelScope.launch {
            _artistSongsState.value = UiState.Loading
            val result = repository.searchSongs(artistName, 30)
            result.onSuccess { songs ->
                _artistSongsState.value = UiState.Success(songs)
            }.onFailure { error ->
                _artistSongsState.value = UiState.Error(error.localizedMessage ?: "Failed to load artist songs")
            }
        }
    }

    /**
     * Keeps "Up Next" endless and random: whenever the queue runs low, a batch of random songs
     * (from the picked languages, or from everything if none is picked) is appended. It keeps
     * fetching until a real batch (15+) is found, so songs never arrive one at a time.
     */
    private fun loadMoreSongsForQueue() {
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            val existing = playerManager.queue.value.mapTo(HashSet()) { it.id }
            val languages = repository.prefs.musicLanguages.first()

            val collected = LinkedHashMap<String, Song>()
            var attempts = 0
            while (collected.size < MORE_SONGS_BATCH && attempts < 6) {
                repository.fetchRandomSongs(languages, existing + collected.keys, 20)
                    .forEach { collected.putIfAbsent(it.id, it) }
                attempts++
            }

            if (collected.isNotEmpty()) {
                playerManager.appendSongs(collected.values.shuffled())
            }
        }
    }

    // --- "Heard it fully -> suggest related songs" ---


    /**
     * When a song has been listened to (80% played), related songs (same artist / similar, in the
     * picked languages) are put right after it, so the next song is one the listener is likely to
     * enjoy. Skipping a song early does not trigger this.
     */
    private fun observeListenedSongs() {
        viewModelScope.launch {
            combine(
                playerManager.currentSong,
                playerManager.positionMs,
                playerManager.durationMs
            ) { song, pos, dur -> Triple(song, pos, dur) }
                .collect { (song, pos, dur) ->
                    if (song == null || dur <= 0L || !suggestRelatedAfterListen) return@collect
                    if (relatedHandledFor == song.id) return@collect
                    if (pos * 100L >= dur * LISTENED_PERCENT) {
                        relatedHandledFor = song.id
                        suggestRelatedTo(song)
                    }
                }
        }
    }

    private fun suggestRelatedTo(song: Song) {
        viewModelScope.launch {
            val existing = playerManager.queue.value.mapTo(HashSet()) { it.id }
            val related = repository.fetchSimilarSongs(song, existing, 1).take(RELATED_COUNT)
            // Only if the listener is still on this song; otherwise the moment has passed.
            if (related.isNotEmpty() && playerManager.currentSong.value?.id == song.id) {
                playerManager.insertAfterCurrent(related)
            }
        }
    }

    /** Tap on a single song anywhere: play it, then keep playing songs related to it. */
    fun playSongWithRelated(song: Song) {
        suggestRelatedAfterListen = true
        playerManager.setQueueAndPlay(
            songs = listOf(song),
            startIndex = 0,
            contextTitle = "Random mix",
            fromList = true
        )
    }

    fun playFromList(songs: List<Song>, startIndex: Int, contextTitle: String) {
        if (songs.isEmpty()) return
        suggestRelatedAfterListen = false
        playerManager.setQueueAndPlay(
            songs = songs,
            startIndex = startIndex,
            contextTitle = contextTitle,
            fromList = true
        )
    }

    fun playShuffled(songs: List<Song>, contextTitle: String) {
        if (songs.isEmpty()) return
        suggestRelatedAfterListen = false
        playerManager.setQueueAndPlay(
            songs = songs.shuffled(),
            startIndex = 0,
            contextTitle = contextTitle,
            fromList = true
        )
    }

    fun toggleLike(song: Song) {
        val currentlyLiked = song.id in likedSongIds.value
        val remoteCount = if (playerManager.currentSong.value?.id == song.id) {
            currentSongStats.value?.likeCount ?: 0L
        } else {
            0L
        }
        viewModelScope.launch {
            repository.toggleLike(song, currentlyLiked, remoteCount)
        }
    }

    fun likeFromDoubleTap(song: Song) {
        if (song.id !in likedSongIds.value) {
            toggleLike(song)
        }
    }

    fun toggleFollowArtist(artistName: String, imageUrl: String) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return
        val currentlyFollowed = cleanName.lowercase() in followedArtistNames.value
        viewModelScope.launch {
            val nowFollowed = repository.toggleFollowArtist(cleanName, imageUrl, currentlyFollowed)
            _snackbarEvents.tryEmit(
                if (nowFollowed) "Following $cleanName" else "Unfollowed $cleanName"
            )
        }
    }

    fun isArtistFollowed(artistName: String): Boolean {
        return artistName.trim().lowercase() in followedArtistNames.value
    }

    fun openLyricsFor(song: Song) {
        _activeLyricsSong.value = song
        loadLyrics(song)
    }

    fun loadLyrics(song: Song) {
        lyricsJob?.cancel()
        _lyricsState.value = UiState.Loading
        lyricsJob = viewModelScope.launch {
            repository.fetchLyrics(song.id)
                .onSuccess { _lyricsState.value = UiState.Success(it) }
                .onFailure { _lyricsState.value = UiState.Error(it.localizedMessage ?: "Couldn't load lyrics") }
        }
    }

    fun closeLyrics() {
        lyricsJob?.cancel()
        _activeLyricsSong.value = null
    }

    fun openCommentsFor(song: Song) {
        _activeCommentsSong.value = song
    }

    fun closeComments() {
        _activeCommentsSong.value = null
    }

    fun addComment(song: Song, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 300) return
        val name = displayName.value
        viewModelScope.launch {
            val result = repository.addComment(song, trimmed, name)
            result.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Couldn't post comment")
            }
        }
    }

    fun deleteComment(song: Song, comment: SongComment) {
        val remoteCount = currentSongStats.value?.commentCount ?: 0L
        viewModelScope.launch {
            val res = repository.deleteComment(song, comment, remoteCount)
            res.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Couldn't delete comment")
            }
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            repository.saveProfile(newName, username.value)
            _snackbarEvents.tryEmit("Display name updated")
        }
    }

    fun updateProfile(name: String, username: String) {
        viewModelScope.launch {
            repository.saveProfile(name, username)
            _snackbarEvents.tryEmit("Profile updated")
        }
    }

    /** Saves the picked music languages and rebuilds "Trending Now" for them. */
    fun setMusicLanguages(languages: Set<String>) {
        viewModelScope.launch {
            repository.prefs.saveMusicLanguages(languages)
            loadInitialFeedAndTrending(forceRefresh = true, replaceQueue = true)
        }
    }

    /** Language bar above the Music feed: one language, or null for "All". */
    fun selectMusicLanguage(language: String?) {
        setMusicLanguages(if (language == null) emptySet() else setOf(language))
    }

    fun searchArtists(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _artistSearchState.value = UiState.Success(emptyList())
            return
        }
        viewModelScope.launch {
            _artistSearchState.value = UiState.Loading
            repository.searchArtists(trimmed)
                .onSuccess { _artistSearchState.value = UiState.Success(it) }
                .onFailure { _artistSearchState.value = UiState.Error(it.localizedMessage ?: "Search failed") }
        }
    }

    fun clearArtistSearch() {
        _artistSearchState.value = UiState.Success(emptyList())
    }

    // --- Equalizer ---
    val eqSettings: StateFlow<EqSettings> = EqualizerManager.settings
    val eqInfo: StateFlow<EqualizerManager.BandInfo> = EqualizerManager.info

    /** Applies at once (so sliders feel live). Pass persist = true when the change is final. */
    fun updateEqualizer(settings: EqSettings, persist: Boolean) {
        EqualizerManager.update(settings)
        if (persist) {
            viewModelScope.launch { repository.prefs.saveEqSettings(settings) }
        }
    }

    // --- Sleep timer ---
    fun startSleepTimer(minutes: Int) = playerManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerManager.cancelSleepTimer()

    /** Called after the Firebase account is gone (or when there never was one). */
    fun wipeLocalDataAfterAccountDelete(onDone: () -> Unit) {
        viewModelScope.launch {
            playerManager.stopAndForget()
            EqualizerManager.update(EqSettings())
            repository.clearAllLocalData()
            repository.prefs.ensureInitialDisplayName()
            onDone()
        }
    }

    fun markSwipedMusicFeed() {
        if (!hasSwipedMusicFeed.value) {
            viewModelScope.launch {
                repository.prefs.markSwipedMusicFeed()
            }
        }
    }

    fun showMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    companion object {
        /** A "more songs" fetch keeps going until it has at least this many new songs. */
        private const val MORE_SONGS_BATCH = 15

        /** A song counts as "listened" after this share of it has played. */
        private const val LISTENED_PERCENT = 80L

        /** How many related songs are queued after a listened song. */
        private const val RELATED_COUNT = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EvaMusicApplication
                EvaMusicViewModel(
                    repository = app.musicRepository,
                    playerManager = app.playerManager
                )
            }
        }
    }
}
