package com.helpid.app.data

import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HelpIdApiEmergencyLinkRepository(
    private val context: Context,
    private val tokenStore: AuthTokenStore,
    private val authRepository: HelpIdApiAuthRepository
) {
    suspend fun mintOrEmpty(): String = withContext(Dispatchers.IO) {
        val baseUrl = HelpIdApiConfig.getBaseUrl(context)
        if (baseUrl.isBlank()) return@withContext ""

        val accessToken = getOrRefreshAccessToken() ?: return@withContext ""

        try {
            val (code, url) = callMintApi(baseUrl, accessToken)
            when {
                code in 200..299 -> url
                code == 401 -> {
                    val newToken = refreshAndSave() ?: return@withContext ""
                    val (retryCode, retryUrl) = callMintApi(baseUrl, newToken)
                    if (retryCode in 200..299) retryUrl else ""
                }
                else -> ""
            }
        } catch (_: IOException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun getOrRefreshAccessToken(): String? {
        if (!tokenStore.isAccessTokenExpired()) return tokenStore.getAccessToken()
        return refreshAndSave()
    }

    private suspend fun refreshAndSave(): String? {
        val refreshToken = tokenStore.getRefreshToken() ?: return null
        return when (val result = authRepository.refresh(refreshToken, Build.MODEL)) {
            is AuthResult.Success -> {
                tokenStore.saveTokens(
                    result.accessToken,
                    result.refreshToken,
                    result.userId,
                    result.accessTokenExpiresAtEpochMs,
                    result.refreshTokenExpiresAtEpochMs
                )
                result.accessToken
            }
            else -> null
        }
    }

    private fun callMintApi(baseUrl: String, accessToken: String): Pair<Int, String> {
        val conn = (URL("$baseUrl/api/v1/emergency-links/mint").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write("{}") }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                try { conn.errorStream?.close() } catch (_: Exception) {}
                conn.disconnect()
                return code to ""
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            val url = try {
                JsonParser.parseString(body)
                    .asJsonObject?.get("url")
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                    .orEmpty()
            } catch (_: Exception) { "" }
            code to url
        } catch (e: IOException) {
            try { conn.disconnect() } catch (_: Exception) {}
            throw e
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
