package com.helpid.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.helpid.app.data.local.AppDatabase
import com.helpid.app.data.local.LocalEmergencyContact
import com.helpid.app.data.local.LocalUserProfile
import com.helpid.app.data.local.UserProfileDao
import com.helpid.app.network.HelpIdHttpClient
import com.helpid.app.utils.SecurePrefs
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLHandshakeException

/** Abstracts token read/write so tests can inject a fake without Android Keystore. */
internal interface ProfileTokenSource {
    fun getUserId(): String?
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun isExpired(): Boolean
    fun save(result: AuthResult.Success)
}

/** Abstracts HTTP calls so tests can inject a fake without a real network. */
internal interface ProfileHttpClient {
    fun get(url: String, token: String): Pair<Int, String>
    fun put(url: String, body: String, token: String): Pair<Int, String>
}

/**
 * Profile repository backed by the HelpID backend API.
 *
 * Room (SQLite) is the local truth — writes go there first.
 * Backend sync is best-effort: network failures set a pending flag instead
 * of throwing, so the UI always gets a result quickly.
 *
 * Token refresh: if the access token is expired (or a 401 is received),
 * this repository calls [AuthRepository.refresh] once and retries.
 *
 * No token, userId, email, or health data is logged.
 *
 * The primary [constructor] wires production dependencies from [Context].
 * The internal constructor accepts injected deps — used by unit tests.
 */
