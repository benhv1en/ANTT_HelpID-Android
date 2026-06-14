package com.helpid.app.data

sealed class AuthResult {
    data class Success(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresAtEpochMs: Long,
        val refreshTokenExpiresAtEpochMs: Long
    ) : AuthResult()

    data class ValidationError(val fieldErrors: Map<String, String>) : AuthResult()
    data class ApiError(val httpStatus: Int, val errorCode: String?) : AuthResult()
    data object NetworkError : AuthResult()
}

sealed class LogoutResult {
    data object Success : LogoutResult()
    data object NetworkError : LogoutResult()
}

interface AuthRepository {
    suspend fun login(email: String, password: String, deviceName: String): AuthResult
    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
        deviceName: String
    ): AuthResult
    suspend fun refresh(refreshToken: String, deviceName: String): AuthResult
    suspend fun logout(refreshToken: String): LogoutResult
}

/** Stub — returns NetworkError until `HelpIdApiAuthRepository` is wired in the next prompt. */
class StubAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String, deviceName: String): AuthResult =
        AuthResult.NetworkError

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
        deviceName: String
    ): AuthResult = AuthResult.NetworkError

    override suspend fun refresh(refreshToken: String, deviceName: String): AuthResult =
        AuthResult.NetworkError

    override suspend fun logout(refreshToken: String): LogoutResult =
        LogoutResult.NetworkError
}
