package com.helpid.app.data

import android.content.Context
import android.content.SharedPreferences
import com.helpid.app.utils.SecurePrefs
import java.security.MessageDigest

class BiometricPreferenceStore(context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.create(
        context.applicationContext,
        PREFS_NAME
    )

    fun isEnabledForUser(userId: String?): Boolean {
        val key = userScopedKey(KEY_ENABLED_PREFIX, userId) ?: return false
        return prefs.getBoolean(key, false)
    }

    fun setEnabledForUser(userId: String?, enabled: Boolean) {
        val key = userScopedKey(KEY_ENABLED_PREFIX, userId) ?: return
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun clearForUser(userId: String?) {
        val enabledKey = userScopedKey(KEY_ENABLED_PREFIX, userId) ?: return
        val lastUnlockedKey = userScopedKey(KEY_LAST_UNLOCKED_PREFIX, userId) ?: return
        prefs.edit()
            .remove(enabledKey)
            .remove(lastUnlockedKey)
            .apply()
    }

    fun markUnlockedForUser(userId: String?, unlockedAtEpochMs: Long = System.currentTimeMillis()) {
        val key = userScopedKey(KEY_LAST_UNLOCKED_PREFIX, userId) ?: return
        prefs.edit().putLong(key, unlockedAtEpochMs).apply()
    }

    fun getLastUnlockedAtEpochMs(userId: String?): Long? {
        val key = userScopedKey(KEY_LAST_UNLOCKED_PREFIX, userId) ?: return null
        if (!prefs.contains(key)) return null
        return prefs.getLong(key, 0L)
    }

    companion object {
        private const val PREFS_NAME = "helpid_biometric_prefs"
        private const val KEY_ENABLED_PREFIX = "biometric_enabled_user_"
        private const val KEY_LAST_UNLOCKED_PREFIX = "last_unlocked_at_epoch_ms_user_"

        internal fun userScopedKey(prefix: String, userId: String?): String? {
            val normalized = userId?.trim()
            if (normalized.isNullOrEmpty()) return null
            return prefix + sha256Hex(normalized)
        }

        internal fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
