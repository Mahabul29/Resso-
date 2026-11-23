package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EvaMusicApplication
import com.example.data.firebase.authStateFlow
import com.example.data.model.ArtistInfo
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.data.model.SongStats
import com.example.data.repository.MusicRepository
import com.example.player.PlayerManager
import com.example.util.AppConstants
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    val authUser: StateFlow<FirebaseUser?> = Firebase.auth.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = Firebase.auth.currentUser
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

    private var isLoadingMore = false
    private var rotatingQueryIndex = 0

    init {
        viewModelScope.launch {
            repository.prefs.ensureInitialDisplayName()
        }

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

        loadInitialFeedAndTrending()
        observeDebouncedSearch()
    }

    fun loadInitialFeedAndTrending() {
        viewModelScope.launch {
            _feedState.value = UiState.Loading
            _trendingState.value = UiState.Loading
            val result = repository.fetchTrendingSongs(30)
            result.onSuccess { songs ->
                _trendingState.value = UiState.Success(songs)
                _feedState.value = UiState.Success(songs)
                if (playerManager.queue.value.isEmpty() && songs.isNotEmpty()) {
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
                    } else {
                        executeSearch(query, saveHistory = true)
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
        viewModelScope.launch {
            executeSearch(category, saveHistory = true)
        }
    }

    fun onRecentSearchSelected(query: String) {
        _selectedCategory.value = null
        _searchQuery.value = query
        viewModelScope.launch {
            executeSearch(query, saveHistory = true)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _searchResultsState.value = UiState.Success(emptyList())
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    private suspend fun executeSearch(query: String, saveHistory: Boolean) {
        _searchResultsState.value = UiState.Loading
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

    private fun loadMoreSongsForQueue() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val currentSong = playerManager.currentSong.value
                val moreSongs = mutableListOf<Song>()
                if (currentSong != null) {
                    moreSongs.addAll(repository.fetchSimilarSongs(currentSong))
                }
                if (moreSongs.size < 8) {
                    val queries = AppConstants.ROTATING_DISCOVERY_QUERIES
                    val nextQuery = queries[rotatingQueryIndex % queries.size]
                    rotatingQueryIndex++
                    moreSongs.addAll(repository.searchSongs(nextQuery, 20).getOrDefault(emptyList()))
                }
                if (moreSongs.isNotEmpty()) {
                    playerManager.appendSongs(moreSongs)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun playFromList(songs: List<Song>, startIndex: Int, contextTitle: String) {
        if (songs.isEmpty()) return
        playerManager.setQueueAndPlay(
            songs = songs,
            startIndex = startIndex,
            contextTitle = contextTitle,
            fromList = true
        )
    }

    fun playShuffled(songs: List<Song>, contextTitle: String) {
        if (songs.isEmpty()) return
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
            repository.prefs.updateDisplayName(newName)
            _snackbarEvents.tryEmit("Display name updated")
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
