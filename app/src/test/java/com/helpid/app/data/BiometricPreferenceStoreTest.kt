package com.helpid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPreferenceStoreTest {

    @Test
    fun `sha256Hex returns stable lowercase digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BiometricPreferenceStore.sha256Hex("abc")
        )
    }

    @Test
    fun `sha256Hex output is always 64 hex characters`() {
        listOf("", "a", "user-123", "a".repeat(1000)).forEach { input ->
            assertEquals(
                "sha256 should always be 64 chars for '$input'",
                64,
                BiometricPreferenceStore.sha256Hex(input).length
            )
        }
    }

    @Test
    fun `sha256Hex is deterministic across calls`() {
        val input = "same-user-id"
        assertEquals(
            BiometricPreferenceStore.sha256Hex(input),
            BiometricPreferenceStore.sha256Hex(input)
        )
    }

    @Test
    fun `userScopedKey hashes trimmed user id and does not expose raw id`() {
        val key = BiometricPreferenceStore.userScopedKey("enabled_", "  user-123  ")

        requireNotNull(key)
        assertTrue(key.startsWith("enabled_"))
        assertEquals("enabled_".length + 64, key.length)
        assertFalse(key.contains("user-123"))
        assertEquals(
            BiometricPreferenceStore.userScopedKey("enabled_", "user-123"),
            key
        )
    }

    @Test
    fun `userScopedKey returns null for missing user id`() {
        assertNull(BiometricPreferenceStore.userScopedKey("enabled_", null))
        assertNull(BiometricPreferenceStore.userScopedKey("enabled_", "   "))
    }

    // ── user isolation ────────────────────────────────────────────────────────

    @Test
    fun `keys for different users are different`() {
        val keyA = BiometricPreferenceStore.userScopedKey("biometric_enabled_user_", "user-A")
        val keyB = BiometricPreferenceStore.userScopedKey("biometric_enabled_user_", "user-B")

        requireNotNull(keyA)
        requireNotNull(keyB)
        assertNotEquals("Settings key for user A must differ from user B", keyA, keyB)
    }

    @Test
    fun `last-unlocked keys for different users are different`() {
        val keyA = BiometricPreferenceStore.userScopedKey("last_unlocked_at_epoch_ms_user_", "alice")
        val keyB = BiometricPreferenceStore.userScopedKey("last_unlocked_at_epoch_ms_user_", "bob")

        requireNotNull(keyA)
        requireNotNull(keyB)
        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `enabled key and last-unlocked key for same user are different`() {
        val userId = "user-xyz"
        val enabledKey = BiometricPreferenceStore.userScopedKey("biometric_enabled_user_", userId)
        val unlockedKey = BiometricPreferenceStore.userScopedKey("last_unlocked_at_epoch_ms_user_", userId)

        requireNotNull(enabledKey)
        requireNotNull(unlockedKey)
        assertNotEquals(enabledKey, unlockedKey)
    }

    @Test
    fun `same user id always produces same keys regardless of call order`() {
        val userId = "stable-user"
        val prefix = "biometric_enabled_user_"

        val first = BiometricPreferenceStore.userScopedKey(prefix, userId)
        val second = BiometricPreferenceStore.userScopedKey(prefix, userId)

        assertEquals(first, second)
    }
}
