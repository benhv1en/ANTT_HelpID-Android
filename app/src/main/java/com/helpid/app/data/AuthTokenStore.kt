package com.helpid.app.data

import android.content.Context
import android.content.SharedPreferences
import com.helpid.app.utils.SecurePrefs

class AuthTokenStore(context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.create(context, "helpid_auth_tokens")

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        accessTokenExpiresAtEpochMs: Long,
        refreshTokenExpiresAtEpochMs: Long
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putLong(KEY_ACCESS_EXPIRES, accessTokenExpiresAtEpochMs)
            .putLong(KEY_REFRESH_EXPIRES, refreshTokenExpiresAtEpochMs)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun isAccessTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_ACCESS_EXPIRES, 0L)
        if (expiresAt == 0L) return true
        return System.currentTimeMillis() > expiresAt - BUFFER_MS
    }

    fun hasValidSession(): Boolean = getAccessToken() != null && !isAccessTokenExpired()

    /**
     * Decodes the stored JWT access token payload and returns true if the
     * permission claim "admin:metadata:read" is present.
     *
     * This is a client-side UI gate only — the backend always enforces
     * RequireAuthorization("AdminMetadata") independently.
     * No token bytes or payload content are logged.
     */
    fun isAdmin(): Boolean {
        val token = getAccessToken() ?: return false
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return false
            val encoded = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .padEnd((parts[1].length + 3) / 4 * 4, '=')
            val payloadJson = String(
                android.util.Base64.decode(encoded, android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            payloadJson.contains("\"admin:metadata:read\"")
        } catch (_: Exception) {
            false
        }
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_EXPIRES = "access_expires_at"
        private const val KEY_REFRESH_EXPIRES = "refresh_expires_at"
        private const val BUFFER_MS = 60_000L
    }
}
