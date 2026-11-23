package com.example.data.remote

import com.example.data.model.Song
import com.example.util.decodeHtmlEntities
import com.squareup.moshi.Moshi

object JioSaavnParser {

    private val moshiAdapter by lazy {
        Moshi.Builder().build().adapter(Any::class.java)
    }

    fun parseSongs(rawJson: String): List<Song> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parsed = try {
            moshiAdapter.fromJson(trimmed)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val itemsList: List<*> = when (parsed) {
            is List<*> -> parsed
            is Map<*, *> -> extractSongList(parsed)
            else -> return emptyList()
        }

        val songs = mutableListOf<Song>()
        val seenIds = HashSet<String>()
        for (item in itemsList) {
            val map = item as? Map<*, *> ?: continue
            val song = normalizeSong(map) ?: continue
            if (seenIds.add(song.id)) {
                songs.add(song)
            }
        }
        return songs
    }

    private fun extractSongList(root: Map<*, *>): List<*> {
        val dataVal = root["data"]
        if (dataVal is Map<*, *>) {
            (dataVal["results"] as? List<*>)?.let { return it }
            (dataVal["songs"] as? List<*>)?.let { return it }
            val nestedSongs = dataVal["songs"] as? Map<*, *>
            if (nestedSongs != null) {
                (nestedSongs["results"] as? List<*>)?.let { return it }
                (nestedSongs["data"] as? List<*>)?.let { return it }
            }
            (dataVal["trending"] as? List<*>)?.let { return it }
            (dataVal["list"] as? List<*>)?.let { return it }
        }
        if (dataVal is List<*>) return dataVal
        (root["results"] as? List<*>)?.let { return it }
        (root["songs"] as? List<*>)?.let { return it }
        val songsMap = root["songs"] as? Map<*, *>
        (songsMap?.get("results") as? List<*>)?.let { return it }
        return emptyList<Any>()
    }

    private fun normalizeSong(obj: Map<*, *>): Song? {
        val id = obj["id"].asCleanString()
        if (id.isEmpty()) return null

        val rawAudioUrl = extractDownloadUrl(obj)
        if (rawAudioUrl.isEmpty() || rawAudioUrl.contains("jiosaavn.com", ignoreCase = true)) {
            return null
        }
        val audioUrl = forceHttps(rawAudioUrl)

        val rawTitle = obj["name"].asCleanString().ifBlank { obj["title"].asCleanString() }
        val title = decodeHtmlEntities(rawTitle).ifBlank { "Untitled Track" }

        val rawAlbum = when (val albumField = obj["album"]) {
            is Map<*, *> -> albumField["name"].asCleanString()
            is String -> albumField.trim()
            else -> ""
        }
        val album = decodeHtmlEntities(rawAlbum).ifBlank { "Single" }

        val (artist, primaryArtistImage) = extractArtists(obj)
        val duration = parseDurationSeconds(obj["duration"])
        val image = extractBestImage(obj["image"])

        return Song(
            id = id,
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = album,
            duration = duration,
            image = image,
            url = audioUrl,
            primaryArtistImage = primaryArtistImage.ifBlank { image }
        )
    }

    private fun extractArtists(obj: Map<*, *>): Pair<String, String> {
        val artistsMap = obj["artists"] as? Map<*, *>
        if (artistsMap != null) {
            val primaryList = artistsMap["primary"] as? List<*>
            if (!primaryList.isNullOrEmpty()) {
                val names = mutableListOf<String>()
                var firstImg = ""
                primaryList.forEachIndexed { idx, entry ->
                    val artistMap = entry as? Map<*, *> ?: return@forEachIndexed
                    val name = decodeHtmlEntities(artistMap["name"].asCleanString())
                    if (name.isNotBlank()) names.add(name)
                    if (idx == 0) {
                        firstImg = extractBestImage(artistMap["image"])
                    }
                }
                if (names.isNotEmpty()) {
                    return names.distinct().joinToString(", ") to firstImg
                }
            }

            val allList = artistsMap["all"] as? List<*>
            if (!allList.isNullOrEmpty()) {
                val names = mutableListOf<String>()
                var firstImg = ""
                allList.forEachIndexed { idx, entry ->
                    val artistMap = entry as? Map<*, *> ?: return@forEachIndexed
                    val name = decodeHtmlEntities(artistMap["name"].asCleanString())
                    if (name.isNotBlank()) names.add(name)
                    if (idx == 0) {
                        firstImg = extractBestImage(artistMap["image"])
                    }
                }
                if (names.isNotEmpty()) {
                    return names.distinct().joinToString(", ") to firstImg
                }
            }
        }

        val fallbackStr = when (val primaryArtists = obj["primaryArtists"]) {
            is String -> primaryArtists.trim()
            is List<*> -> primaryArtists.mapNotNull { entry ->
                when (entry) {
                    is Map<*, *> -> entry["name"].asCleanString().ifBlank { null }
                    is String -> entry.trim().ifBlank { null }
                    else -> null
                }
            }.joinToString(", ")
            else -> ""
        }.ifBlank {
            obj["artist"].asCleanString()
        }.ifBlank {
            obj["singers"].asCleanString()
        }.ifBlank {
            obj["subtitle"].asCleanString()
        }

        return decodeHtmlEntities(fallbackStr) to ""
    }

    private fun extractBestImage(imageField: Any?): String {
        val rawUrl = when (imageField) {
            is List<*> -> {
                var picked = ""
                for (i in imageField.indices.reversed()) {
                    val item = imageField[i]
                    val candidate = when (item) {
                        is Map<*, *> -> item["url"].asCleanString().ifBlank { item["link"].asCleanString() }
                        is String -> item.trim()
                        else -> ""
                    }
                    if (candidate.isNotBlank()) {
                        picked = candidate
                        break
                    }
                }
                picked
            }
            is String -> imageField.trim()
            is Map<*, *> -> imageField["url"].asCleanString().ifBlank { imageField["link"].asCleanString() }
            else -> ""
        }
        if (rawUrl.isBlank()) return ""
        return forceHttps(rawUrl)
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
    }

    private fun extractDownloadUrl(obj: Map<*, *>): String {
        val downloadList = obj["downloadUrl"] as? List<*>
        if (!downloadList.isNullOrEmpty()) {
            for (i in downloadList.indices.reversed()) {
                val item = downloadList[i]
                val candidate = when (item) {
                    is Map<*, *> -> item["url"].asCleanString().ifBlank { item["link"].asCleanString() }
                    is String -> item.trim()
                    else -> ""
                }
                if (candidate.isNotBlank() && !candidate.contains("jiosaavn.com", ignoreCase = true)) {
                    return candidate
                }
            }
        }
        val mediaUrl = obj["mediaUrl"].asCleanString().ifBlank { obj["streamUrl"].asCleanString() }
        if (mediaUrl.isNotBlank() && !mediaUrl.contains("jiosaavn.com", ignoreCase = true)) {
            return mediaUrl
        }
        val directUrl = obj["url"].asCleanString()
        if (directUrl.isNotBlank() && !directUrl.contains("jiosaavn.com", ignoreCase = true)) {
            return directUrl
        }
        return ""
    }

    private fun parseDurationSeconds(raw: Any?): Int {
        return when (raw) {
            is Number -> raw.toInt().coerceAtLeast(0)
            is String -> raw.trim().toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: 0
            else -> 0
        }
    }

    private fun forceHttps(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring(7)
        } else {
            trimmed
        }
    }

    private fun Any?.asCleanString(): String = (this as? String)?.trim().orEmpty()
}
