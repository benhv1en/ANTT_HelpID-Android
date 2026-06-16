package com.helpid.app.data

import android.content.SharedPreferences
import com.helpid.app.data.local.LocalUserProfile
import com.helpid.app.data.local.UserProfileDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HelpIdApiProfileRepositoryTest {

    // ── test doubles ──────────────────────────────────────────────────────────

    private class FakeTokenSource(
        var stubUserId: String? = "user-abc",
        var stubAccessToken: String? = "access-tok",
        var stubRefreshToken: String? = "refresh-tok",
        var expired: Boolean = false
    ) : ProfileTokenSource {
        val saves = mutableListOf<AuthResult.Success>()
        override fun getUserId() = stubUserId
        override fun getAccessToken() = stubAccessToken
        override fun getRefreshToken() = stubRefreshToken
        override fun isExpired() = expired
        override fun save(result: AuthResult.Success) {
            saves += result
            stubAccessToken = result.accessToken
        }
    }

    private class FakeAuthRepo(
        var refreshResult: AuthResult = AuthResult.Success(
            userId = "user-abc",
            accessToken = "new-access-tok",
            refreshToken = "new-refresh-tok",
            accessTokenExpiresAtEpochMs = Long.MAX_VALUE,
            refreshTokenExpiresAtEpochMs = Long.MAX_VALUE
        )
    ) : AuthRepository {
        override suspend fun login(email: String, password: String, deviceName: String) =
            AuthResult.NetworkError
        override suspend fun register(
            email: String, password: String, displayName: String?, deviceName: String
        ) = AuthResult.NetworkError
        override suspend fun refresh(refreshToken: String, deviceName: String) = refreshResult
        override suspend fun logout(refreshToken: String) = LogoutResult.NetworkError
    }

    private class FakeDao : UserProfileDao {
        val store = mutableMapOf<String, LocalUserProfile>()
        var insertCount = 0
        override suspend fun getUserProfile(userId: String) = store[userId]
        override suspend fun insertUserProfile(profile: LocalUserProfile) {
            store[profile.userId] = profile
            insertCount++
        }
        override suspend fun deleteUserProfile(userId: String) { store.remove(userId) }
    }

    private class FakePrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean) =
            data[key] as? Boolean ?: defValue
        override fun contains(key: String) = key in data
        override fun getAll(): Map<String, *> = data.toMap()
        override fun getString(key: String?, defValue: String?) = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = null
        override fun getInt(key: String?, defValue: Int) = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = data[key] as? Float ?: defValue
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putBoolean(k: String, v: Boolean) =
                this.also { pending[k] = v } as SharedPreferences.Editor
            override fun putString(k: String?, v: String?) =
                this.also { pending[k!!] = v } as SharedPreferences.Editor
            override fun putStringSet(k: String?, v: MutableSet<String>?) =
                this as SharedPreferences.Editor
            override fun putInt(k: String?, v: Int) =
                this.also { pending[k!!] = v } as SharedPreferences.Editor
            override fun putLong(k: String?, v: Long) =
                this.also { pending[k!!] = v } as SharedPreferences.Editor
            override fun putFloat(k: String?, v: Float) =
                this.also { pending[k!!] = v } as SharedPreferences.Editor
            override fun remove(k: String?) =
                this.also { pending.remove(k) } as SharedPreferences.Editor
            override fun clear() = this.also { pending.clear() } as SharedPreferences.Editor
            override fun commit(): Boolean { data.putAll(pending); return true }
            override fun apply() { data.putAll(pending) }
        }
    }

    private class FakeHttpClient(
        var getQueue: ArrayDeque<Pair<Int, String>> = ArrayDeque(),
        var putQueue: ArrayDeque<Pair<Int, String>> = ArrayDeque()
    ) : ProfileHttpClient {
        override fun get(url: String, token: String) =
            getQueue.removeFirstOrNull() ?: (503 to "")
        override fun put(url: String, body: String, token: String) =
            putQueue.removeFirstOrNull() ?: (503 to "")
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    /** Profile response fixture — no real health data. */
    private val PROFILE_RESPONSE_JSON = """
        {
          "profile": {
            "userId": "user-abc",
            "name": "Test User",
            "bloodGroup": "O+",
            "address": "Test City",
            "language": "en",
            "allergies": ["none"],
            "medicalNotes": [],
            "emergencyContacts": [
              {"id": "ec-1", "name": "Contact A", "phone": "+84900000001", "relationship": null}
            ],
            "lastUpdated": 1718534400000
          }
        }
    """.trimIndent()

    private fun makeCachedLocalProfile(userId: String = "user-abc") = LocalUserProfile(
        userId = userId,
        name = "Cached Name",
        bloodGroup = "A-",
        address = "",
        allergies = emptyList(),
        medicalNotes = emptyList(),
        emergencyContacts = emptyList(),
        language = "en",
        lastUpdated = 0L
    )

    // ── setup ─────────────────────────────────────────────────────────────────

    private lateinit var tokens: FakeTokenSource
    private lateinit var authRepo: FakeAuthRepo
    private lateinit var dao: FakeDao
    private lateinit var prefs: FakePrefs
    private lateinit var http: FakeHttpClient
    private lateinit var repo: HelpIdApiProfileRepository

    @Before
    fun setup() {
        tokens = FakeTokenSource()
        authRepo = FakeAuthRepo()
        dao = FakeDao()
        prefs = FakePrefs()
        http = FakeHttpClient()
        repo = makeRepo()
    }

    private fun makeRepo(
        t: FakeTokenSource = tokens,
        a: FakeAuthRepo = authRepo,
        d: FakeDao = dao,
        p: FakePrefs = prefs,
        h: ProfileHttpClient = http
    ) = HelpIdApiProfileRepository(
        tokenSource = t,
        authRepo = a,
        profileDao = d,
        syncPrefs = p,
        http = h,
        getBaseUrl = { "http://test.local" }
    )

    // ── parseProfile — pure JSON parsing ──────────────────────────────────────

    @Test
    fun `parseProfile parses all standard fields`() {
        val result = HelpIdApiProfileRepository.parseProfile(PROFILE_RESPONSE_JSON, "fallback")

        assertNotNull(result)
        result!!
        assertEquals("user-abc", result.userId)
        assertEquals("Test User", result.name)
        assertEquals("O+", result.bloodGroup)
        assertEquals("Test City", result.address)
        assertEquals("en", result.language)
        assertEquals(listOf("none"), result.allergies)
        assertTrue(result.medicalNotes.isEmpty())
        assertEquals(1, result.emergencyContacts.size)
        assertEquals("Contact A", result.emergencyContacts[0].name)
        assertEquals("+84900000001", result.emergencyContacts[0].phone)
        assertEquals(1718534400000L, result.lastUpdated)
    }

    @Test
    fun `parseProfile uses fallbackUserId when userId field missing`() {
        val json = """{"profile":{"name":"X","bloodGroup":"A+","address":"","language":"en","allergies":[],"medicalNotes":[],"emergencyContacts":[],"lastUpdated":0}}"""
        val result = HelpIdApiProfileRepository.parseProfile(json, "fallback-id")

        assertNotNull(result)
        assertEquals("fallback-id", result!!.userId)
    }

    @Test
    fun `parseProfile missing top-level profile key returns null`() {
        assertNull(HelpIdApiProfileRepository.parseProfile("""{"data":{}}""", "u"))
    }

    @Test
    fun `parseProfile malformed JSON returns null without crash`() {
        assertNull(HelpIdApiProfileRepository.parseProfile("not json {{{{", "u"))
    }

    @Test
    fun `parseProfile empty body returns null without crash`() {
        assertNull(HelpIdApiProfileRepository.parseProfile("", "u"))
    }

    @Test
    fun `parseProfile null name and bloodGroup fall back to empty string`() {
        val json = """{"profile":{"name":null,"bloodGroup":null,"address":null,"language":null,"allergies":[],"medicalNotes":[],"emergencyContacts":[],"lastUpdated":0}}"""
        val result = HelpIdApiProfileRepository.parseProfile(json, "u")

        assertNotNull(result)
        assertEquals("", result!!.name)
        assertEquals("", result.bloodGroup)
        assertEquals("en", result.language) // null language defaults to "en"
    }

    @Test
    fun `parseProfile empty emergencyContacts list is allowed`() {
        val json = """{"profile":{"userId":"u","name":"","bloodGroup":"","address":"","language":"en","allergies":[],"medicalNotes":[],"emergencyContacts":[],"lastUpdated":0}}"""
        val result = HelpIdApiProfileRepository.parseProfile(json, "u")

        assertNotNull(result)
        assertTrue(result!!.emergencyContacts.isEmpty())
    }

    // ── buildJson — pure JSON serialization ───────────────────────────────────

    @Test
    fun `buildJson produces correct field names and values`() {
        val profile = UserProfile(
            userId = "u1",
            name = "Build Test",
            bloodGroup = "B+",
            address = "Somewhere",
            language = "vi",
            allergies = listOf("dust"),
            medicalNotes = listOf("note1"),
            emergencyContacts = listOf(EmergencyContactData("Contact", "+84900000002"))
        )
        val json = HelpIdApiProfileRepository.buildJson(profile)

        assertTrue(json.contains("\"name\":\"Build Test\""))
        assertTrue(json.contains("\"bloodGroup\":\"B+\""))
        assertTrue(json.contains("\"language\":\"vi\""))
        assertTrue(json.contains("\"allergies\":[\"dust\"]"))
        assertTrue(json.contains("\"medicalNotes\":[\"note1\"]"))
        assertTrue(json.contains("\"phone\":\"+84900000002\""))
        assertTrue(json.contains("\"name\":\"Contact\""))
    }

    @Test
    fun `buildJson escapes double quotes in string values`() {
        val profile = UserProfile(name = "Say \"hello\"", userId = "", bloodGroup = "O+", language = "en")
        val json = HelpIdApiProfileRepository.buildJson(profile)

        assertTrue("embedded quote must be escaped", json.contains("\\\"hello\\\""))
    }

    @Test
    fun `buildJson escapes backslashes in string values`() {
        val profile = UserProfile(name = "path\\to", userId = "", bloodGroup = "O+", language = "en")
        val json = HelpIdApiProfileRepository.buildJson(profile)

        assertTrue("backslash must be escaped", json.contains("path\\\\to"))
    }

    @Test
    fun `buildJson handles empty lists`() {
        val profile = UserProfile(
            userId = "u",
            name = "",
            bloodGroup = "O+",
            language = "en",
            allergies = emptyList(),
            medicalNotes = emptyList(),
            emergencyContacts = emptyList()
        )
        val json = HelpIdApiProfileRepository.buildJson(profile)

        assertTrue(json.contains("\"allergies\":[]"))
        assertTrue(json.contains("\"medicalNotes\":[]"))
        assertTrue(json.contains("\"emergencyContacts\":[]"))
    }

    // ── esc — string escaping helper ──────────────────────────────────────────

    @Test
    fun `esc wraps string in double quotes`() {
        val result = HelpIdApiProfileRepository.esc("hello")
        assertEquals("\"hello\"", result)
    }

    @Test
    fun `esc escapes newline and tab`() {
        val result = HelpIdApiProfileRepository.esc("a\nb\tc")
        assertTrue(result.contains("\\n"))
        assertTrue(result.contains("\\t"))
    }

    // ── getProfile behavior ───────────────────────────────────────────────────

    @Test
    fun `getProfile 200 returns parsed profile and inserts to Room`() = runBlocking {
        http.getQueue.add(200 to PROFILE_RESPONSE_JSON)

        val result = repo.getProfile()

        assertEquals("Test User", result.name)
        assertEquals("O+", result.bloodGroup)
        assertTrue("Room must be updated", dao.insertCount >= 1)
        assertEquals("Test User", dao.store["user-abc"]?.name)
    }

    @Test
    fun `getProfile 401 refreshes token then retries and returns profile`() = runBlocking {
        // Token appears valid but server rejects it
        tokens.expired = false
        http.getQueue.add(401 to "")                    // first call → 401
        http.getQueue.add(200 to PROFILE_RESPONSE_JSON) // retry after refresh → 200

        val result = repo.getProfile()

        assertEquals("Test User", result.name)
        assertEquals("refresh must be called once", 1, tokens.saves.size)
        assertEquals("new-access-tok", tokens.stubAccessToken)
    }

    @Test
    fun `getProfile IOException falls back to Room cache`() = runBlocking {
        dao.store["user-abc"] = makeCachedLocalProfile()
        val throwingHttp = object : ProfileHttpClient {
            override fun get(url: String, token: String): Pair<Int, String> =
                throw IOException("network timeout")
            override fun put(url: String, body: String, token: String) = 503 to ""
        }
        val r = makeRepo(h = throwingHttp)

        val result = r.getProfile()

        assertEquals("Cached Name", result.name)
    }

    @Test
    fun `getProfile no token returns Room cache when token is null`() = runBlocking {
        tokens.stubAccessToken = null
        tokens.stubRefreshToken = null
        tokens.expired = true
        dao.store["user-abc"] = makeCachedLocalProfile()

        val result = repo.getProfile()

        assertEquals("Cached Name", result.name)
    }

    // ── updateProfile behavior ────────────────────────────────────────────────

    @Test
    fun `updateProfile 200 writes to Room and clears pending flag`() = runBlocking {
        prefs.data[HelpIdApiProfileRepository.KEY_PENDING_SYNC] = true
        http.putQueue.add(200 to PROFILE_RESPONSE_JSON)
        val profile = UserProfile(
            name = "New Name", userId = "user-abc", bloodGroup = "O+", language = "en"
        )

        val success = repo.updateProfile(profile)

        assertTrue(success)
        assertTrue("Room must be written", dao.insertCount >= 1)
        assertFalse("pending flag must be cleared", repo.hasPendingSync())
    }

    @Test
    fun `updateProfile still writes to Room even when network throws IOException`() = runBlocking {
        val throwingHttp = object : ProfileHttpClient {
            override fun get(url: String, token: String) = 503 to ""
            override fun put(url: String, body: String, token: String): Pair<Int, String> =
                throw IOException("no network")
        }
        val r = makeRepo(h = throwingHttp)
        val profile = UserProfile(
            name = "Offline Save", userId = "user-abc", bloodGroup = "O+", language = "en"
        )

        val success = r.updateProfile(profile)

        assertTrue("Room save must succeed", success)
        assertEquals("Offline Save", dao.store["user-abc"]?.name)
    }

    @Test
    fun `updateProfile sets pending flag when network throws IOException`() = runBlocking {
        val throwingHttp = object : ProfileHttpClient {
            override fun get(url: String, token: String) = 503 to ""
            override fun put(url: String, body: String, token: String): Pair<Int, String> =
                throw IOException("no network")
        }
        val r = makeRepo(h = throwingHttp)
        val profile = UserProfile(
            name = "Offline Save", userId = "user-abc", bloodGroup = "O+", language = "en"
        )

        r.updateProfile(profile)

        assertTrue("pending flag must be set on network error", r.hasPendingSync())
    }

    @Test
    fun `updateProfile 500 from backend sets pending flag but still returns true`() = runBlocking {
        http.putQueue.add(500 to "")
        val profile = UserProfile(
            name = "Server Error", userId = "user-abc", bloodGroup = "O+", language = "en"
        )

        val success = repo.updateProfile(profile)

        assertTrue("Room save succeeded so must return true", success)
        assertTrue("pending flag must be set on 5xx", repo.hasPendingSync())
    }
}
