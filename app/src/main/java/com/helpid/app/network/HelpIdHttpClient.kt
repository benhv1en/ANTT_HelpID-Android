package com.helpid.app.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Centralized HTTP connection factory cho backend HelpID API.
 *
 * Pin enforcement được thực hiện ở OS level qua network_security_config.xml:
 * - Khi SPKI hash của cert server không khớp với CertPins.BACKEND_PIN_*,
 *   Android ném SSLHandshakeException trước khi bất kỳ byte payload nào được gửi.
 * - Gọi [logPinFailure] trong catch block của từng repository để log cảnh báo
 *   mà không lộ URL, header, hay token.
 *
 * Timeout: connect 15s, read 30s (ghi đè timeout cũ 8s của từng repository).
 */
object HelpIdHttpClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    // Exposed internal for unit tests to verify message contains no sensitive keywords
    internal const val SECURITY_LOG_MESSAGE =
        "[SECURITY] SSL handshake failed — possible MITM or cert change"

    fun openConnection(url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    fun logPinFailure() {
        // KHÔNG log URL, header Authorization, body, hay token
        Log.w("HelpID", SECURITY_LOG_MESSAGE)
    }
}
