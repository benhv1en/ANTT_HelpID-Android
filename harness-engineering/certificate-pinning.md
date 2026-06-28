# Thiết kế Certificate Pinning — Chặn MITM Attack

Thời điểm lập tài liệu: 17/06/2026 04:10:00

---

## 1. Threat Model

### Vector tấn công: SSL Man-in-the-Middle qua proxy (Charles / Burp Suite / mitmproxy)

Kịch bản đầy đủ:

1. Kẻ tấn công và nạn nhân cùng mạng WiFi (quán cà phê, trường học, mạng công ty).
2. Kẻ tấn công cài root CA của Charles Proxy vào hệ thống Android nạn nhân (Settings → Security → Install CA certificate) — hoặc dùng social engineering, hoặc khai thác quyền admin thiết bị.
3. Kẻ tấn công bật proxy Charles/Burp và dùng ARP spoofing để route traffic của nạn nhân qua máy mình.
4. Android tin CA giả → TLS handshake với proxy thành công → proxy đọc được toàn bộ plaintext HTTP/1.1 payload trước khi forward lên backend thật.
5. Dữ liệu bị lộ trong một phiên login thông thường:
   - `POST /api/v1/auth/login` body: `{ "email": "...", "password": "..." }` — credential bị đọc trực tiếp.
   - Response: `{ "accessToken": "...", "refreshToken": "..." }` — toàn bộ session bị chiếm.
   - `GET /api/v1/profile` response: tên, nhóm máu, dị ứng, bệnh nền, liên hệ khẩn cấp.
   - Mọi API call tiếp theo: JWT token trong header `Authorization: Bearer ...` bị capture.
6. Kẻ tấn công dùng token đánh cắp để call API từ thiết bị khác — không cần biết password, không cần phá mã.

### Tại sao HTTPS không đủ nếu không có Certificate Pinning

HTTPS mặc định chỉ kiểm tra cert có được ký bởi CA nào đó trong trust store. Khi kẻ tấn công đã cài CA giả vào trust store → CA giả ký cert cho backend domain → Android tin cert giả → proxy thấy plaintext. HTTPS bị bypass hoàn toàn.

### Cách Certificate Pinning phá vỡ chuỗi tấn công

Certificate Pinning ghi nhớ "dấu vân tay" (SPKI hash) của public key backend thật. Khi TLS handshake, Android so sánh SPKI hash của cert server gửi lên với hash đã pin. Cert do CA giả ký có public key khác → hash không khớp → kết nối bị reject ngay tại TLS layer, trước khi bất kỳ byte payload nào được gửi đi.

---

## 2. Cơ chế SPKI Pinning

**SPKI = SubjectPublicKeyInfo** — phần DER-encoded public key trong X.509 certificate, không bao gồm signature hay metadata của CA.

```
SHA-256(DER(SubjectPublicKeyInfo)) → base64 → "sha256/AAAA...="
```

Ưu điểm so với pin toàn bộ cert:
- Cert có thể được renew (đổi validity period, đổi CA) nhưng **giữ nguyên key pair** → pin vẫn hoạt động mà không cần cập nhật app.
- Cho phép pin ở nhiều cấp trong chain (leaf cert, intermediate CA, root CA).

Android `network_security_config.xml` dùng cú pháp:
```xml
<pin digest="SHA-256">BASE64_SHA256_OF_SPKI_DER</pin>
```

---

## 3. Inventory HTTP Client trong Codebase

**Không có OkHttp** — `build.gradle.kts` không có dependency `com.squareup.okhttp3`. Toàn bộ codebase dùng `java.net.HttpURLConnection`.

| File | HTTP client | Abstraction | Cần đổi |
|---|---|---|---|
| `HelpIdApiAuthRepository.kt` | `HttpURLConnection` trực tiếp (hàm `openConnection()` private tại dòng 102) | Không có interface | Cần trích `openConnection()` ra shared utility |
| `HelpIdApiProfileRepository.kt` | `DefaultHttpClient` (dòng 295) implement `ProfileHttpClient` interface | Có — `ProfileHttpClient` | Cần cập nhật `DefaultHttpClient.open()` |
| `HelpIdApiAdminRepository.kt` | `DefaultAdminHttpClient` (dòng 264) implement `AdminHttpClient` interface | Có — `AdminHttpClient` | Cần cập nhật `DefaultAdminHttpClient.execute()` |
| `HelpIdApiEmergencyLinkRepository.kt` | `HttpURLConnection` trực tiếp tại hàm `callMintApi()` (dòng 64) | Không có interface | Cần cập nhật `callMintApi()` |

**Điểm chung của 4 client**: tất cả gọi `URL(...).openConnection() as HttpURLConnection`. Việc pin được enforce bởi OS thông qua `network_security_config.xml` — không cần sửa từng repository nếu dùng approach này.

---

