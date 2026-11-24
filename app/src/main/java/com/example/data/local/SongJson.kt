package com.example.data.local

import com.example.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/** Tiny JSON helpers so we can persist songs without touching the Room schema. */
object SongJson {

    fun toJson(songs: List<Song>): JSONArray {
        val arr = JSONArray()
        songs.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album)
                    put("duration", s.duration)
                    put("image", s.image)
                    put("url", s.url)
                    put("primaryArtistImage", s.primaryArtistImage)
                    put("language", s.language)
                }
            )
        }
        return arr
    }

    fun fromJson(arr: JSONArray?): List<Song> {
        if (arr == null) return emptyList()
        val out = ArrayList<Song>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val url = o.optString("url")
            if (id.isBlank() || url.isBlank()) continue
            out.add(
                Song(
                    id = id,
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    duration = o.optInt("duration"),
                    image = o.optString("image"),
                    url = url,
                    primaryArtistImage = o.optString("primaryArtistImage"),
                    language = o.optString("language")
                )
            )
        }
        return out
    }
}
