package com.example.data.model

/** One lyric line. [timeMs] is null when the source has no timing for it. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>) {
    /** True when lines carry timestamps, so they can follow the song ("karaoke style"). */
    val isSynced: Boolean get() = lines.any { it.timeMs != null }
    val isEmpty: Boolean get() = lines.isEmpty()
}
