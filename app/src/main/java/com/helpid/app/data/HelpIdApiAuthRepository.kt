package com.helpid.app.data

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.helpid.app.network.HelpIdHttpClient
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLHandshakeException

/**
 * Real implementation of [AuthRepository] that calls the HelpID backend API.
 *
 * Base URL is read from [HelpIdApiConfig]. All requests set
 * Content-Type and Accept headers. No token, password or PII is logged.
 *
 * 422 responses are parsed from ASP.NET Core ValidationProblemDetails
 * (`{ "errors": { "field": ["message"] } }`). Field keys are normalised to
 * lower-case before being placed in [AuthResult.ValidationError.fieldErrors].
 *
 * Parsing helpers are `internal` companion functions so they can be covered
 * by JVM unit tests without a real network or Android Context.
 */
class HelpIdApiAuthRepository(private val context: Context) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String,
        deviceName: String
    ): AuthResult {
        val body = buildJson {
            addString("email", email)
            addString("password", password)
            addString("deviceName", deviceName)
        }
        return postTokenEndpoint("/api/v1/auth/login", body)
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
        deviceName: String
    ): AuthResult {
        val body = buildJson {
            addString("email", email)
            addString("password", password)
            if (displayName != null) addString("displayName", displayName)
            addString("deviceName", deviceName)
        }
        return postTokenEndpoint("/api/v1/auth/register", body)
    }

    override suspend fun refresh(
        refreshToken: String,
        deviceName: String
    ): AuthResult {
        val body = buildJson {
            addString("refreshToken", refreshToken)
            addString("deviceName", deviceName)
        }
        return postTokenEndpoint("/api/v1/auth/refresh", body)
    }

    override suspend fun logout(refreshToken: String): LogoutResult {
        val baseUrl = HelpIdApiConfig.getBaseUrl(context)
        if (baseUrl.isBlank()) return LogoutResult.NetworkError
        return try {
            val body = buildJson { addString("refreshToken", refreshToken) }
            val conn = openConnection(URL("$baseUrl/api/v1/auth/logout"), "POST", body)
            val code = conn.responseCode
            drainAndDisconnect(conn)
            if (code in 200..299) LogoutResult.Success else LogoutResult.NetworkError
        } catch (e: SSLHandshakeException) {
            HelpIdHttpClient.logPinFailure()
            LogoutResult.NetworkError
        } catch (_: IOException) {
            LogoutResult.NetworkError
        } catch (_: Exception) {
            LogoutResult.NetworkError
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun postTokenEndpoint(path: String, bodyJson: String): AuthResult {
        val baseUrl = HelpIdApiConfig.getBaseUrl(context)
        if (baseUrl.isBlank()) return AuthResult.NetworkError
        return try {
            val conn = openConnection(URL("$baseUrl$path"), "POST", bodyJson)
            val code = conn.responseCode
            val raw = readBody(conn, code)
            conn.disconnect()
            parseTokenResponse(code, raw)
        } catch (e: SSLHandshakeException) {
            HelpIdHttpClient.logPinFailure()
            AuthResult.NetworkError
        } catch (_: IOException) {
            AuthResult.NetworkError
        } catch (_: Exception) {
            AuthResult.NetworkError
        }
    }

    private fun openConnection(url: URL, method: String, body: String): HttpURLConnection =
        HelpIdHttpClient.openConnection(url, method).apply {
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (method != "GET") {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
            }
        }

    private fun readBody(conn: HttpURLConnection, code: Int): String =
        try {
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (_: Exception) {
            ""
        }

    private fun drainAndDisconnect(conn: HttpURLConnection) {
        try { conn.errorStream?.close() } catch (_: Exception) {}
        try { conn.disconnect() } catch (_: Exception) {}
    }

    // ── JSON builder ─────────────────────────────────────────────────────────

    private class JsonObjectBuilder {
        private val sb = StringBuilder("{")
        private var first = true

        fun addString(key: String, value: String) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escapeJson(key)).append('"')
            sb.append(':')
            sb.append('"').append(escapeJson(value)).append('"')
        }

        override fun toString(): String = sb.append('}').toString()

        private fun escapeJson(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun buildJson(block: JsonObjectBuilder.() -> Unit): String =
        JsonObjectBuilder().apply(block).toString()

    // ── parsing (companion so unit tests can call without Context) ────────────

    companion object {
        private const val TIMEOUT_MS = 8_000
        private const val DEFAULT_ACCESS_TTL_MS = 15L * 60 * 1_000       // 15 min fallback
        private const val DEFAULT_REFRESH_TTL_MS = 30L * 24 * 60 * 60 * 1_000 // 30 days fallback

        /**
         * Parses an API auth response (login/register/refresh) to [AuthResult].
         *
         * Success shape:
         * `{ "accessToken": "…", "accessTokenExpiresAtUtc": "…",
         *    "refreshToken": "…", "refreshTokenExpiresAtUtc": "…",
         *    "user": { "id": "…" } }`
         *
         * Validation error shape (422 from ASP.NET Core):
         * `{ "errors": { "email": ["Email is required."] } }`
         */
        internal fun parseTokenResponse(httpCode: Int, body: String): AuthResult {
            if (httpCode in 200..299) {
                return parseSuccessBody(httpCode, body)
            }
            if (httpCode == 422) {
                return parseValidationError(body)
            }
            return AuthResult.ApiError(httpCode, extractTitle(body))
        }

        private fun parseSuccessBody(httpCode: Int, body: String): AuthResult {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                val accessToken = root["accessToken"]?.asStringOrNull
                    ?: return AuthResult.ApiError(httpCode, null)
                val refreshToken = root["refreshToken"]?.asStringOrNull
                    ?: return AuthResult.ApiError(httpCode, null)
                val userId = root["user"]?.asJsonObject?.get("id")?.asStringOrNull.orEmpty()
                AuthResult.Success(
                    userId = userId,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accessTokenExpiresAtEpochMs = parseIso8601ToEpochMs(
                        root["accessTokenExpiresAtUtc"]?.asStringOrNull,
                        DEFAULT_ACCESS_TTL_MS
                    ),
                    refreshTokenExpiresAtEpochMs = parseIso8601ToEpochMs(
                        root["refreshTokenExpiresAtUtc"]?.asStringOrNull,
                        DEFAULT_REFRESH_TTL_MS
                    )
                )
            } catch (_: Exception) {
                AuthResult.ApiError(httpCode, null)
            }
        }

        private fun parseValidationError(body: String): AuthResult {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                val errorsObj = root["errors"]?.asJsonObject
                    ?: return AuthResult.ApiError(422, null)
                val fieldErrors = mutableMapOf<String, String>()
                for (key in errorsObj.keySet()) {
                    val msgs = errorsObj.getAsJsonArray(key)
                    val firstMsg = msgs?.firstOrNull()?.asStringOrNull.orEmpty()
                    fieldErrors[key.lowercase()] = detectFieldCode(key.lowercase(), firstMsg)
                }
                AuthResult.ValidationError(fieldErrors)
            } catch (_: Exception) {
                AuthResult.ApiError(422, null)
            }
        }

        /**
         * Maps a backend field name + error message to a stable code string
         * that [mapFieldCode] in the UI layer can translate to a localized string.
         *
         * The backend (AuthValidation.cs) returns human-readable English messages
         * rather than structured codes. This function bridges that gap using
         * lightweight keyword detection so the UI stays locale-aware.
         */
        internal fun detectFieldCode(field: String, message: String): String {
            val m = message.lowercase()
            return when {
                field == "email" && "required" in m -> "email.required"
                field == "email" && "format" in m   -> "email.invalid"
                field == "email" && "254" in m      -> "email.too_long"
                field == "password" && "required" in m    -> "password.required"
                field == "password" && "least" in m       -> "password.too_short"
                field == "password" && "128" in m         -> "password.too_long"
                field == "displayname" && "80" in m       -> "display_name.too_long"
                field == "refreshtoken" && "required" in m -> "refresh_token.required"
                else -> "server_error"
            }
        }

        /**
         * Converts an ISO-8601 UTC timestamp string (as returned by DateTimeOffset
         * in ASP.NET Core) to epoch milliseconds.
         *
         * Handles both `"2026-06-14T10:15:00Z"` and `"2026-06-14T10:15:00+00:00"`,
         * with optional fractional seconds. Returns `now + fallbackMs` on any
         * parse failure so callers always get a non-zero expiry.
         */
        internal fun parseIso8601ToEpochMs(str: String?, fallbackMs: Long): Long {
            if (str.isNullOrBlank()) return System.currentTimeMillis() + fallbackMs
            return try {
                // Strip fractional seconds ("2026-06-14T10:15:00.123Z" → "2026-06-14T10:15:00Z")
                val noFrac = str.replace(Regex("\\.\\d+(?=[Z+\\-])"), "")

                if (noFrac.endsWith("Z")) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .parse(noFrac)
                        ?.time ?: (System.currentTimeMillis() + fallbackMs)
                } else {
                    // "+HH:MM" or "-HH:MM" → collapse colon so SimpleDateFormat Z works
                    val collapsed = noFrac.replace(
                        Regex("([+-])(\\d{2}):(\\d{2})$"),
                        "$1$2$3"
                    )
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                        .parse(collapsed)
                        ?.time ?: (System.currentTimeMillis() + fallbackMs)
                }
            } catch (_: Exception) {
                System.currentTimeMillis() + fallbackMs
            }
        }

        private fun extractTitle(body: String): String? = try {
            JsonParser.parseString(body).asJsonObject?.get("title")?.asStringOrNull
        } catch (_: Exception) {
            null
        }

        private val JsonElement.asStringOrNull: String?
            get() = if (isJsonNull || !isJsonPrimitive) null else asString?.ifBlank { null }
    }
}