class HelpIdApiProfileRepository internal constructor(
    private val tokenSource: ProfileTokenSource,
    private val authRepo: AuthRepository,
    private val profileDao: UserProfileDao,
    private val syncPrefs: SharedPreferences,
    private val http: ProfileHttpClient,
    private val getBaseUrl: () -> String
) {
    constructor(context: Context) : this(
        tokenSource = AuthTokenStoreAdapter(AuthTokenStore(context)),
        authRepo = HelpIdApiAuthRepository(context),
        profileDao = AppDatabase.getDatabase(context).userProfileDao(),
        syncPrefs = SecurePrefs.create(context, "helpid_api_profile_sync"),
        http = DefaultHttpClient(),
        getBaseUrl = { HelpIdApiConfig.getBaseUrl(context) }
    )

    /**
     * Returns the current user's profile.
     * Tries backend first; saves to Room on success; falls back to Room / default on failure.
     */
    suspend fun getProfile(): UserProfile {
        val userId = tokenSource.getUserId().orEmpty()
        val token = getValidToken() ?: return roomOrDefault(userId)
        val base = getBaseUrl().ifBlank { return roomOrDefault(userId) }
        return try {
            val (code, body) = http.get("$base/api/v1/profile", token)
            when {
                code in 200..299 ->
                    parseProfile(body, userId)
                        ?.also { profileDao.insertUserProfile(toLocal(it)) }
                        ?: roomOrDefault(userId)
                code == 401 -> {
                    val fresh = refreshAndSave() ?: return roomOrDefault(userId)
                    val (rc, rb) = http.get("$base/api/v1/profile", fresh)
                    if (rc in 200..299)
                        parseProfile(rb, userId)
                            ?.also { profileDao.insertUserProfile(toLocal(it)) }
                            ?: roomOrDefault(userId)
                    else
                        roomOrDefault(userId)
                }
                else -> roomOrDefault(userId)
            }
        } catch (e: SSLHandshakeException) {
            HelpIdHttpClient.logPinFailure()
            throw e
        } catch (_: IOException) {
            roomOrDefault(userId)
        } catch (_: Exception) {
            roomOrDefault(userId)
        }
    }

    /**
     * Saves [profile] to Room immediately, then syncs to the backend.
     * Returns true when the Room write succeeds (even if backend sync fails).
     * Sets a pending sync flag when the backend is unreachable.
     */
    suspend fun updateProfile(profile: UserProfile): Boolean {
        val userId = tokenSource.getUserId().orEmpty()
        val toSave = profile.copy(userId = userId, lastUpdated = System.currentTimeMillis())
        return try {
            profileDao.insertUserProfile(toLocal(toSave))
            syncToBackend(toSave)
            true
        } catch (_: Exception) {
            setPendingSync(true)
            false
        }
    }

    fun hasPendingSync(): Boolean = syncPrefs.getBoolean(KEY_PENDING_SYNC, false)

    // ── backend sync ──────────────────────────────────────────────────────────

    private suspend fun syncToBackend(profile: UserProfile) {
        val base = getBaseUrl().ifBlank { setPendingSync(true); return }
        try {
            val token = getValidToken() ?: run { setPendingSync(true); return }
            val (code, body) = http.put("$base/api/v1/profile", buildJson(profile), token)
            when {
                code in 200..299 -> {
                    setPendingSync(false)
                    parseProfile(body, profile.userId)
                        ?.let { profileDao.insertUserProfile(toLocal(it)) }
                }
                code == 401 -> {
                    val fresh = refreshAndSave() ?: run { setPendingSync(true); return }
                    val (rc, rb) = http.put("$base/api/v1/profile", buildJson(profile), fresh)
                    if (rc in 200..299) {
                        setPendingSync(false)
                        parseProfile(rb, profile.userId)
                            ?.let { profileDao.insertUserProfile(toLocal(it)) }
                    } else {
                        setPendingSync(true)
                    }
                }
                else -> setPendingSync(true)
            }
        } catch (e: SSLHandshakeException) {
            HelpIdHttpClient.logPinFailure()
            setPendingSync(true)
        } catch (_: IOException) {
            setPendingSync(true)
        } catch (_: Exception) {
            setPendingSync(true)
        }
    }

    private fun setPendingSync(pending: Boolean) {
        syncPrefs.edit().putBoolean(KEY_PENDING_SYNC, pending).apply()
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

    // ── Room helpers ──────────────────────────────────────────────────────────

    private suspend fun roomOrDefault(userId: String): UserProfile =
        profileDao.getUserProfile(userId)?.let { toDomain(it) } ?: UserProfile.default(userId)

    // ── mapping ───────────────────────────────────────────────────────────────

    private fun toLocal(p: UserProfile) = LocalUserProfile(
        userId = p.userId,
        name = p.name,
        bloodGroup = p.bloodGroup,
        address = p.address,
        allergies = p.allergies,
        medicalNotes = p.medicalNotes,
        emergencyContacts = p.emergencyContacts.map { LocalEmergencyContact(it.name, it.phone) },
        language = p.language,
        lastUpdated = p.lastUpdated
    )

    private fun toDomain(l: LocalUserProfile) = UserProfile(
        userId = l.userId,
        name = l.name,
        bloodGroup = l.bloodGroup,
        address = l.address,
        allergies = l.allergies,
        medicalNotes = l.medicalNotes,
        emergencyContacts = l.emergencyContacts.map { EmergencyContactData(it.name, it.phone) },
        language = l.language,
        lastUpdated = l.lastUpdated
    )

    // ── companion: pure functions exposed as internal for unit tests ──────────

    companion object {
        internal const val KEY_PENDING_SYNC = "pending_sync"
        private const val DEVICE_NAME = "android"
        private const val TIMEOUT_MS = 8_000

        internal fun parseProfile(body: String, fallbackUserId: String): UserProfile? {
            return try {
                val root = JsonParser.parseString(body).asJsonObject
                val dto = root["profile"]?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return null
                UserProfile(
                    userId = dto["userId"]?.str ?: fallbackUserId,
                    name = dto["name"]?.str.orEmpty(),
                    bloodGroup = dto["bloodGroup"]?.str.orEmpty(),
                    address = dto["address"]?.str.orEmpty(),
                    language = dto["language"]?.str ?: "en",
                    allergies = dto["allergies"]?.asJsonArray
                        ?.mapNotNull { it.str } ?: emptyList(),
                    medicalNotes = dto["medicalNotes"]?.asJsonArray
                        ?.mapNotNull { it.str } ?: emptyList(),
                    emergencyContacts = dto["emergencyContacts"]?.asJsonArray
                        ?.mapNotNull { elem ->
                            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject
                                ?: return@mapNotNull null
                            EmergencyContactData(
                                name = obj["name"]?.str.orEmpty(),
                                phone = obj["phone"]?.str.orEmpty()
                            )
                        } ?: emptyList(),
                    lastUpdated = dto["lastUpdated"]?.takeIf { it.isJsonPrimitive }
                        ?.let { runCatching { it.asLong }.getOrNull() }
                        ?: System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }

        internal fun buildJson(p: UserProfile) = buildString {
            append("{")
            append("\"name\":").append(esc(p.name)).append(",")
            append("\"bloodGroup\":").append(esc(p.bloodGroup)).append(",")
            append("\"address\":").append(esc(p.address)).append(",")
            append("\"language\":").append(esc(p.language)).append(",")
            append("\"allergies\":[")
            p.allergies.forEachIndexed { i, s -> if (i > 0) append(","); append(esc(s)) }
            append("],\"medicalNotes\":[")
            p.medicalNotes.forEachIndexed { i, s -> if (i > 0) append(","); append(esc(s)) }
            append("],\"emergencyContacts\":[")
            p.emergencyContacts.forEachIndexed { i, c ->
                if (i > 0) append(",")
                append("{\"name\":").append(esc(c.name))
                append(",\"phone\":").append(esc(c.phone)).append("}")
            }
            append("]}")
        }

        internal fun esc(s: String) = buildString {
            append('"')
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '"'  -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
            append('"')
        }

        private val JsonElement.str: String?
            get() = if (isJsonNull || !isJsonPrimitive) null else asString?.ifBlank { null }
    }
}

// ── private wiring helpers ────────────────────────────────────────────────────

private class AuthTokenStoreAdapter(private val s: AuthTokenStore) : ProfileTokenSource {
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

private class DefaultHttpClient : ProfileHttpClient {
    override fun get(url: String, token: String): Pair<Int, String> {
        val conn = open(URL(url), "GET", null, token)
        val code = conn.responseCode
        val body = read(conn, code)
        conn.disconnect()
        return code to body
    }

    override fun put(url: String, body: String, token: String): Pair<Int, String> {
        val conn = open(URL(url), "PUT", body, token)
        val code = conn.responseCode
        val resp = read(conn, code)
        conn.disconnect()
        return code to resp
    }

    private fun open(url: URL, method: String, body: String?, token: String): HttpURLConnection =
        HelpIdHttpClient.openConnection(url, method).apply {
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
            }
        }

    private fun read(conn: HttpURLConnection, code: Int): String = try {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    } catch (_: Exception) {
        ""
    }
}