## 4. Kế hoạch Centralize + Implement

### Approach đã chọn: Android Network Security Config XML (không cần OkHttp)

Vì toàn bộ codebase dùng `HttpURLConnection`, phương án tối ưu là dùng `res/xml/network_security_config.xml` với `<pin-set>`:

**Ưu điểm**:
- Enforce ở OS level — áp dụng cho **tất cả** HTTP client trong app (kể cả `HttpURLConnection`, Firebase SDK, và bất kỳ thư viện nào thêm vào sau).
- Không cần OkHttp dependency (tránh thêm ~3MB và tránh migrate 4 repository).
- Declarative — dễ audit, dễ review security.
- Android bắt lỗi pin trước khi TCP connection được hoàn thành.

**Cấu trúc file `network_security_config.xml`**:
```xml
<network-security-config>
    <!-- Chặn HTTP plaintext trong release build -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>

    <!-- Backend domain: pin SPKI SHA-256 -->
    <domain-config>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <pin-set expiration="2028-01-01">
            <pin digest="SHA-256">PRIMARY_PIN_BASE64</pin>
            <pin digest="SHA-256">BACKUP_PIN_BASE64</pin>
        </pin-set>
        <!-- Debug build: trust dotnet dev cert (self-signed) -->
        <debug-overrides>
            <trust-anchors>
                <certificates src="user"/>
                <certificates src="system"/>
            </trust-anchors>
        </debug-overrides>
    </domain-config>
</network-security-config>
```

### Centralize `HelpIdHttpClient.kt` (bổ sung, không bắt buộc cho pin)

Tạo `app/src/main/java/com/helpid/app/network/HelpIdHttpClient.kt` với `fun openConnection(url: URL, method: String, timeoutMs: Int): HttpURLConnection` — wrapper dùng chung cho 4 repository. Lợi ích:
- Bắt `SSLHandshakeException` (pin failure) tại một chỗ và log cảnh báo an toàn (không log URL, header, body).
- Cho phép UI phân biệt "pin failure" vs "network offline" để hiện error message khác nhau.
- Các `FakeHttpClient` / `FakeProfileHttpClient` trong unit test không đổi (không dùng `HelpIdHttpClient` thật).

---

## 5. Backup Pin Bắt Buộc

`<pin-set>` yêu cầu **ít nhất 2 pin** để tránh self-DoS khi cert rotation:

- **Primary pin**: SPKI SHA-256 của server cert hiện đang dùng.
- **Backup pin**: SPKI SHA-256 của cert kế tiếp đã được chuẩn bị, hoặc của CA intermediate ký cert đó.

Trong giai đoạn dev với self-signed cert từ `dotnet dev-certs`, không có intermediate CA. Backup pin tạm thời là SPKI hash của một key pair riêng được generate thủ công và ghi vào `CertPins.kt` với comment `// BACKUP — placeholder, phải cập nhật trước production`.

Nếu dùng localtunnel (`evil-paws-try.loca.lt`): primary pin là SPKI của Let's Encrypt E6 intermediate CA; backup là SPKI của Let's Encrypt R10 intermediate CA.

---

## 6. Backend HTTPS Dev Setup

### Tình trạng hiện tại

`run-backend.sh` chạy `dotnet run --urls "http://0.0.0.0:5080"` — HTTP only. Backend chỉ có HTTPS qua localtunnel (`https://evil-paws-try.loca.lt`) nhưng tunnel chạy riêng ngoài script.

### Lựa chọn 1 — dotnet dev-certs HTTPS (khuyên dùng cho demo pinning)

```bash
# Tạo và trust dev cert trên host
dotnet dev-certs https --trust

# Start backend với cả HTTP + HTTPS
dotnet run --project backend/HelpId.Api/HelpId.Api.csproj \
    --urls "http://localhost:5080;https://localhost:7080"

# Forward HTTPS port vào emulator
adb reverse tcp:7080 tcp:7080

# Android kết nối đến https://127.0.0.1:7080
```

- `appsettings.Development.json` cấu hình Kestrel lắng nghe cả 2 port.
- Android emulator dùng `https://127.0.0.1:7080` thay vì `http://10.0.2.2:5080`.
- Network Security Config `<domain-config>` cho hostname `127.0.0.1`.
- Debug override trust `user` certificates để Android tin dev cert (vì dev cert không được ký bởi CA trong system store của Android).

### Lựa chọn 2 — Localtunnel HTTPS (đơn giản hơn, pin CA intermediate)

Giữ localtunnel `evil-paws-try.loca.lt` đang dùng. Pin SPKI của Let's Encrypt intermediate CA. Nhược điểm: CA intermediate rotate theo lịch Let's Encrypt, cần cập nhật pin theo.

**Kế hoạch sử dụng: Lựa chọn 1** (cho thấy pinning cert backend thật, phù hợp hơn cho thuyết trình).

