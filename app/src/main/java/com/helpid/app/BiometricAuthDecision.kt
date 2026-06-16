package com.helpid.app

/**
 * Pure function — no Android context, no store access — that maps the three
 * pieces of auth + biometric state to the correct [AuthState].
 *
 * Extracted from [AppNavigation.authenticatedOrBiometricLocked] so the
 * decision logic can be covered by JVM unit tests without mocking.
 *
 * Rules:
 *  - Blank [userId] can never be biometric-locked (nothing to lock).
 *  - When biometric is enabled, the caller still decides [requiresRefresh];
 *    the flag is forwarded so the lock-screen can trigger a backend refresh
 *    only when the access token has already expired.
 */
fun resolveAuthState(
    userId: String,
    isBiometricEnabled: Boolean,
    requiresRefresh: Boolean
): AuthState = when {
    userId.isNotBlank() && isBiometricEnabled ->
        AuthState.BiometricLocked(userId, requiresRefresh = requiresRefresh)
    requiresRefresh -> AuthState.Unauthenticated
    else -> AuthState.Authenticated(userId)
}
