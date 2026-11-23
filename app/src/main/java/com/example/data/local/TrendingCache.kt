package com.example.data.local

import android.content.Context
import com.example.data.model.Song
import org.json.JSONArray

/** Stores the last computed "Trending Now" list together with the day it was built for. */
class TrendingCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("eva_trending_cache", Context.MODE_PRIVATE)

    fun load(): Pair<String, List<Song>>? {
        val day = prefs.getString("day", null) ?: return null
        val raw = prefs.getString("songs", null) ?: return null
        return try {
            val songs = SongJson.fromJson(JSONArray(raw))
            if (songs.isEmpty()) null else day to songs
        } catch (_: Exception) {
            null
        }
    }

    fun save(day: String, songs: List<Song>) {
        prefs.edit()
            .putString("day", day)
            .putString("songs", SongJson.toJson(songs).toString())
            .apply()
    }
}
