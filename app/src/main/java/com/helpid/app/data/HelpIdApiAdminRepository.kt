package com.helpid.app.data

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// ── Data classes ──────────────────────────────────────────────────────────────

data class AdminStats(
    val totalUsers: Int,
    val totalProfiles: Int,
    val totalPublicLinks: Int,
    val auditEventsLast7Days: Int
)

data class AdminUserItem(
    val userId: String,
    val email: String,
    val displayName: String?,
    val roles: List<String>,
    val isLocked: Boolean,
    val createdAtUtc: String
)

data class AdminUsersPage(
    val users: List<AdminUserItem>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int
)

/**
 * Outcome of an admin API call — gives the screen enough detail to distinguish
 * authorization failures (403) from network failures (offline) from other errors.
 */
sealed class AdminApiResult<out T> {
    data class Ok<out T>(val value: T) : AdminApiResult<T>()
    /** HTTP 403 — admin role revoked or token lacks the required permission. */
    object Forbidden : AdminApiResult<Nothing>()
    /** Network unreachable, connection refused, or timeout. */
    object Offline : AdminApiResult<Nothing>()
    /** Any other failure: parse error, 4xx ≠ 403, 5xx. */
    object Failed : AdminApiResult<Nothing>()
}

// ── HTTP client abstraction ───────────────────────────────────────────────────

