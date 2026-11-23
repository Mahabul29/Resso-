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

class PlayerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    var onNeedMoreSongs: (() -> Unit)? = null
    var onSongPlayed: ((Song) -> Unit)? = null
    var onSongFailedSkipped: ((Song) -> Unit)? = null

    private var pendingAction: ((MediaController) -> Unit)? = null
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
                if (list.size - idx <= 4) {
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
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val ctrl = future.get()
                    controller = ctrl
                    ctrl.addListener(playerListener)
                    syncFromController(ctrl)
                    pendingAction?.let { action ->
                        pendingAction = null
                        action(ctrl)
                    }
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
            pendingAction = action
            connect()
        }
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
            while (isActive) {
                syncDurationAndPosition()
                delay(250L)
            }
        }
    }

    fun setQueueAndPlay(
        songs: List<Song>,
        startIndex: Int = 0,
        contextTitle: String = "Your Daily Mix",
        fromList: Boolean = false
    ) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        _queue.value = songs
        _currentIndex.value = safeIndex
        _currentSong.value = songs[safeIndex]
        _contextTitle.value = contextTitle
        _isFromList.value = fromList
        _positionMs.value = 0L
        _durationMs.value = (songs[safeIndex].duration * 1000L).coerceAtLeast(0L)

        withController { ctrl ->
            val mediaItems = songs.map { it.toMediaItem() }
            ctrl.setMediaItems(mediaItems, safeIndex, 0L)
            ctrl.prepare()
            ctrl.play()
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
        }
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
        if (list.size - index <= 4) {
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

    fun resetFromListFlag() {
        _isFromList.value = false
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
