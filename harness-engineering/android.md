# Ghi chú Android

## Cấu hình build

- Namespace/application id: `com.helpid.app`.
- `compileSdk = 34`, `minSdk = 24`, `targetSdk = 34`.
- Compose compiler extension: `1.5.1`.
- Kotlin: `1.9.0`.
- AGP: `8.5.2`.
- Release bật minify và shrink resources.

Dependency đáng chú ý:

- Firebase BOM `32.7.0`, Auth KTX, Firestore KTX.
- Room `2.6.1` với KSP.
- WorkManager `2.9.1`.
- AndroidX Security Crypto `1.1.0-alpha06`.
- ZXing core `3.5.1`.
- libphonenumber `8.13.27`.
- iTextG `5.5.10`.

## Auth backend mới

Android đã có luồng Login/Register dùng backend `backend/HelpId.Api` thay cho Firebase anonymous ở bề mặt auth user-facing mới.

Thành phần cần chú ý:

- `MainActivity.kt`: auth state, Login/Register, route sau đăng nhập.
- `data/AuthTokenStore.kt`: lưu access token, refresh token, user id, expiry bằng secure prefs wrapper.
- `data/HelpIdApiAuthRepository.kt`: gọi `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/me`.
- `data/HelpIdApiEmergencyLinkRepository.kt`: mint QR/NFC/SOS fallback qua `/api/v1/emergency-links/mint`.

Quy tắc:

- Khi access token hết hạn, refresh và retry private request đúng một lần.
- Logout xóa token local nhưng không tự xóa Room profile nếu chưa có xác nhận riêng.
- Không log email/password/token hoặc response body có profile.
- Nếu backend/offline lỗi, EmergencyScreen vẫn ưu tiên Room cache đã có.

## Navigation và state

Repo chưa dùng Navigation Compose. `MainActivity.AppNavigation` giữ `currentScreen` bằng `remember { mutableStateOf(initialScreen) }`.

Các route hiện có:

- `login`
- `register`
- `emergency`
- `qr`
- `edit`
- `language`

Nếu thêm màn hình mới, cân nhắc giữ pattern hiện tại để thay đổi hẹp. Chỉ thêm Navigation Compose khi có nhu cầu thật và cập nhật test/navigation toàn app.

## Dữ liệu profile

Domain model:

- `UserProfile`
- `EmergencyContactData`

Local Room model:

- `LocalUserProfile`
- `LocalEmergencyContact`

Firestore field whitelist trong app:

- `userId`
- `name`
- `bloodGroup`
- `address`
- `allergies`
- `medicalNotes`
- `emergencyContacts`
- `language`
- `lastUpdated`

Không tự thêm field vào Firestore mà không cập nhật:

- `UserProfile.toMap()`
- `FirebaseRepository.fetchRemoteProfile()`
- Room entity/converters nếu cần local.
- Web `/api/profile` sanitize nếu field public được phép lộ.

## Local encryption

`FirebaseRepository` mã hóa các field nhạy cảm khi ghi Room qua `SensitiveDataCipher`.

Cơ chế:

- AES/GCM/NoPadding.
- Key trong AndroidKeyStore alias `helpid_sensitive_aes`.
- Payload lưu prefix `enc::`.
- Nếu decrypt lỗi field mã hóa trả rỗng.

`SecurePrefs.create()` dùng EncryptedSharedPreferences, fallback về SharedPreferences thường nếu khởi tạo lỗi. Vì có fallback này, không coi secure prefs là bảo mật tuyệt đối trong mọi môi trường.

## Room

Database:

- Tên DB: `helpid_database`.
- Version hiện tại: `2`.
- Migration `1 -> 2` thêm `address` và `allergies`.

Quy tắc:

- Mọi thay đổi entity phải tăng version.
- Migration phải bảo toàn dữ liệu.
- Không thêm `fallbackToDestructiveMigration()` cho app này.

## SOS

`EmergencyScreen` xử lý:

- Request quyền `SEND_SMS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS` trên Android 13+.
- Countdown 5 giây trước khi gửi SOS.
- SMS tới emergency contacts.
- Lấy location trong timeout 3,5 giây.
- Retry SMS từng contact tối đa 2 lần.
- Nếu có contact fail, cố mint profile link để fallback share.
- Nếu gửi ít nhất một SMS thành công, schedule `SosFollowUpWorker`.
- Auto dial số khẩn cấp sau countdown 30 giây, có nút cancel.

Điểm cần giữ:

- Không gửi SOS thật trong test tự động.
- Không log số điện thoại hoặc location.
- Luôn xử lý thiếu quyền không crash.
- Nếu profile vẫn là default demo, không gửi SOS thật.

## QR và NFC

`QRScreen`:

- Mint link qua repository; luồng mới dùng backend `POST /api/v1/emergency-links/mint`, legacy có thể còn đi qua Firebase/Vercel trong code cũ.
- Tạo bitmap QR bằng `generateQRCode`.
- Nếu mint fail, không render URL hỏng.
- NFC beam dùng API phản chiếu `setNdefPushMessage`; đây là Android Beam cũ và không đảm bảo hoạt động trên mọi thiết bị.

`EmergencyScreen` cũng có biến NFC share nhưng UI hiện không hiển thị nút NFC ở màn hình này. Khi sửa NFC, kiểm tra cả hai màn hình để tránh logic chết.

## Ngôn ngữ

Locale hỗ trợ:

- `en`
- `es`
- `hi`
- `fr`
- `de`

`LanguageManager` lưu vào SharedPreferences `app_settings`, key `selected_language`. Repository có migration một số key legacy sang secure prefs, nhưng language vẫn dùng `app_settings`.

Khi thêm string:

1. Thêm vào `values/strings.xml`.
2. Thêm bản dịch hoặc fallback rõ ràng vào `values-es`, `values-fr`, `values-de`, `values-hi`.
3. Chạy Android build.

## Hard-code cần chú ý

Các màn hình còn nhiều text hard-code tiếng Anh như cancel SOS, auto-call, fallback share, validation lỗi, CTA. Khi sửa khu vực này, ưu tiên chuyển text sang resource.