/** Abstracts HTTP calls for admin endpoints so tests can inject a fake. */
internal interface AdminHttpClient {
    fun get(url: String, token: String): Pair<Int, String>
    fun post(url: String, token: String): Pair<Int, String>
    fun delete(url: String, token: String): Pair<Int, String>
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Repository for admin API endpoints.
 *
 * Follows the same pattern as [HelpIdApiProfileRepository]:
 * - inject [ProfileTokenSource], [AuthRepository], [AdminHttpClient], [getBaseUrl]
 * - refresh token on 401, retry once
 * - return null/false on network error or non-2xx
 * - no userId/email/token/health data logged
 *
 * The primary [constructor] wires production dependencies from [Context].
 * The internal constructor accepts injected deps — used by unit tests.
 */
class HelpIdApiAdminRepository internal constructor(
    private val tokenSource: ProfileTokenSource,
    private val authRepo: AuthRepository,
    private val http: AdminHttpClient,
    private val getBaseUrl: () -> String
) {
    constructor(context: Context) : this(
        tokenSource = AdminTokenStoreAdapter(AuthTokenStore(context)),
        authRepo = HelpIdApiAuthRepository(context),
        http = DefaultAdminHttpClient(TIMEOUT_MS),
        getBaseUrl = { HelpIdApiConfig.getBaseUrl(context) }
    )

    /** Fetches dashboard statistics. */
    suspend fun getStats(): AdminApiResult<AdminStats> {
        val base = getBaseUrl().ifBlank { return AdminApiResult.Failed }
        val token = getValidToken() ?: return AdminApiResult.Failed
        val url = "$base/api/v1/admin/stats"
        return try {
            var response = http.get(url, token)
            if (response.first == 401) {
                val fresh = refreshAndSave() ?: return AdminApiResult.Failed
                response = http.get(url, fresh)
            }
            when {
                response.first in 200..299 ->
                    parseStats(response.second)?.let { AdminApiResult.Ok(it) }
                        ?: AdminApiResult.Failed
                response.first == 403 -> AdminApiResult.Forbidden
                else -> AdminApiResult.Failed
            }
        } catch (_: IOException) { AdminApiResult.Offline }
        catch (_: Exception) { AdminApiResult.Failed }
    }

    /** Fetches a paginated user list. */
    suspend fun getUsers(page: Int = 1, size: Int = 20): AdminApiResult<AdminUsersPage> {
        val base = getBaseUrl().ifBlank { return AdminApiResult.Failed }
        val token = getValidToken() ?: return AdminApiResult.Failed
        val url = "$base/api/v1/admin/users?page=$page&size=$size"
        return try {
            var response = http.get(url, token)
            if (response.first == 401) {
                val fresh = refreshAndSave() ?: return AdminApiResult.Failed
                response = http.get(url, fresh)
            }
            when {
                response.first in 200..299 ->
                    parseUsersPage(response.second)?.let { AdminApiResult.Ok(it) }
                        ?: AdminApiResult.Failed
                response.first == 403 -> AdminApiResult.Forbidden
                else -> AdminApiResult.Failed
            }
        } catch (_: IOException) { AdminApiResult.Offline }
        catch (_: Exception) { AdminApiResult.Failed }
    }

    /**
     * Assigns [roleId] to [userId].
     * Returns [AdminApiResult.Ok] on 204; [AdminApiResult.Forbidden] on 403;
     * [AdminApiResult.Offline] on network failure; [AdminApiResult.Failed] otherwise.
     */
    suspend fun assignRole(userId: String, roleId: String): AdminApiResult<Unit> {
        val base = getBaseUrl().ifBlank { return AdminApiResult.Failed }
        val token = getValidToken() ?: return AdminApiResult.Failed
        val url = "$base/api/v1/admin/users/$userId/roles/$roleId"
        return try {
            var response = http.post(url, token)
            if (response.first == 401) {
                val fresh = refreshAndSave() ?: return AdminApiResult.Failed
                response = http.post(url, fresh)
            }
            when {
                response.first == 204 -> AdminApiResult.Ok(Unit)
                response.first == 403 -> AdminApiResult.Forbidden
                else -> AdminApiResult.Failed
            }
        } catch (_: IOException) { AdminApiResult.Offline }
        catch (_: Exception) { AdminApiResult.Failed }
    }

    /**
     * Revokes [roleId] from [userId].
     * Returns [AdminApiResult.Ok] on 204; [AdminApiResult.Forbidden] on 403
     * (including server-side self-revoke guard); [AdminApiResult.Offline] on network failure;
     * [AdminApiResult.Failed] otherwise.
     */
    suspend fun revokeRole(userId: String, roleId: String): AdminApiResult<Unit> {
        val base = getBaseUrl().ifBlank { return AdminApiResult.Failed }
        val token = getValidToken() ?: return AdminApiResult.Failed
        val url = "$base/api/v1/admin/users/$userId/roles/$roleId"
        return try {
            var response = http.delete(url, token)
            if (response.first == 401) {
                val fresh = refreshAndSave() ?: return AdminApiResult.Failed
                response = http.delete(url, fresh)
            }
            when {
                response.first == 204 -> AdminApiResult.Ok(Unit)
                response.first == 403 -> AdminApiResult.Forbidden
                else -> AdminApiResult.Failed
            }
        } catch (_: IOException) { AdminApiResult.Offline }
        catch (_: Exception) { AdminApiResult.Failed }
    }

    // ── token helpers ─────────────────────────────────────────────────────────

    private suspend fun getValidToken(): String? {
        if (!tokenSource.isExpired()) return tokenSource.getAccessToken()
        return refreshAndSave()
    }

    private suspend fun refreshAndSave(): String? {
        val rt = tokenSource.getRefreshToken() ?: return null
        val result = authRepo.refresh(rt, DEVICE_NAME)
        return if (result is AuthResult.Success) {
            tokenSource.save(result)
            result.accessToken
        } else null
    }

    // ── companion: pure parsers exposed as internal for unit tests ────────────

    companion object {
        private const val DEVICE_NAME = "android"
        private const val TIMEOUT_MS = 8_000

        internal fun parseStats(body: String): AdminStats? {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                AdminStats(
                    totalUsers = root["totalUsers"]?.int ?: return null,
                    totalProfiles = root["totalProfiles"]?.int ?: return null,
                    totalPublicLinks = root["totalPublicLinks"]?.int ?: return null,
                    auditEventsLast7Days = root["auditEventsLast7Days"]?.int ?: return null
                )
            } catch (_: Exception) { null }
        }

        internal fun parseUsersPage(body: String): AdminUsersPage? {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                val usersArray = root["users"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return null
                val users = usersArray.mapNotNull { elem ->
                    val u = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    AdminUserItem(
                        userId = u["userId"]?.str ?: return@mapNotNull null,
                        email = u["email"]?.str ?: return@mapNotNull null,
                        displayName = u["displayName"]?.str,
                        roles = u["roles"]?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { it.str } ?: emptyList(),
                        isLocked = u["isLocked"]?.takeIf { it.isJsonPrimitive }
                            ?.runCatching { asBoolean }?.getOrDefault(false) ?: false,
                        createdAtUtc = u["createdAtUtc"]?.str.orEmpty()
                    )
                }
                AdminUsersPage(
                    users = users,
                    page = root["page"]?.int ?: 1,
                    pageSize = root["pageSize"]?.int ?: users.size,
                    totalCount = root["totalCount"]?.int ?: users.size
                )
            } catch (_: Exception) { null }
        }

        private val JsonElement.str: String?
            get() = if (isJsonNull || !isJsonPrimitive) null else asString?.ifBlank { null }

        private val JsonElement.int: Int?
            get() = if (isJsonNull || !isJsonPrimitive) null
                    else runCatching { asInt }.getOrNull()
    }
}

// ── private wiring helpers ────────────────────────────────────────────────────

private class AdminTokenStoreAdapter(private val s: AuthTokenStore) : ProfileTokenSource {
    override fun getUserId() = s.getUserId()
    override fun getAccessToken() = s.getAccessToken()
    override fun getRefreshToken() = s.getRefreshToken()
    override fun isExpired() = s.isAccessTokenExpired()
    override fun save(r: AuthResult.Success) = s.saveTokens(
        accessToken = r.accessToken,
        refreshToken = r.refreshToken,
        userId = r.userId,
        accessTokenExpiresAtEpochMs = r.accessTokenExpiresAtEpochMs,
        refreshTokenExpiresAtEpochMs = r.refreshTokenExpiresAtEpochMs
    )
}

private class DefaultAdminHttpClient(private val timeoutMs: Int) : AdminHttpClient {
    override fun get(url: String, token: String) = execute(url, "GET", token)
    override fun post(url: String, token: String) = execute(url, "POST", token)
    override fun delete(url: String, token: String) = execute(url, "DELETE", token)

    private fun execute(url: String, method: String, token: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        val code = conn.responseCode
        val body = try {
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (_: Exception) { "" }
        conn.disconnect()
        return code to body
    }
}
