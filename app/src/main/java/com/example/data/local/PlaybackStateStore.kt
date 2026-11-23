package com.example.data.local

import android.content.Context
import com.example.data.model.Song
import org.json.JSONObject
import java.io.File

/**
 * Remembers what was playing so that after the app is closed / killed and reopened,
 * playback continues from the same song (and position) instead of starting a fresh feed.
 *
 * - queue (heavy)      -> json file, written only when the queue changes
 * - song id + position -> SharedPreferences, written often (cheap)
 */
class PlaybackStateStore(context: Context) {

    data class Saved(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        val contextTitle: String
    )

    private val prefs = context.applicationContext
        .getSharedPreferences("eva_playback_state", Context.MODE_PRIVATE)
    private val queueFile = File(context.applicationContext.filesDir, "playback_queue.json")

    /** Blocking disk write: call from a background dispatcher. */
    @Synchronized
    fun saveQueue(songs: List<Song>, currentIndex: Int, contextTitle: String) {
        if (songs.isEmpty()) return
        val idx = currentIndex.coerceIn(0, songs.lastIndex)
        val from = (idx - KEEP_BEFORE).coerceAtLeast(0)
        val to = (idx + KEEP_AFTER + 1).coerceAtMost(songs.size)
        val window = songs.subList(from, to)
        try {
            val json = JSONObject().apply {
                put("title", contextTitle)
                put("songs", SongJson.toJson(window))
            }
            val tmp = File(queueFile.parentFile, "playback_queue.json.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(queueFile)) {
                queueFile.writeText(json.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Persistence is best-effort; never crash playback because of it.
        }
        prefs.edit().putString(KEY_SONG_ID, songs[idx].id).apply()
    }

    /** Forgets the saved queue (used by "Delete account"). */
    @Synchronized
    fun clear() {
        try {
            queueFile.delete()
        } catch (_: Exception) {
        }
        prefs.edit().clear().apply()
    }

    fun savePosition(songId: String, positionMs: Long) {
        prefs.edit()
            .putString(KEY_SONG_ID, songId)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    /** Blocking disk read: call from a background dispatcher. */
    @Synchronized
    fun load(): Saved? {
        return try {
            if (!queueFile.exists()) return null
            val json = JSONObject(queueFile.readText())
            val songs = SongJson.fromJson(json.optJSONArray("songs"))
            if (songs.isEmpty()) return null
            val savedId = prefs.getString(KEY_SONG_ID, null)
            val idx = songs.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
            val position = if (songs[idx].id == savedId) prefs.getLong(KEY_POSITION, 0L) else 0L
            Saved(
                songs = songs,
                index = idx,
                positionMs = position,
                contextTitle = json.optString("title").ifBlank { "Your Daily Mix" }
            )
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_POSITION = "position_ms"
        const val KEEP_BEFORE = 20
        const val KEEP_AFTER = 80
    }
}
