package com.helpid.app.network

/**
 * SPKI SHA-256 pins cho backend HelpID API.
 *
 * Primary pin: dotnet dev-certs cert hiện tại (extract bằng openssl từ /tmp/devcert.pfx).
 * Backup pin: placeholder key — phải thay bằng cert rotation key thật trước production.
 *
 * Các pin này được enforce bởi Android OS qua network_security_config.xml.
 * File này chỉ là tham chiếu cho code (logging, unit test) — không cần đọc runtime
 * vì OS enforce độc lập.
 *
 * KHÔNG COMMIT file .pfx, .pem, .key, hay private key vào repo.
 */
object CertPins {
    const val BACKEND_HOSTNAME = "127.0.0.1"

    // SHA-256(DER(SubjectPublicKeyInfo)) của dotnet dev-certs cert
    const val BACKEND_PIN_PRIMARY = "Hp88H3igedctKspnX1r9lMTuRy8jT0maAtc9qAMQPyI="

    // Placeholder — phải cập nhật trước production với cert rotation key thật
    const val BACKEND_PIN_BACKUP = "I73HBnEVq8rxWRTENWE2CbiRa3+l000YYQJIdbnJaCQ="
}
