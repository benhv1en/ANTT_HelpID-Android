package com.helpid.app.data

import android.content.Context
import com.helpid.app.BuildConfig

/**
 * Holds the configurable base URL for the HelpID backend API.
 *
 * In debug builds the default points to the Android emulator host loopback
 * (10.0.2.2 = 127.0.0.1 on the host machine running the emulator).
 * In release builds the default is empty — the URL must be set explicitly or
 * shipped via a build variant, keeping no hard-coded secret or production URL
 * in the debug APK.
 *
 * The URL is persisted in plain SharedPreferences (not encrypted) because it
 * is not sensitive — it is a server address, not a secret. The preference key
 * is "base_url" in the "helpid_api_config" file.
 */
object HelpIdApiConfig {
    internal const val PREF_NAME = "helpid_api_config"
    internal const val KEY_BASE_URL = "base_url"

    private const val DEFAULT_DEBUG_URL = "http://10.0.2.2:5080"

    fun getBaseUrl(context: Context): String {
        val stored = context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
            ?.trim()
            ?.trimEnd('/')
        if (!stored.isNullOrBlank()) return stored
        return if (BuildConfig.DEBUG) DEFAULT_DEBUG_URL else ""
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, url.trim().trimEnd('/'))
            .apply()
    }
}
