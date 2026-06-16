package com.helpid.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HelpIdApiAdminRepositoryTest {

    // ── test doubles ──────────────────────────────────────────────────────────

    private class FakeTokenSource(
        var stubAccessToken: String? = "access-tok",
        var stubRefreshToken: String? = "refresh-tok",
        var expired: Boolean = false
    ) : ProfileTokenSource {
        val saves = mutableListOf<AuthResult.Success>()
        override fun getUserId() = "user-admin"
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
            userId = "user-admin",
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

    private class FakeAdminHttpClient(
        val getQueue: ArrayDeque<Pair<Int, String>> = ArrayDeque(),
        val postQueue: ArrayDeque<Pair<Int, String>> = ArrayDeque(),
        val deleteQueue: ArrayDeque<Pair<Int, String>> = ArrayDeque()
    ) : AdminHttpClient {
        override fun get(url: String, token: String) =
            getQueue.removeFirstOrNull() ?: (503 to "")
        override fun post(url: String, token: String) =
            postQueue.removeFirstOrNull() ?: (503 to "")
        override fun delete(url: String, token: String) =
            deleteQueue.removeFirstOrNull() ?: (503 to "")
    }

    // ── fixtures — no real health data ────────────────────────────────────────

    private val STATS_JSON = """
        {
          "totalUsers": 42,
          "totalProfiles": 38,
          "totalPublicLinks": 15,
          "auditEventsLast7Days": 7
        }
    """.trimIndent()

    private val USERS_PAGE_JSON = """
        {
          "users": [
            {
              "userId": "user-1",
              "email": "user1@example.com",
              "displayName": "User One",
              "roles": ["role_user"],
              "isLocked": false,
              "createdAtUtc": "2024-01-15T08:00:00Z"
            }
          ],
          "page": 1,
          "pageSize": 20,
          "totalCount": 1
        }
    """.trimIndent()

    /** Backend response when the requested page exceeds the last page. */
    private val EMPTY_PAGE_JSON = """
        {
          "users": [],
          "page": 99,
          "pageSize": 20,
          "totalCount": 1
        }
    """.trimIndent()

    // ── setup ─────────────────────────────────────────────────────────────────

    private lateinit var tokens: FakeTokenSource
    private lateinit var authRepo: FakeAuthRepo
    private lateinit var http: FakeAdminHttpClient
    private lateinit var repo: HelpIdApiAdminRepository

    @Before
    fun setup() {
        tokens = FakeTokenSource()
        authRepo = FakeAuthRepo()
        http = FakeAdminHttpClient()
        repo = makeRepo()
    }

    private fun makeRepo(
        t: FakeTokenSource = tokens,
        a: FakeAuthRepo = authRepo,
        h: AdminHttpClient = http
    ) = HelpIdApiAdminRepository(
        tokenSource = t,
        authRepo = a,
        http = h,
        getBaseUrl = { "http://test.local" }
    )

    // ── (1) getStats 200 → parse all fields ───────────────────────────────────

    @Test
    fun `getStats 200 parses all four stat fields correctly`() = runBlocking {
        http.getQueue.add(200 to STATS_JSON)

        val result = repo.getStats()

        assertTrue("expected Ok result", result is AdminApiResult.Ok)
        val stats = (result as AdminApiResult.Ok).value
        assertEquals(42, stats.totalUsers)
        assertEquals(38, stats.totalProfiles)
        assertEquals(15, stats.totalPublicLinks)
        assertEquals(7, stats.auditEventsLast7Days)
    }

    // ── (2) getStats IOException → Offline, no crash ──────────────────────────

    @Test
    fun `getStats IOException returns Offline and does not crash`() = runBlocking {
        val throwingHttp = object : AdminHttpClient {
            override fun get(url: String, token: String): Pair<Int, String> =
                throw IOException("connection refused")
            override fun post(url: String, token: String) = 503 to ""
            override fun delete(url: String, token: String) = 503 to ""
        }
        val r = makeRepo(h = throwingHttp)

        val result = r.getStats()

        assertTrue("IOException must map to Offline", result is AdminApiResult.Offline)
    }

    // ── (3) getStats 401 → refresh token, retry, return stats ─────────────────

    @Test
    fun `getStats 401 refreshes token then retries and returns stats`() = runBlocking {
        http.getQueue.add(401 to "")          // first attempt → rejected
        http.getQueue.add(200 to STATS_JSON)  // retry after token refresh → ok

        val result = repo.getStats()

        assertTrue("expected Ok after refresh+retry", result is AdminApiResult.Ok)
        assertEquals("refresh must be called exactly once", 1, tokens.saves.size)
        assertEquals("new-access-tok", tokens.stubAccessToken)
        assertEquals(42, (result as AdminApiResult.Ok).value.totalUsers)
    }

    // ── (4) getStats 403 → Forbidden ──────────────────────────────────────────

    @Test
    fun `getStats 403 returns Forbidden`() = runBlocking {
        http.getQueue.add(403 to "")

        val result = repo.getStats()

        assertTrue("403 must map to Forbidden", result is AdminApiResult.Forbidden)
    }

    // ── (5) getUsers 200 → parse page/items/totalCount ────────────────────────

    @Test
    fun `getUsers 200 parses page number, item list, and totalCount`() = runBlocking {
        http.getQueue.add(200 to USERS_PAGE_JSON)

        val result = repo.getUsers(page = 1, size = 20)

        assertTrue("expected Ok result", result is AdminApiResult.Ok)
        val page = (result as AdminApiResult.Ok).value
        assertEquals(1, page.page)
        assertEquals(1, page.totalCount)
        assertEquals(1, page.users.size)
        assertEquals("user-1", page.users[0].userId)
        assertEquals("user1@example.com", page.users[0].email)
        assertEquals(listOf("role_user"), page.users[0].roles)
        assertFalse("user must not be locked", page.users[0].isLocked)
    }

    // ── (6) getUsers out-of-range page → Ok with empty list ───────────────────

    @Test
    fun `getUsers out-of-range page returns Ok with empty user list and does not crash`() =
        runBlocking {
            http.getQueue.add(200 to EMPTY_PAGE_JSON)

            val result = repo.getUsers(page = 99, size = 20)

            assertTrue("expected Ok for empty page", result is AdminApiResult.Ok)
            val page = (result as AdminApiResult.Ok).value
            assertTrue("users list must be empty for out-of-range page", page.users.isEmpty())
            assertEquals(99, page.page)
            assertEquals(1, page.totalCount)
        }

    // ── (7) assignRole 204 → Ok ───────────────────────────────────────────────

    @Test
    fun `assignRole 204 returns Ok`() = runBlocking {
        http.postQueue.add(204 to "")

        val result = repo.assignRole("user-1", "role_admin")

        assertTrue("204 must return Ok", result is AdminApiResult.Ok)
    }

    // ── (8) assignRole IOException → Offline, no crash ────────────────────────

    @Test
    fun `assignRole IOException returns Offline and does not crash`() = runBlocking {
        val throwingHttp = object : AdminHttpClient {
            override fun get(url: String, token: String) = 503 to ""
            override fun post(url: String, token: String): Pair<Int, String> =
                throw IOException("network error")
            override fun delete(url: String, token: String) = 503 to ""
        }
        val r = makeRepo(h = throwingHttp)

        val result = r.assignRole("user-1", "role_admin")

        assertTrue("IOException must map to Offline", result is AdminApiResult.Offline)
    }

    // ── (9) revokeRole 204 → Ok ───────────────────────────────────────────────

    @Test
    fun `revokeRole 204 returns Ok`() = runBlocking {
        http.deleteQueue.add(204 to "")

        val result = repo.revokeRole("user-1", "role_admin")

        assertTrue("204 must return Ok", result is AdminApiResult.Ok)
    }

    // ── (10) revokeRole 404 userId not found → Failed ─────────────────────────

    @Test
    fun `revokeRole 404 userId not found returns Failed and does not crash`() = runBlocking {
        http.deleteQueue.add(404 to "")

        val result = repo.revokeRole("nonexistent-user", "role_admin")

        assertTrue("404 must map to Failed", result is AdminApiResult.Failed)
    }
}
