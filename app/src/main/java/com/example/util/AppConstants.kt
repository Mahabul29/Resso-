package com.example.util

import android.content.Context
import android.content.Intent
import com.example.R

object AppConstants {
    // Update this single constant with your Play Store or website link
    const val APP_LINK = "https://play.google.com/store/apps/details?id=com.aistudio.evamusic.rkmpzq"
    const val SHARE_MESSAGE = "Listen to unlimited music on Resso \uD83C\uDFA7 $APP_LINK"

    // ---- Monetag ads ----
    // Ads WebView me load hote hain (native Android app me service worker sw.js kaam nahi karta).
    const val MONETAG_TAG_SRC = "https://nap5k.com/tag.min.js"
    const val MONETAG_BASE_URL = "https://nap5k.com/"

    // Har jagah ka apna zone
    const val ZONE_MUSIC_PHOTO = "12335692"      // Music page, photo ke beech me
    const val ZONE_EXPLORE_RECENT = "12335693"   // Explore, recent searches ke niche
    const val ZONE_PROFILE = "12335694"          // Profile ke niche

    // Up Next ads: har 8 songs ke baad agla zone (cycle hota rehta hai)
    val ZONES_UP_NEXT = listOf(
        "12335695", "12335696", "12335697",
        "12335702", "12335703", "12335704"
    )

    // Up Next list me har itne songs ke baad ek ad
    const val UP_NEXT_AD_INTERVAL = 8

    const val TELEGRAM_URL = "https://t.me/+8pRE58uYZZIzYjJl"

    /** Music languages offered in Settings: key is used for searching, native is only a label. */
    data class MusicLanguage(val key: String, val native: String)

    val MUSIC_LANGUAGES = listOf(
        MusicLanguage("Hindi", "हिन्दी"),
        MusicLanguage("English", "English"),
        MusicLanguage("Bengali", "বাংলা"),
        MusicLanguage("Assamese", "অসমীয়া"),
        MusicLanguage("Punjabi", "ਪੰਜਾਬੀ"),
        MusicLanguage("Bhojpuri", "भोजपुरी"),
        MusicLanguage("Haryanvi", "हरियाणवी"),
        MusicLanguage("Marathi", "मराठी"),
        MusicLanguage("Gujarati", "ગુજરાતી"),
        MusicLanguage("Tamil", "தமிழ்"),
        MusicLanguage("Telugu", "తెలుగు"),
        MusicLanguage("Kannada", "ಕನ್ನಡ"),
        MusicLanguage("Malayalam", "മലയാളം"),
        MusicLanguage("Odia", "ଓଡ଼ିଆ"),
        MusicLanguage("Urdu", "اردو")
    )

    /** Search queries built from the languages the listener picked. */
    fun languageTrendingQueries(languages: Set<String>): List<String> =
        languages.sorted().flatMap { lang -> listOf("Top $lang songs", "Latest $lang hits") }

    fun languageDiscoveryQueries(languages: Set<String>): List<String> =
        languages.sorted().flatMap { lang -> listOf("$lang romantic songs", "$lang hits", "$lang party songs") }

    const val JIOSAAVN_BASE_URL = "https://shnwazdev-jiosaavn-apii.vercel.app/"

    val EXPLORE_CATEGORIES = listOf(
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

    // One of these (picked by the day of the year) is mixed into "Trending Now" so the list
    // changes every single day, even if the trending endpoint returns the same songs.
    val DAILY_TRENDING_QUERIES = listOf(
        "Top Hindi songs",
        "Bollywood new releases",
        "Punjabi latest hits",
        "Hindi viral songs",
        "Romantic Hindi hits",
        "Bollywood party songs",
        "Hindi chill songs",
        "Latest English pop",
        "South Indian hits",
        "Hindi sad songs",
        "Bollywood dance hits",
        "Indie Hindi songs"
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

fun openTelegram(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(AppConstants.TELEGRAM_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No app can open the link: nothing else to do.
    }
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

/** Opens this app's notification settings in Android Settings. */
fun openNotificationSettings(context: Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
