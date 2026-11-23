package com.example.util

import android.content.Context
import android.content.Intent
import com.example.R

object AppConstants {
    // Update this single constant with your Play Store or website link
    const val APP_LINK = "https://play.google.com/store/apps/details?id=com.aistudio.evamusic.rkmpzq"
    const val SHARE_MESSAGE = "Listen to unlimited music on EvaMusic \uD83C\uDFA7 $APP_LINK"

    const val JIOSAAVN_BASE_URL = "https://shnwazdev-jiosaavn-apii.vercel.app/"

    val EXPLORE_CATEGORIES = listOf(
        "Arijit Singh",
        "Romantic",
        "Punjabi",
        "Lo-fi",
        "Party",
        "Sad songs",
        "English pop",
        "Devotional",
        "Retro",
        "Workout"
    )

    val ROTATING_DISCOVERY_QUERIES = listOf(
        "Arijit Singh hits",
        "Bollywood romantic",
        "Punjabi top hits",
        "Hindi lo-fi chill",
        "Party anthems",
        "Jubin Nautiyal",
        "Shreya Ghoshal",
        "English pop hits"
    )
}

fun shareApp(context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, AppConstants.SHARE_MESSAGE)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

fun decodeHtmlEntities(input: String?): String {
    if (input.isNullOrBlank()) return ""
    var result = input
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
    result = Regex("&#(\\d+);").replace(result) { match ->
        match.groupValues[1].toIntOrNull()?.let { code ->
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        } ?: match.value
    }
    result = Regex("&#[xX]([0-9a-fA-F]+);").replace(result) { match ->
        match.groupValues[1].toIntOrNull(16)?.let { code ->
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
        } ?: match.value
    }
    return result.trim()
}

fun formatCompactCount(count: Long): String {
    if (count < 0) return "0"
    return when {
        count >= 1_000_000 -> {
            val value = count / 1_000_000.0
            String.format(java.util.Locale.US, "%.1fM", value).removeSuffix(".0M").let {
                if (it.endsWith("M")) it else "${it}M"
            }
        }
        count >= 1_000 -> {
            val value = count / 1_000.0
            String.format(java.util.Locale.US, "%.1fK", value).replace(".0K", "K")
        }
        else -> count.toString()
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}

fun formatRemainingMs(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "-0:00"
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    return "-${formatDurationMs(remaining)}"
}
