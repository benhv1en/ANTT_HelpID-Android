package com.helpid.app.network

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

/**
 * Unit tests cho [HelpIdHttpClient] và [CertPins].
 *
 * Lưu ý về thiết kế:
 * - Project dùng `java.net.HttpURLConnection`, không phải OkHttp.
 *   Pin enforcement thực hiện ở OS level qua `network_security_config.xml`
 *   (`<pin-set>` với SPKI SHA-256) — không cần `CertificatePinner` của OkHttp.
 * - [HelpIdHttpClient.logPinFailure] dùng `android.util.Log.w` — stub bởi
 *   `testOptions.unitTests.isReturnDefaultValues = true` trong build.gradle.kts.
 * - TC-MITM thực (proxy chặn, SSLHandshakeException) được kiểm tra qua test
 *   thủ công trên emulator với APK có pin sai (xem passed-testcases.md PROMPT 55).
 */
class HelpIdHttpClientTest {

    // ── TC-1: CertPins constants ──────────────────────────────────────────────

    @Test
    fun `BACKEND_HOSTNAME is not blank`() {
        assertTrue(
            "BACKEND_HOSTNAME must not be blank",
            CertPins.BACKEND_HOSTNAME.isNotBlank()
        )
    }

    @Test
    fun `primary and backup pins are not blank`() {
        assertTrue(
            "BACKEND_PIN_PRIMARY must not be blank",
            CertPins.BACKEND_PIN_PRIMARY.isNotBlank()
        )
        assertTrue(
            "BACKEND_PIN_BACKUP must not be blank",
            CertPins.BACKEND_PIN_BACKUP.isNotBlank()
        )
    }

    @Test
    fun `primary and backup pins are distinct`() {
        assertNotEquals(
            "Primary and backup pins must be different (two distinct key pairs)",
            CertPins.BACKEND_PIN_PRIMARY,
            CertPins.BACKEND_PIN_BACKUP
        )
    }

    @Test
    fun `pins look like base64-encoded SHA-256 — 44 chars each`() {
        assertEquals(
            "Primary pin must be 44 chars (base64 of 32-byte SHA-256)",
            44, CertPins.BACKEND_PIN_PRIMARY.length
        )
        assertEquals(
            "Backup pin must be 44 chars (base64 of 32-byte SHA-256)",
            44, CertPins.BACKEND_PIN_BACKUP.length
        )
    }

    @Test
    fun `pins end with = padding (valid base64 pattern)`() {
        assertTrue(
            "Primary pin must end with '='",
            CertPins.BACKEND_PIN_PRIMARY.endsWith("=")
        )
        assertTrue(
            "Backup pin must end with '='",
            CertPins.BACKEND_PIN_BACKUP.endsWith("=")
        )
    }

    // ── TC-2: HelpIdHttpClient timeouts ───────────────────────────────────────

    @Test
    fun `openConnection sets connectTimeout to 15000ms`() {
        // URL.openConnection() không thực sự kết nối — chỉ tạo object connection
        val conn = HelpIdHttpClient.openConnection(URL("http://localhost:12345/test"), "GET")
        assertEquals(
            "connectTimeout must be 15s (15000ms)",
            15_000, conn.connectTimeout
        )
    }

    @Test
    fun `openConnection sets readTimeout to 30000ms`() {
        val conn = HelpIdHttpClient.openConnection(URL("http://localhost:12345/test"), "GET")
        assertEquals(
            "readTimeout must be 30s (30000ms)",
            30_000, conn.readTimeout
        )
    }

    @Test
    fun `openConnection sets requestMethod correctly`() {
        val connGet = HelpIdHttpClient.openConnection(URL("http://localhost:12345/test"), "GET")
        assertEquals("GET", connGet.requestMethod)

        val connPost = HelpIdHttpClient.openConnection(URL("http://localhost:12345/test"), "POST")
        assertEquals("POST", connPost.requestMethod)
    }

    // ── TC-3: logPinFailure security message ─────────────────────────────────

    @Test
    fun `SECURITY_LOG_MESSAGE does not contain sensitive keywords`() {
        val message = HelpIdHttpClient.SECURITY_LOG_MESSAGE
        val forbidden = listOf("token", "Bearer", "Authorization", "password", "email", "url", "http")
        for (word in forbidden) {
            assertFalse(
                "Security log message must NOT contain '$word' — would leak sensitive data",
                message.contains(word, ignoreCase = true)
            )
        }
    }

    @Test
    fun `logPinFailure does not throw — android Log stubbed by isReturnDefaultValues`() {
        // android.util.Log.w returns 0 (default) because isReturnDefaultValues = true
        // Nếu test này fail với "RuntimeException: not mocked", cần kiểm tra
        // testOptions.unitTests.isReturnDefaultValues = true trong build.gradle.kts
        HelpIdHttpClient.logPinFailure()
        // No assertion needed — verifies it doesn't throw
    }

    @Test
    fun `SECURITY_LOG_MESSAGE contains SECURITY keyword to aid log filtering`() {
        assertTrue(
            "Message should contain [SECURITY] prefix for easy logcat filtering",
            HelpIdHttpClient.SECURITY_LOG_MESSAGE.contains("[SECURITY]")
        )
    }
}