### Extract SPKI fingerprint

```bash
# Sau khi backend đang chạy HTTPS tại localhost:7080
openssl s_client -connect localhost:7080 -servername localhost 2>/dev/null </dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | base64
```

Nếu openssl s_client không lấy được cert (dev cert tự ký), dùng export path:
```bash
dotnet dev-certs https --export-path /tmp/devcert.pfx --password ""
openssl pkcs12 -in /tmp/devcert.pfx -nokeys -passin pass: \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | base64
```

Fingerprint đã được extract và lưu vào `app/src/main/java/com/helpid/app/network/CertPins.kt`:

```
Primary pin (dotnet dev-certs, extracted 17/06/2026):
Hp88H3igedctKspnX1r9lMTuRy8jT0maAtc9qAMQPyI=

Backup pin (placeholder key — phải thay trước production):
I73HBnEVq8rxWRTENWE2CbiRa3+l000YYQJIdbnJaCQ=
```

---

## 7. Network Security Config Chi Tiết

File: `app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!--
        base-config: áp dụng cho tất cả domain không có domain-config riêng.
        cleartextTrafficPermitted="false" chặn toàn bộ HTTP plaintext trong
        release build. Debug build cho phép plaintext qua debug-overrides.
    -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>

    <!--
        domain-config cho backend dev (127.0.0.1 qua adb reverse tcp:7080 tcp:7080).
        Production sẽ thay hostname thật và pin của cert production.
    -->
    <domain-config>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <pin-set expiration="2028-01-01">
            <!-- Primary: SPKI SHA-256 của dotnet dev-certs cert (điền sau khi extract) -->
            <pin digest="SHA-256">REPLACE_WITH_PRIMARY_PIN</pin>
            <!-- Backup: placeholder, phải thay trước production -->
            <pin digest="SHA-256">REPLACE_WITH_BACKUP_PIN</pin>
        </pin-set>
        <!--
            debug-overrides: chỉ áp dụng trong debug build (buildType = debug).
            Trust "user" CA để Android chấp nhận dotnet dev cert tự ký.
            Không áp dụng trong release build.
        -->
        <debug-overrides>
            <trust-anchors>
                <certificates src="user"/>
                <certificates src="system"/>
            </trust-anchors>
        </debug-overrides>
    </domain-config>
</network-security-config>
```

Khai báo trong `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

**Khi pin fail**: Android ném `javax.net.ssl.SSLHandshakeException: java.security.cert.CertPathValidatorException: Pin verification failed` — catch tại tầng repository, log cảnh báo không chứa URL/token/header, hiện error message thân thiện.

---

## 8. Pin Failure UX

| Scenario | Behavior | Error message |
|---|---|---|
| `SSLHandshakeException` (pin mismatch) | Không crash, không retry tự động | "Không thể kết nối an toàn với máy chủ. Vui lòng kiểm tra mạng." |
| `IOException` (offline / timeout) | Không crash, dùng Room cache | "Không có kết nối mạng." |
| Cả hai | Phân biệt trong catch block | Message khác nhau |

Không fallback về HTTP khi pin fail — HTTP fallback tạo false sense of security và cho phép proxy đọc lại.

Log khi pin fail:
```kotlin
Log.w("HelpID", "[SECURITY] SSL handshake failed — possible MITM or cert change")
// KHÔNG log: URL, headers, Authorization, token, body, email
```

---

## 9. Không Làm

- **Không pin Firebase SDK**: Firebase SDK tự quản lý trust store và đã có pinning nội bộ. Can thiệp vào Firebase TLS có thể phá offline cache và FCM.
- **Không pin Vercel `helper-id/`**: Không có Android→Vercel call trực tiếp; app chỉ gọi backend và Firebase.
- **Không implement dynamic pin update**: Pin cứng trong `network_security_config.xml` — nếu cần đổi pin, publish app update. Không fetch pin từ remote (pin server có thể bị MITM chính nó nếu chưa pin).
- **Không thêm OkHttp**: Không có OkHttp trong codebase hiện tại, không cần thêm chỉ để pin.
- **Không cập nhật UML**: Certificate Pinning không thêm actor hay luồng user mới.

---

## 10. Kế Hoạch Implement (tham khảo Prompts 53–55)

| Bước | Prompt | Nội dung |
|---|---|---|
| 1 | 53 | Bật HTTPS Kestrel + export dev cert + extract SPKI pin + tạo `CertPins.kt` + tạo `network_security_config.xml` + update `AndroidManifest.xml` |
| 2 | 54 | Tạo `HelpIdHttpClient.kt` centralize openConnection + update 4 repository để bắt `SSLHandshakeException` riêng + switch sang HTTPS URL + thêm string resource + hardening grep |
| 3 | 55 | Unit test + test thủ công MITM demo (mitmproxy) + ghi kết quả |
