package com.example.data.remote

import com.example.data.model.ArtistInfo
import com.example.data.model.LyricLine
import com.example.data.model.Lyrics
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

    /** Reads an artist search response ({data:{results:[{name, image}]}}) into [ArtistInfo]s. */
    fun parseArtists(rawJson: String): List<ArtistInfo> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parsed = try {
            moshiAdapter.fromJson(trimmed)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val items: List<*> = when (parsed) {
            is List<*> -> parsed
            is Map<*, *> -> {
                val dataVal = parsed["data"]
                when {
                    dataVal is Map<*, *> && dataVal["results"] is List<*> -> dataVal["results"] as List<*>
                    dataVal is List<*> -> dataVal
                    parsed["results"] is List<*> -> parsed["results"] as List<*>
                    else -> emptyList<Any>()
                }
            }
            else -> emptyList<Any>()
        }

        val seen = HashSet<String>()
        val artists = mutableListOf<ArtistInfo>()
        for (item in items) {
            val map = item as? Map<*, *> ?: continue
            val name = decodeHtmlEntities(map["name"].asCleanString().ifBlank { map["title"].asCleanString() })
            if (name.isBlank() || !seen.add(name.lowercase())) continue
            artists.add(ArtistInfo(name = name, imageUrl = extractBestImage(map["image"])))
        }
        return artists
    }

    // ---- Lyrics ----------------------------------------------------------------------------
    // The lyrics routes are proxied from another service, so the exact JSON shape is not fixed.
    // This reader accepts: an LRC string ("[01:23.45] text"), plain text, or a list of
    // {time / start / startTime..., text / line / words...} objects, inside "data" or at the top.

    private val lrcTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val textKeys = listOf("text", "line", "words", "lyrics", "content", "lyric")
    private val timeKeys = listOf(
        "time", "timestamp", "start", "startTime", "start_time", "startTimeMs", "startMs",
        "start_ms", "ms", "t", "offset", "seconds"
    )
    private val containerKeys = listOf(
        "syncedLyrics", "synced", "sync", "lines", "lyrics", "lrc", "subtitles", "data", "result", "results"
    )

    fun parseLyrics(rawJson: String): Lyrics {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return Lyrics(emptyList())
        val parsed: Any? = try {
            moshiAdapter.fromJson(trimmed)
        } catch (_: Exception) {
            // Not JSON at all: treat the body itself as lyrics text.
            return Lyrics(linesFromText(trimmed))
        }
        return Lyrics(findLyricLines(parsed, 0))
    }

    private fun findLyricLines(node: Any?, depth: Int): List<LyricLine> {
        if (depth > 4 || node == null) return emptyList()
        return when (node) {
            is String -> linesFromText(node)
            is List<*> -> linesFromList(node)
            is Map<*, *> -> {
                for (key in containerKeys) {
                    val child = node[key] ?: continue
                    val found = findLyricLines(child, depth + 1)
                    if (found.isNotEmpty()) return found
                }
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun linesFromList(list: List<*>): List<LyricLine> {
        if (list.isEmpty()) return emptyList()
        // A list of plain strings: one lyric line each.
        if (list.all { it is String }) {
            return list.flatMap { linesFromText(it as String) }
        }
        val rawTimes = ArrayList<Pair<Double?, String>>()
        var timeKeyUsed: String? = null
        for (item in list) {
            val map = item as? Map<*, *> ?: continue
            val text = textKeys.firstNotNullOfOrNull { k ->
                (map[k] as? String)?.let { decodeHtmlEntities(it) }?.takeIf { it.isNotEmpty() }
            } ?: ""
            var time: Double? = null
            for (k in timeKeys) {
                val v = map[k] ?: continue
                time = parseTimeValue(v)
                if (time != null) {
                    timeKeyUsed = timeKeyUsed ?: k
                    break
                }
            }
            rawTimes.add(time to text)
        }
        if (rawTimes.isEmpty()) return emptyList()

        val numbers = rawTimes.mapNotNull { it.first }
        // Seconds or milliseconds? Key names win; otherwise a fractional or small max means seconds.
        val key = timeKeyUsed.orEmpty().lowercase()
        val isMillis = when {
            numbers.isEmpty() -> false
            key.contains("ms") || key.contains("milli") -> true
            key.contains("sec") -> false
            else -> numbers.max() > 1000.0 && numbers.all { it == Math.floor(it) }
        }
        return rawTimes.map { (t, text) ->
            LyricLine(t?.let { if (isMillis) it.toLong() else (it * 1000.0).toLong() }, text)
        }
    }

    /** Number, "12.5", or clock text such as "01:23.45" -> value in the item's own unit. */
    private fun parseTimeValue(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> {
            val s = v.trim()
            s.toDoubleOrNull() ?: run {
                val parts = s.split(":")
                if (parts.size in 2..3 && parts.all { it.toDoubleOrNull() != null }) {
                    parts.fold(0.0) { acc, p -> acc * 60.0 + p.toDouble() }
                } else {
                    null
                }
            }
        }
        else -> null
    }

    private fun linesFromText(raw: String): List<LyricLine> {
        val cleaned = decodeHtmlEntities(
            raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        ).replace("\r", "")
        if (cleaned.isBlank()) return emptyList()

        val out = ArrayList<LyricLine>()
        var anyTimed = false
        for (rawLine in cleaned.split("\n")) {
            val tags = lrcTag.findAll(rawLine).toList()
            if (tags.isEmpty()) {
                out.add(LyricLine(null, rawLine.trim()))
                continue
            }
            anyTimed = true
            val text = rawLine.replace(lrcTag, "").trim()
            for (tag in tags) {
                val min = tag.groupValues[1].toLong()
                val sec = tag.groupValues[2].toLong()
                val fraction = tag.groupValues[3]
                val ms = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                out.add(LyricLine(min * 60_000L + sec * 1000L + ms, text))
            }
        }
        val lines = if (anyTimed) {
            // Metadata lines like "[ar:Name]" have no time tag match and no real lyric: drop them.
            out.filter { it.timeMs != null }.sortedBy { it.timeMs }
        } else {
            out
        }
        // Keep blank lines between stanzas, but not at the start/end or doubled.
        val result = ArrayList<LyricLine>()
        for (line in lines) {
            if (line.text.isEmpty() && (result.isEmpty() || result.last().text.isEmpty()) && line.timeMs == null) continue
            result.add(line)
        }
        while (result.isNotEmpty() && result.last().text.isEmpty() && result.last().timeMs == null) {
            result.removeAt(result.lastIndex)
        }
        return result
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
            primaryArtistImage = primaryArtistImage.ifBlank { image },
            language = obj["language"].asCleanString().lowercase()
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
