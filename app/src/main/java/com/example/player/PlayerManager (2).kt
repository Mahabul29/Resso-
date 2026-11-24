package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.PlaybackStateStore
import com.example.data.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerManager(
    private val context: Context,
    private val store: PlaybackStateStore = PlaybackStateStore(context)
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _contextTitle = MutableStateFlow("Your Daily Mix")
    val contextTitle: StateFlow<String> = _contextTitle.asStateFlow()

    private val _isFromList = MutableStateFlow(false)
    val isFromList: StateFlow<Boolean> = _isFromList.asStateFlow()

    // --- Sleep timer ---
    private val _sleepTimerEndMs = MutableStateFlow<Long?>(null)

    /** Wall-clock time (System.currentTimeMillis) when playback will pause, or null if no timer. */
    val sleepTimerEndMs: StateFlow<Long?> = _sleepTimerEndMs.asStateFlow()
    private var sleepJob: Job? = null

    var onNeedMoreSongs: (() -> Unit)? = null
    var onSongPlayed: ((Song) -> Unit)? = null
    var onSongFailedSkipped: ((Song) -> Unit)? = null

    /** Called whenever the whole queue is replaced (user picked a new song / list). */
    var onQueueReplaced: (() -> Unit)? = null

    private val pendingActions = ArrayList<(MediaController) -> Unit>()
    private var waitingForMoreSongs = false
    private var lastRecordedSongId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
                _currentSong.value?.let { song ->
                    if (lastRecordedSongId != song.id) {
                        lastRecordedSongId = song.id
                        onSongPlayed?.invoke(song)
                    }
                }
            } else {
                positionJob?.cancel()
                savePositionNow()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            syncDurationAndPosition()
            if (playbackState == Player.STATE_ENDED) {
                val ctrl = controller ?: return
                if (ctrl.hasNextMediaItem()) {
                    ctrl.seekToNextMediaItem()
                    ctrl.play()
                } else {
                    // Reached the end of the queue: fetch more and continue automatically.
                    waitingForMoreSongs = true
                    onNeedMoreSongs?.invoke()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val idx = ctrl.currentMediaItemIndex
            val list = _queue.value
            if (idx in list.indices) {
                _currentIndex.value = idx
                val song = list[idx]
                _currentSong.value = song
                _positionMs.value = 0L
                _durationMs.value = (song.duration * 1000L).coerceAtLeast(0L)
                if (ctrl.isPlaying && lastRecordedSongId != song.id) {
                    lastRecordedSongId = song.id
                    onSongPlayed?.invoke(song)
                }
                store.savePosition(song.id, 0L)
                if (list.size - idx <= NEED_MORE_THRESHOLD) {
                    onNeedMoreSongs?.invoke()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w("PlayerManager", "Playback failed, auto-skipping: ${error.message}")
            val ctrl = controller ?: return
            val failedSong = _currentSong.value
            if (failedSong != null) {
                onSongFailedSkipped?.invoke(failedSong)
            }
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
                ctrl.prepare()
                ctrl.play()
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // The playback service was stopped (e.g. app swiped away). Reconnect lazily
                    // the next time something needs the player.
                    this@PlayerManager.controller = null
                    controllerFuture = null
                    positionJob?.cancel()
                    _isPlaying.value = false
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val ctrl = future.get()
                    controller = ctrl
                    ctrl.addListener(playerListener)
                    rehydrateIfServiceIsEmpty(ctrl)
                    syncFromController(ctrl)
                    val actions = pendingActions.toList()
                    pendingActions.clear()
                    actions.forEach { it(ctrl) }
                } catch (e: Exception) {
                    Log.e("PlayerManager", "Failed to connect MediaController", e)
                    controllerFuture = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        val ctrl = controller
        if (ctrl != null) {
            action(ctrl)
        } else {
            pendingActions.add(action)
            connect()
        }
    }

    /**
     * A freshly started playback service has an empty playlist. If we still remember a queue
     * (process survived, service didn't), load it back so the same song is ready to play.
     */
    private fun rehydrateIfServiceIsEmpty(ctrl: MediaController) {
        val songs = _queue.value
        if (ctrl.mediaItemCount != 0 || songs.isEmpty()) return
        val idx = _currentIndex.value.coerceIn(0, songs.lastIndex)
        ctrl.setMediaItems(songs.map { it.toMediaItem() }, idx, _positionMs.value)
        ctrl.prepare()
    }

    private fun persistQueue() {
        val songs = _queue.value
        if (songs.isEmpty()) return
        val idx = _currentIndex.value
        val title = _contextTitle.value
        ioScope.launch { store.saveQueue(songs, idx, title) }
    }

    private fun savePositionNow() {
        val song = _currentSong.value ?: return
        store.savePosition(song.id, _positionMs.value)
    }

    /**
     * Restores what was playing when the app was last closed. Returns true if a queue is
     * (now) available, so callers know not to start a brand-new feed.
     */
    suspend fun restoreSavedPlayback(autoPlay: Boolean = true): Boolean {
        if (_queue.value.isNotEmpty()) return true
        val saved = withContext(Dispatchers.IO) { store.load() } ?: return false
        if (_queue.value.isNotEmpty()) return true
        setQueueAndPlay(
            songs = saved.songs,
            startIndex = saved.index,
            contextTitle = saved.contextTitle,
            fromList = false,
            startPositionMs = saved.positionMs,
            autoPlay = autoPlay,
            isRestore = true
        )
        return true
    }

    private fun syncFromController(ctrl: MediaController) {
        _isPlaying.value = ctrl.isPlaying
        _isBuffering.value = ctrl.playbackState == Player.STATE_BUFFERING
        val list = _queue.value
        val idx = ctrl.currentMediaItemIndex
        if (idx in list.indices) {
            _currentIndex.value = idx
            _currentSong.value = list[idx]
        }
        syncDurationAndPosition()
        if (ctrl.isPlaying) {
            startPositionUpdates()
        }
    }

    private fun syncDurationAndPosition() {
        val ctrl = controller ?: return
        val fallbackDuration = (_currentSong.value?.duration ?: 0) * 1000L
        val dur = ctrl.duration.takeIf { it > 0L } ?: fallbackDuration
        _durationMs.value = dur.coerceAtLeast(0L)
        _positionMs.value = ctrl.currentPosition.coerceAtLeast(0L)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var ticks = 0
            while (isActive) {
                syncDurationAndPosition()
                delay(250L)
                if (++ticks % 20 == 0) savePositionNow()
            }
        }
    }

    fun setQueueAndPlay(
        songs: List<Song>,
        startIndex: Int = 0,
        contextTitle: String = "Your Daily Mix",
        fromList: Boolean = false,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        isRestore: Boolean = false
    ) {
        if (songs.isEmpty()) return
        waitingForMoreSongs = false
        if (!isRestore) onQueueReplaced?.invoke()
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        _queue.value = songs
        _currentIndex.value = safeIndex
        _currentSong.value = songs[safeIndex]
        _contextTitle.value = contextTitle
        _isFromList.value = fromList
        _positionMs.value = startPositionMs.coerceAtLeast(0L)
        _durationMs.value = (songs[safeIndex].duration * 1000L).coerceAtLeast(0L)

        withController { ctrl ->
            val mediaItems = songs.map { it.toMediaItem() }
            ctrl.setMediaItems(mediaItems, safeIndex, startPositionMs.coerceAtLeast(0L))
            ctrl.prepare()
            if (autoPlay) ctrl.play()
        }

        if (!isRestore) {
            persistQueue()
            store.savePosition(songs[safeIndex].id, 0L)
        }
        // Short queue (e.g. a single tapped song): immediately fetch related songs.
        if (songs.size - safeIndex <= NEED_MORE_THRESHOLD) {
            onNeedMoreSongs?.invoke()
        }
    }

    fun appendSongs(newSongs: List<Song>) {
        if (newSongs.isEmpty()) return
        val existingIds = _queue.value.map { it.id }.toHashSet()
        val unique = newSongs.filter { it.id !in existingIds }
        if (unique.isEmpty()) return

        _queue.value = _queue.value + unique
        withController { ctrl ->
            ctrl.addMediaItems(unique.map { it.toMediaItem() })
            if (waitingForMoreSongs && ctrl.playbackState == Player.STATE_ENDED && ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
                ctrl.play()
            }
            waitingForMoreSongs = false
        }
        persistQueue()
    }

    fun playNext(song: Song) {
        val current = _queue.value.toMutableList()
        if (current.isEmpty()) {
            setQueueAndPlay(listOf(song), 0, "Up Next", fromList = false)
            return
        }
        val curIdx = _currentIndex.value.coerceIn(0, current.lastIndex)
        val existingIdx = current.indexOfFirst { it.id == song.id }
        if (existingIdx == curIdx) return
        if (existingIdx != -1) {
            current.removeAt(existingIdx)
        }
        val adjustedCurIdx = current.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(0)
        val insertIdx = (adjustedCurIdx + 1).coerceAtMost(current.size)
        current.add(insertIdx, song)
        _queue.value = current
        _currentIndex.value = adjustedCurIdx

        withController { ctrl ->
            if (existingIdx != -1 && existingIdx < ctrl.mediaItemCount) {
                ctrl.removeMediaItem(existingIdx)
            }
            val playerInsertIdx = (ctrl.currentMediaItemIndex + 1).coerceIn(0, ctrl.mediaItemCount)
            ctrl.addMediaItem(playerInsertIdx, song.toMediaItem())
        }
        persistQueue()
    }

    /** Puts [newSongs] right after the song that is playing now (before the rest of the queue). */
    fun insertAfterCurrent(newSongs: List<Song>) {
        val current = _queue.value
        if (current.isEmpty() || newSongs.isEmpty()) return
        val existingIds = current.mapTo(HashSet()) { it.id }
        val unique = newSongs.filter { it.id !in existingIds }
        if (unique.isEmpty()) return

        val curIdx = _currentIndex.value.coerceIn(0, current.lastIndex)
        val insertIdx = curIdx + 1
        _queue.value = current.toMutableList().apply { addAll(insertIdx, unique) }

        withController { ctrl ->
            val playerInsertIdx = (ctrl.currentMediaItemIndex + 1).coerceIn(0, ctrl.mediaItemCount)
            ctrl.addMediaItems(playerInsertIdx, unique.map { it.toMediaItem() })
        }
        persistQueue()
    }

    fun addToQueue(song: Song) {
        val current = _queue.value
        if (current.isEmpty()) {
            setQueueAndPlay(listOf(song), 0, "Queue", fromList = false)
            return
        }
        if (current.any { it.id == song.id }) return
        _queue.value = current + song
        withController { ctrl ->
            ctrl.addMediaItem(song.toMediaItem())
        }
        persistQueue()
    }

    fun selectQueueIndex(index: Int) {
        val list = _queue.value
        if (index !in list.indices) return
        _currentIndex.value = index
        _currentSong.value = list[index]
        _positionMs.value = 0L
        _durationMs.value = (list[index].duration * 1000L).coerceAtLeast(0L)

        withController { ctrl ->
            if (ctrl.currentMediaItemIndex != index) {
                ctrl.seekToDefaultPosition(index)
            }
            if (ctrl.playbackState == Player.STATE_IDLE) {
                ctrl.prepare()
            }
            ctrl.play()
        }
        store.savePosition(list[index].id, 0L)
        if (list.size - index <= NEED_MORE_THRESHOLD) {
            onNeedMoreSongs?.invoke()
        }
    }

    fun togglePlayPause() {
        withController { ctrl ->
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                if (ctrl.playbackState == Player.STATE_IDLE) {
                    ctrl.prepare()
                }
                ctrl.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        _positionMs.value = clamped
        withController { ctrl ->
            ctrl.seekTo(clamped)
        }
    }

    /** Pauses playback after [minutes]. Starting a new timer replaces the old one. */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepJob?.cancel()
        val durationMs = minutes * 60_000L
        _sleepTimerEndMs.value = System.currentTimeMillis() + durationMs
        sleepJob = scope.launch {
            delay(durationMs)
            withController { ctrl -> ctrl.pause() }
            _sleepTimerEndMs.value = null
            sleepJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimerEndMs.value = null
    }

    /** Pauses playback and forgets the saved queue (used by "Delete account"). */
    fun stopAndForget() {
        cancelSleepTimer()
        withController { ctrl -> ctrl.pause() }
        ioScope.launch { store.clear() }
    }

    fun resetFromListFlag() {
        _isFromList.value = false
    }

    private companion object {
        /** When this few songs are left after the current one, load more ("Up Next" never ends). */
        const val NEED_MORE_THRESHOLD = 6
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                if (image.isNotBlank()) {
                    setArtworkUri(Uri.parse(image))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }
}
