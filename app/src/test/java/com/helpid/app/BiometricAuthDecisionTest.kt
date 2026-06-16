package com.helpid.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricAuthDecisionTest {

    // ── biometric enabled ─────────────────────────────────────────────────────

    @Test
    fun `biometric enabled valid token returns BiometricLocked without refresh`() {
        val result = resolveAuthState("user-1", isBiometricEnabled = true, requiresRefresh = false)
        assertEquals(AuthState.BiometricLocked("user-1", requiresRefresh = false), result)
    }

    @Test
    fun `biometric enabled expired token returns BiometricLocked with refresh`() {
        val result = resolveAuthState("user-1", isBiometricEnabled = true, requiresRefresh = true)
        assertEquals(AuthState.BiometricLocked("user-1", requiresRefresh = true), result)
    }

    // ── biometric disabled ────────────────────────────────────────────────────

    @Test
    fun `biometric disabled valid token returns Authenticated`() {
        val result = resolveAuthState("user-1", isBiometricEnabled = false, requiresRefresh = false)
        assertEquals(AuthState.Authenticated("user-1"), result)
    }

    @Test
    fun `biometric disabled expired token returns Unauthenticated`() {
        val result = resolveAuthState("user-1", isBiometricEnabled = false, requiresRefresh = true)
        assertEquals(AuthState.Unauthenticated, result)
    }

    // ── blank userId never triggers biometric gate ────────────────────────────

    @Test
    fun `blank userId biometric enabled valid token returns Authenticated`() {
        val result = resolveAuthState("", isBiometricEnabled = true, requiresRefresh = false)
        assertEquals(AuthState.Authenticated(""), result)
    }

    @Test
    fun `blank userId biometric enabled expired token returns Unauthenticated`() {
        val result = resolveAuthState("", isBiometricEnabled = true, requiresRefresh = true)
        assertEquals(AuthState.Unauthenticated, result)
    }

    @Test
    fun `whitespace userId is treated as blank and skips biometric gate`() {
        val result = resolveAuthState("   ", isBiometricEnabled = true, requiresRefresh = false)
        // "   ".isNotBlank() == false, so biometric gate is not entered
        assertEquals(AuthState.Authenticated("   "), result)
    }

    // ── requiresRefresh propagation ───────────────────────────────────────────

    @Test
    fun `requiresRefresh false is forwarded into BiometricLocked`() {
        val state = resolveAuthState("u", isBiometricEnabled = true, requiresRefresh = false)
        require(state is AuthState.BiometricLocked)
        assertEquals(false, state.requiresRefresh)
    }

    @Test
    fun `requiresRefresh true is forwarded into BiometricLocked`() {
        val state = resolveAuthState("u", isBiometricEnabled = true, requiresRefresh = true)
        require(state is AuthState.BiometricLocked)
        assertEquals(true, state.requiresRefresh)
    }

    @Test
    fun `userId is preserved in BiometricLocked`() {
        val state = resolveAuthState("my-user-id", isBiometricEnabled = true, requiresRefresh = false)
        require(state is AuthState.BiometricLocked)
        assertEquals("my-user-id", state.userId)
    }

    @Test
    fun `userId is preserved in Authenticated`() {
        val state = resolveAuthState("my-user-id", isBiometricEnabled = false, requiresRefresh = false)
        require(state is AuthState.Authenticated)
        assertEquals("my-user-id", state.userId)
    }
}
