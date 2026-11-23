package com.example.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language (English / Hindi). Stored in plain SharedPreferences because the choice has to
 * be read synchronously in Activity.attachBaseContext, before any Compose code runs.
 */
object AppLanguage {
    const val ENGLISH = "en"
    const val HINDI = "hi"

    private const val PREFS = "eva_settings"
    private const val KEY = "app_language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ENGLISH) ?: ENGLISH

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Returns a context whose resources use the saved language. */
    fun wrap(base: Context): Context {
        val locale = Locale(get(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

tailrec fun Context.findActivityOrNull(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
