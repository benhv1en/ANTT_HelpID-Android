package com.helpid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HelpIdApiAuthRepositoryTest {

    // ── parseTokenResponse ────────────────────────────────────────────────────

    @Test
    fun `parseTokenResponse 200 success parses tokens and userId`() {
        val json = """
            {
              "accessToken": "access123",
              "accessTokenExpiresAtUtc": "2026-06-14T10:15:00Z",
              "refreshToken": "refresh456",
              "refreshTokenExpiresAtUtc": "2026-07-14T10:00:00Z",
              "user": { "id": "usr_abc", "email": "a@b.com", "roles": ["User"] }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(200, json)

        assertTrue(result is AuthResult.Success)
        result as AuthResult.Success
        assertEquals("usr_abc", result.userId)
        assertEquals("access123", result.accessToken)
        assertEquals("refresh456", result.refreshToken)
        assertTrue("accessTokenExpiresAtEpochMs should be in the future",
            result.accessTokenExpiresAtEpochMs > 0)
    }

    @Test
    fun `parseTokenResponse 201 created also parses successfully`() {
        val json = """
            {
              "accessToken": "at",
              "accessTokenExpiresAtUtc": "2026-06-14T10:15:00+00:00",
              "refreshToken": "rt",
              "refreshTokenExpiresAtUtc": "2026-07-14T10:00:00+00:00",
              "user": { "id": "usr_xyz" }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(201, json)
        assertTrue(result is AuthResult.Success)
        assertEquals("usr_xyz", (result as AuthResult.Success).userId)
    }

    @Test
    fun `parseTokenResponse 200 missing accessToken returns ApiError`() {
        val json = """{ "refreshToken": "rt", "user": { "id": "u1" } }"""
        val result = HelpIdApiAuthRepository.parseTokenResponse(200, json)
        assertTrue(result is AuthResult.ApiError)
        assertEquals(200, (result as AuthResult.ApiError).httpStatus)
    }

    @Test
    fun `parseTokenResponse 401 returns ApiError with title`() {
        val json = """{ "title": "Email or password is invalid.", "status": 401 }"""
        val result = HelpIdApiAuthRepository.parseTokenResponse(401, json)
        assertTrue(result is AuthResult.ApiError)
        result as AuthResult.ApiError
        assertEquals(401, result.httpStatus)
        assertEquals("Email or password is invalid.", result.errorCode)
    }

    @Test
    fun `parseTokenResponse 409 duplicate email returns ApiError`() {
        val json = """{ "title": "Email is already registered.", "status": 409 }"""
        val result = HelpIdApiAuthRepository.parseTokenResponse(409, json)
        assertTrue(result is AuthResult.ApiError)
        assertEquals(409, (result as AuthResult.ApiError).httpStatus)
    }

    @Test
    fun `parseTokenResponse 423 locked returns ApiError`() {
        val json = """{ "title": "Account is temporarily locked.", "status": 423 }"""
        val result = HelpIdApiAuthRepository.parseTokenResponse(423, json)
        assertTrue(result is AuthResult.ApiError)
        assertEquals(423, (result as AuthResult.ApiError).httpStatus)
    }

    @Test
    fun `parseTokenResponse 422 validation email required`() {
        val json = """
            {
              "title": "One or more validation errors occurred.",
              "status": 422,
              "errors": {
                "email": ["Email is required."]
              }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(422, json)
        assertTrue(result is AuthResult.ValidationError)
        val ve = result as AuthResult.ValidationError
        assertEquals("email.required", ve.fieldErrors["email"])
    }

    @Test
    fun `parseTokenResponse 422 validation password too short`() {
        val json = """
            {
              "status": 422,
              "errors": {
                "password": ["Password must be at least 12 characters."]
              }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(422, json)
        assertTrue(result is AuthResult.ValidationError)
        assertEquals("password.too_short", (result as AuthResult.ValidationError).fieldErrors["password"])
    }

    @Test
    fun `parseTokenResponse 422 validation email invalid format`() {
        val json = """
            {
              "status": 422,
              "errors": { "email": ["Email format is invalid."] }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(422, json)
        assertTrue(result is AuthResult.ValidationError)
        assertEquals("email.invalid", (result as AuthResult.ValidationError).fieldErrors["email"])
    }

    @Test
    fun `parseTokenResponse 422 DisplayName PascalCase key is normalised to lowercase`() {
        val json = """
            {
              "status": 422,
              "errors": { "DisplayName": ["DisplayName must be 80 characters or fewer."] }
            }
        """.trimIndent()

        val result = HelpIdApiAuthRepository.parseTokenResponse(422, json)
        assertTrue(result is AuthResult.ValidationError)
        val ve = result as AuthResult.ValidationError
        // Key must be lower-cased so UI code can use "displayname"
        assertNotNull("expected displayname key", ve.fieldErrors["displayname"])
        assertEquals("display_name.too_long", ve.fieldErrors["displayname"])
    }

    @Test
    fun `parseTokenResponse malformed JSON returns ApiError without crash`() {
        val result = HelpIdApiAuthRepository.parseTokenResponse(200, "not json {{{")
        assertTrue(result is AuthResult.ApiError)
    }

    @Test
    fun `parseTokenResponse empty body returns ApiError without crash`() {
        val result = HelpIdApiAuthRepository.parseTokenResponse(200, "")
        assertTrue(result is AuthResult.ApiError)
    }

    @Test
    fun `parseTokenResponse 422 no errors field returns ApiError`() {
        val json = """{ "title": "Some other 422", "status": 422 }"""
        val result = HelpIdApiAuthRepository.parseTokenResponse(422, json)
        assertTrue(result is AuthResult.ApiError)
        assertEquals(422, (result as AuthResult.ApiError).httpStatus)
    }

    // ── parseIso8601ToEpochMs ─────────────────────────────────────────────────

    private fun utcEpochMs(year: Int, month: Int, day: Int, h: Int, m: Int, s: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, h, m, s)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `parseIso8601ToEpochMs parses Z suffix correctly`() {
        val expected = utcEpochMs(2026, 6, 14, 3, 15, 0)
        val actual = HelpIdApiAuthRepository.parseIso8601ToEpochMs("2026-06-14T03:15:00Z", 0)
        assertEquals(expected, actual)
    }

    @Test
    fun `parseIso8601ToEpochMs parses +00 colon offset`() {
        val expected = utcEpochMs(2026, 6, 14, 3, 15, 0)
        val actual = HelpIdApiAuthRepository.parseIso8601ToEpochMs("2026-06-14T03:15:00+00:00", 0)
        assertEquals(expected, actual)
    }

    @Test
    fun `parseIso8601ToEpochMs strips fractional seconds`() {
        val expected = utcEpochMs(2026, 6, 14, 3, 15, 0)
        val actual = HelpIdApiAuthRepository.parseIso8601ToEpochMs("2026-06-14T03:15:00.123Z", 0)
        assertEquals(expected, actual)
    }

    @Test
    fun `parseIso8601ToEpochMs null returns now plus fallback`() {
        val before = System.currentTimeMillis()
        val fallback = 60_000L
        val result = HelpIdApiAuthRepository.parseIso8601ToEpochMs(null, fallback)
        assertTrue(result >= before + fallback)
    }

    @Test
    fun `parseIso8601ToEpochMs blank returns now plus fallback`() {
        val fallback = 60_000L
        val before = System.currentTimeMillis()
        val result = HelpIdApiAuthRepository.parseIso8601ToEpochMs("  ", fallback)
        assertTrue(result >= before + fallback)
    }

    @Test
    fun `parseIso8601ToEpochMs garbage string returns now plus fallback`() {
        val fallback = 60_000L
        val before = System.currentTimeMillis()
        val result = HelpIdApiAuthRepository.parseIso8601ToEpochMs("not-a-date", fallback)
        assertTrue(result >= before + fallback)
    }

    // ── detectFieldCode ───────────────────────────────────────────────────────

    @Test
    fun `detectFieldCode email required`() {
        assertEquals("email.required",
            HelpIdApiAuthRepository.detectFieldCode("email", "Email is required."))
    }

    @Test
    fun `detectFieldCode email format invalid`() {
        assertEquals("email.invalid",
            HelpIdApiAuthRepository.detectFieldCode("email", "Email format is invalid."))
    }

    @Test
    fun `detectFieldCode email too long 254`() {
        assertEquals("email.too_long",
            HelpIdApiAuthRepository.detectFieldCode("email", "Email must be 254 characters or fewer."))
    }

    @Test
    fun `detectFieldCode password required`() {
        assertEquals("password.required",
            HelpIdApiAuthRepository.detectFieldCode("password", "Password is required."))
    }

    @Test
    fun `detectFieldCode password too short at least`() {
        assertEquals("password.too_short",
            HelpIdApiAuthRepository.detectFieldCode("password", "Password must be at least 12 characters."))
    }

    @Test
    fun `detectFieldCode password too long 128`() {
        assertEquals("password.too_long",
            HelpIdApiAuthRepository.detectFieldCode("password", "Password must be 128 characters or fewer."))
    }

    @Test
    fun `detectFieldCode displayname 80`() {
        assertEquals("display_name.too_long",
            HelpIdApiAuthRepository.detectFieldCode("displayname", "DisplayName must be 80 characters or fewer."))
    }

    @Test
    fun `detectFieldCode refreshtoken required`() {
        assertEquals("refresh_token.required",
            HelpIdApiAuthRepository.detectFieldCode("refreshtoken", "Refresh token is required."))
    }

    @Test
    fun `detectFieldCode unknown field returns server_error`() {
        assertEquals("server_error",
            HelpIdApiAuthRepository.detectFieldCode("devicename", "DeviceName too long."))
    }
}
