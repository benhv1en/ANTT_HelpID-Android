# Yêu cầu cụ thể để viết báo cáo đồ án An ninh thông tin

Tài liệu này thay thế bản nháp `yeu_cau_ve_bao_cao.txt`. Mục tiêu là làm rõ toàn bộ chỗ còn bỏ trống để ở prompt sau AI có thể viết báo cáo hoàn chỉnh cho môn học **An ninh thông tin** dựa trên repo HelpID.

## 1. Mục tiêu của prompt viết báo cáo sau

AI ở prompt sau phải viết báo cáo học thuật bằng tiếng Việt về đề tài:

**XÂY DỰNG CÁC TÍNH NĂNG BẢO MẬT CHO PHẦN MỀM QUẢN LÝ THÔNG TIN Y TẾ KHẨN CẤP TRÊN ĐIỆN THOẠI ANDROID**

Báo cáo phải tập trung vào các đóng góp bảo mật đã được triển khai trong repo:

1. Xác thực vân tay / biometric unlock cho ứng dụng Android.
2. Bảo vệ màn hình chống chụp màn hình, ghi màn hình và lộ thumbnail Recent Apps bằng `FLAG_SECURE` và `SecureScreenWrapper`.
3. Certificate Pinning / SPKI pinning để giảm rủi ro MITM khi Android giao tiếp backend.

Báo cáo vẫn cần giới thiệu bối cảnh hệ thống HelpID để người đọc hiểu vì sao các tính năng trên cần thiết:

- Android app `app/`: Kotlin, Jetpack Compose, Room, Firebase legacy, WorkManager, ZXing, Google Play Services Location, AndroidX Security Crypto.
- Backend `backend/HelpId.Api/`: ASP.NET Core, EF Core SQLite, JWT access token, refresh token rotation/revoke, RBAC, public emergency link.
- Web/API `helper-id/`: React/Vite, route public `/e/:publicKey`, Vercel serverless proxy `/api/profile`, API legacy `/api/mint`.

Không viết báo cáo như tài liệu marketing. Nội dung phải là báo cáo kỹ thuật bảo mật: có cơ sở lý thuyết, thiết kế, triển khai, kiểm thử, đánh giá, hạn chế và hướng phát triển.

## 2. Nguồn nội bộ phải dùng khi viết báo cáo

Trước khi viết báo cáo, AI phải đọc và đối chiếu lại các file sau để tránh bịa:

- `AGENTS.md`
- `README.md`
- `harness-engineering/tong-quan-kien-truc.md`
- `harness-engineering/android.md`
- `harness-engineering/bao-mat-du-lieu.md`
- `harness-engineering/thiet-ke-xac-thuc-van-tay.md`
- `harness-engineering/bao-mat-man-hinh.md`
- `harness-engineering/certificate-pinning.md`
- `harness-engineering/quy-trinh-test.md`
- `harness-engineering/uml-use-case-android.puml` và `.png`
- `harness-engineering/uml-use-case-website.puml` và `.png`
- `harness-engineering/uml-use-case-api.puml` và `.png`
- `harness-engineering/uml-database.puml` và `.png`
- `passed-testcases.md`
- `failed-testcases.md`
- `CHANGELOG.md`

Các file source chính cần dùng làm bằng chứng triển khai:

- `app/src/main/java/com/helpid/app/MainActivity.kt`
- `app/src/main/java/com/helpid/app/BiometricAuthDecision.kt`
- `app/src/main/java/com/helpid/app/utils/BiometricManager.kt`
- `app/src/main/java/com/helpid/app/data/BiometricPreferenceStore.kt`
- `app/src/main/java/com/helpid/app/ui/EditProfileScreen.kt`
- `app/src/main/java/com/helpid/app/ui/EmergencyScreen.kt`
- `app/src/main/java/com/helpid/app/ui/QRScreen.kt`
- `app/src/main/java/com/helpid/app/ui/components/SecureScreenWrapper.kt`
- `app/src/main/java/com/helpid/app/network/HelpIdHttpClient.kt`
- `app/src/main/java/com/helpid/app/network/CertPins.kt`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/debug/res/xml/network_security_config.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/helpid/app/BiometricAuthDecisionTest.kt`
- `app/src/test/java/com/helpid/app/data/BiometricPreferenceStoreTest.kt`
- `app/src/test/java/com/helpid/app/utils/BiometricUtilsTest.kt`
- `app/src/test/java/com/helpid/app/network/HelpIdHttpClientTest.kt`

Nếu báo cáo cần nói đến backend/web để giải thích luồng public emergency profile, đọc thêm:

- `backend/HelpId.Api/README.md`
- `backend/HelpId.Api/Program.cs`
- `backend/HelpId.Api/Auth/*`
- `backend/HelpId.Api/Profiles/*`
- `backend/HelpId.Api/EmergencyLinks/*`
- `helper-id/App.tsx`
- `helper-id/components/EmergencyProfilePage.tsx`
- `helper-id/api/profile.js`
- `helper-id/vercel.json`

## 3. Quy tắc nội dung bắt buộc

- Viết bằng tiếng Việt chuẩn học thuật, không dùng từ thô, không xưng hô kiểu hội thoại.
- Không bịa testcase. Nếu testcase thủ công chưa thực hiện thì ghi là "kịch bản đề xuất" hoặc "chưa thực hiện", không ghi PASS.
- Không đưa dữ liệu y tế thật, số điện thoại thật, vị trí thật, email thật, token, refresh token, Firebase ID token hoặc secret vào báo cáo.
- Không khẳng định biometric thay thế backend auth. Biometric chỉ là lớp khóa UI cục bộ cho session/token đã có.
- Không khẳng định `FLAG_SECURE` chống mọi hình thức rò rỉ. Nó không chặn camera vật lý chụp màn hình và không thay thế kiểm soát truy cập.
- Không khẳng định certificate pinning đang enforce giống nhau ở mọi build. Phải nói rõ:
  - `app/src/main/res/xml/network_security_config.xml` là cấu hình chính/release: `cleartextTrafficPermitted="false"`, pin-set cho `127.0.0.1`, 2 pin primary/backup.
  - `app/src/debug/res/xml/network_security_config.xml` là override debug cho phép cleartext và trust user/system để hỗ trợ backend local.
  - Test pinning đã dùng APK/cấu hình tạm bỏ debug-overrides để kích hoạt đường `SSLHandshakeException`.
- Khi nhắc tới "Expected result", dịch là **Kết quả mong đợi**.
- Khi nhắc tới "Actual result", dịch là **Kết quả thực tế**.
- Các kết luận bảo mật phải đi kèm giới hạn/hạn chế thực tế.

## 4. Cấu trúc báo cáo bắt buộc

Báo cáo cần theo hierarchy sau:

1. Trang bìa
2. Trang phụ bìa
3. Mục lục
4. Danh mục ký hiệu và viết tắt
5. Danh mục hình vẽ
6. Danh mục bảng
7. Lời cảm ơn
8. Lời nói đầu
9. Chương 1: Cơ sở lý thuyết về bảo mật cho phần mềm quản lý thông tin cá nhân trên điện thoại
10. Chương 2: Xây dựng các tính năng bảo mật cho phần mềm quản lý thông tin y tế khẩn cấp trên điện thoại Android
11. Chương 3: Kiểm thử và đánh giá
12. Kết luận
13. Tài liệu tham khảo
14. Phụ lục nếu cần

## 5. Trang bìa và trang phụ bìa

Phải dựa theo bố cục trong `trang_bia.png` và `trang_phu_bia.png`.

Các nội dung phải thay thế:

- Phần khoa: `KHOA CÔNG NGHỆ VÀ KỸ THUẬT TIÊN TIẾN`
- Họ và tên sinh viên:
  - `Nguyễn Phương Nhung - 23110208`
  - `Nguyễn Minh Đức - 22110117`
  - `Đào Phương Linh - 23110186`
- Tên đề tài: `XÂY DỰNG CÁC TÍNH NĂNG BẢO MẬT CHO PHẦN MỀM QUẢN LÝ THÔNG TIN Y TẾ KHẨN CẤP TRÊN ĐIỆN THOẠI ANDROID`
- Loại đồ án: `Đồ án cuối kỳ môn An ninh thông tin`
- Ngành: `Ngành Khoa học và Kỹ thuật máy tính`
- Địa điểm/năm: `Hà Nội - 2026`
- Cán bộ hướng dẫn ở trang phụ bìa: `Nguyễn Mạnh Thắng, Mai Đức Thọ`

Tên đề tài dài, cần xuống dòng cân đối, không để tràn lề.

## 6. Danh mục ký hiệu và viết tắt

Tối thiểu phải có các mục sau:

| Viết tắt | Nghĩa |
|---|---|
| API | Application Programming Interface |
| APK | Android Package |
| CA | Certificate Authority |
| EF Core | Entity Framework Core |
| JWT | JSON Web Token |
| MITM | Man-in-the-Middle |
| NFC | Near Field Communication |
| PII | Personally Identifiable Information |
| QR | Quick Response Code |
| RBAC | Role-Based Access Control |
| Room | Thư viện persistence của Android trên SQLite |
| SMS | Short Message Service |
| SPKI | Subject Public Key Info |
| TLS | Transport Layer Security |
| UI | User Interface |
| UX | User Experience |
| UML | Unified Modeling Language |

Có thể bổ sung `AES-GCM`, `PBKDF2`, `Vercel`, `Firestore`, `BiometricPrompt` nếu xuất hiện nhiều trong bài.

## 7. Chương 1: Cơ sở lý thuyết

Chương 1 không được để trống. Cần viết như nền tảng lý thuyết cho ba tính năng bảo mật đã làm. Gợi ý độ dài: 15-20 trang.

### 1.1. Đặc thù dữ liệu cá nhân và dữ liệu y tế khẩn cấp trên thiết bị di động

Nội dung cần có:

- Dữ liệu HelpID xử lý: tên, nhóm máu, địa chỉ, dị ứng, ghi chú y tế, liên hệ khẩn cấp, vị trí SOS, token đăng nhập, public profile link.
- Vì sao dữ liệu này nhạy cảm: liên quan sức khỏe, định danh cá nhân, số điện thoại, vị trí.
- Mâu thuẫn thiết kế: cần bảo mật nhưng vẫn phải truy cập nhanh trong tình huống khẩn cấp.
- Yêu cầu offline-first: hồ sơ local cache vẫn phải xem được khi mạng/backend lỗi.

### 1.2. Mô hình đe dọa đối với ứng dụng Android quản lý thông tin y tế

Nội dung cần có:

- Người cầm thiết bị đã mở khóa có thể xem app nếu token còn lưu.
- App độc hại có thể ghi màn hình hoặc chụp màn hình qua MediaProjection.
- Recent Apps thumbnail có thể lộ dữ liệu mà không cần mở app.
- Kẻ tấn công cùng mạng WiFi có thể MITM traffic nếu TLS bị hạ thấp hoặc người dùng cài CA giả.
- Rủi ro token bị lấy cắp: access token, refresh token, public profile JWT.
- Rủi ro không thuộc phạm vi: thiết bị root, malware quyền cao, camera vật lý chụp màn hình, người có toàn quyền thiết bị.

### 1.3. Xác thực, quản lý phiên và nguyên tắc không thay thế backend auth

Nội dung cần có:

- Backend HelpID dùng email/password, JWT access token 15 phút, refresh token opaque 30 ngày, rotate/revoke.
- Android lưu token bằng `AuthTokenStore` qua `SecurePrefs`.
- Refresh token bị revoke/expired phải thắng mọi trạng thái local.
- Biometric không ký JWT, không tạo token, không thay thế password/backend.

### 1.4. Sinh trắc học cục bộ trên Android

Nội dung cần có:

- `BiometricPrompt` là API hệ thống để xác thực local.
- App không nhận template vân tay và không được lưu biometric template.
- Các trạng thái cần xử lý: available, none enrolled, no hardware, hardware unavailable, lockout, user cancel, system error.
- Lợi ích: giảm rủi ro người khác mở app khi thiết bị đang có token local.
- Hạn chế: không chống root/hooking/malware quyền cao, không bảo vệ token nếu backend bị lộ ở nơi khác.

### 1.5. Bảo vệ màn hình dữ liệu nhạy cảm

Nội dung cần có:

- MediaProjection và screenshot/screen recording là kênh rò rỉ dữ liệu hiển thị.
- Recent Apps thumbnail là ảnh chụp trạng thái app có thể bị xem bởi người cầm máy.
- `FLAG_SECURE`: đánh dấu window là secure để hệ thống không cho capture surface.
- Lifecycle mask/overlay: che nội dung khi `ON_PAUSE` để giảm rủi ro thumbnail.
- Hạn chế: không chặn camera ngoài, không thay thế mã hóa dữ liệu, hành vi OEM có thể khác.

### 1.6. HTTPS, TLS và tấn công Man-in-the-Middle

Nội dung cần có:

- HTTPS/TLS bảo vệ tính bí mật và toàn vẹn khi truyền dữ liệu.
- MITM qua proxy như Charles/Burp/mitmproxy: nếu thiết bị tin CA giả, proxy có thể đọc plaintext trước khi forward.
- Dữ liệu nguy hiểm nếu bị đọc: password, access token, refresh token, profile y tế.
- Không fallback HTTP khi kết nối an toàn thất bại.

### 1.7. Certificate Pinning và SPKI pinning

Nội dung cần có:

- SPKI là phần public key trong certificate.
- Cơ chế: tính SHA-256 của DER-encoded SPKI, so sánh với pin đã khai báo.
- Ưu điểm so với pin toàn bộ certificate: cert có thể renew nếu key pair giữ nguyên.
- Cần backup pin để tránh tự khóa khi certificate rotation.
- Rủi ro vận hành: đổi cert/key mà không cập nhật pin sẽ làm app không kết nối được.

### 1.8. Bảo vệ dữ liệu local và public emergency profile

Nội dung cần có:

- Room/SQLite làm cache local offline-first.
- Dữ liệu nhạy cảm khi ghi Room được mã hóa field-level bằng `SensitiveDataCipher`/AndroidKeyStore, prefix `enc::`.
- Backend public profile chỉ trả whitelist field.
- Public profile link dùng `HID-*` public key và public JWT 3 giờ, route/API noindex/no-store.

### 1.9. Tiêu chí đánh giá tính năng bảo mật

Nội dung cần có:

- Đúng mục tiêu threat model.
- Không phá luồng khẩn cấp SOS/offline.
- Không log dữ liệu nhạy cảm.
- Có xử lý failure mode rõ ràng.
- Có unit test/build/lint/manual test hoặc ít nhất code review bảo mật.
- Có hạn chế và hướng phát triển.

## 8. Chương 2: Xây dựng các tính năng bảo mật

Chương 2 là phần chính. Gợi ý độ dài: 22-28 trang.

### 2.1. Tổng quan kiến trúc HelpID liên quan đến bảo mật

Nên mở Chương 2 bằng một mục tổng quan ngắn trước khi vào ba tính năng:

- Android app là bề mặt chính.
- Backend mới xử lý đăng ký/đăng nhập, session, profile private và public emergency link.
- Website public `/e/:publicKey` chỉ hiển thị whitelist profile qua Vercel proxy.
- Firebase Auth/Firestore legacy vẫn còn, chưa được gỡ.
- Room local là đường an toàn khi offline/token lỗi.

Nên dùng hình:

- `harness-engineering/uml-use-case-android.png`
- `harness-engineering/uml-use-case-api.png`
- `harness-engineering/uml-use-case-website.png`
- `harness-engineering/uml-database.png`

Nếu đưa cả 4 hình vào nội dung chính làm bài quá dài, đưa một phần vào phụ lục.

### 2.2. Tính năng xác thực vân tay

#### 2.2.1. Mục đích

Viết rõ:

- Bảo vệ truy cập lại vào dữ liệu private khi app còn token local.
- Giảm rủi ro người khác cầm điện thoại đã mở khóa và mở HelpID.
- Không làm gián đoạn SOS/offline emergency flow.
- Không thay thế backend auth.

#### 2.2.2. Threat model và phạm vi

Nêu:

- Giảm rủi ro người khác mở app từ recent apps hoặc sau khi token còn hạn.
- Không giải quyết token bị lộ ở nơi khác, thiết bị root/hook runtime, malware quyền cao, public emergency link đã chia sẻ hợp lệ.

#### 2.2.3. Thành phần triển khai

Phải mô tả các file/lớp:

- `BiometricUtils` trong `utils/BiometricManager.kt`:
  - `getAvailability()`
  - `showBiometricPrompt()`
  - `BiometricAvailability`
  - `BiometricPromptError`
  - mapping lỗi sang string resource.
- `BiometricPreferenceStore`:
  - file prefs `helpid_biometric_prefs`
  - key `biometric_enabled_user_{sha256(userId)}`
  - key `last_unlocked_at_epoch_ms_user_{sha256(userId)}`
  - không lưu template/token/email/tên/số điện thoại/dữ liệu y tế.
- `BiometricAuthDecision.resolveAuthState()`:
  - hàm thuần test được bằng JVM unit test.
  - biometric enabled + userId hợp lệ -> `AuthState.BiometricLocked`.
  - biometric disabled + access token còn hạn -> `Authenticated`.
  - biometric disabled + cần refresh -> `Unauthenticated`.
- `MainActivity.kt`:
  - `AuthState.BiometricLocked(userId, requiresRefresh)`.
  - `BiometricLockScreen`.
  - `refreshAfterBiometricUnlock()`.
  - `performLogout()` clear token và `biometricStore.clearForUser(userId)`.
- `EditProfileScreen.kt`:
  - `BiometricSettingsCard`.
  - toggle bật/tắt vân tay.
  - bật phải xác thực thành công trước khi lưu enabled.
  - tắt có dialog xác nhận.

#### 2.2.4. Quy trình bật/tắt xác thực vân tay

Trình bày dạng bước:

1. Người dùng đăng nhập backend thành công.
2. Vào màn hình chỉnh hồ sơ/profile.
3. Bật `Fingerprint unlock`.
4. App kiểm tra `BiometricAvailability`.
5. Nếu khả dụng, hiển thị `BiometricPrompt`.
6. Nếu prompt success, lưu enabled theo userId đã hash.
7. Nếu cancel/fail/không enroll, không bật và hiển thị lỗi.
8. Khi tắt, hỏi xác nhận rồi set enabled=false; không gọi backend, không xóa Room cache.

#### 2.2.5. Quy trình mở khóa app bằng vân tay

Trình bày dạng luồng:

1. App mở, `MainActivity` đọc `AuthTokenStore`.
2. Nếu token còn hạn và biometric enabled -> `BiometricLocked(requiresRefresh=false)`.
3. Biometric success -> vào `Authenticated` trực tiếp, không refresh thừa.
4. Nếu access token hết hạn nhưng refresh token còn -> `BiometricLocked(requiresRefresh=true)`.
5. Biometric success -> gọi refresh backend.
6. Refresh 200 -> lưu token mới -> `Authenticated`.
7. Refresh 400..403 -> clear token + clear biometric setting -> `LocalCacheOnly` nếu có Room cache, nếu không thì `Unauthenticated`.
8. Network error -> `LocalCacheOnly(isOffline=true)` nếu có cache.
9. Cancel/fail -> giữ màn hình khóa, có nút dùng mật khẩu.

#### 2.2.6. Bảo mật và quyền riêng tư

Nhấn mạnh:

- Không gửi vân tay, template, trạng thái enroll hoặc kết quả prompt lên backend.
- Không log userId, token, raw biometric error.
- Logout và refresh 401/403 clear biometric setting theo user.
- Biometric setting tách theo user để user A không ảnh hưởng user B.

#### 2.2.7. Hạn chế

Nêu:

- Cần thiết bị hỗ trợ biometric hoặc device credential.
- Không chống thiết bị bị root/hook.
- Không bảo vệ public link đã chia sẻ hợp lệ.
- Test thủ công biometric thực tế cần emulator/device có enrollment; nếu chưa có kết quả pass thì không ghi là pass.

### 2.3. Tính năng chống chụp màn hình và chống lộ Recent Apps thumbnail

#### 2.3.1. Mục đích

Viết rõ:

- Bảo vệ dữ liệu y tế đang hiển thị trên `EmergencyScreen`, `EditProfileScreen`, `QRScreen`.
- Giảm rủi ro screenshot, screen recording và Recent Apps thumbnail lộ tên, nhóm máu, địa chỉ, dị ứng, ghi chú y tế, liên hệ khẩn cấp, QR public link.

#### 2.3.2. Threat model

Nêu hai vector chính:

1. App khác trên cùng thiết bị dùng MediaProjection để capture màn hình.
2. Người cầm máy xem thumbnail trong Recent Apps mà không mở app.

#### 2.3.3. Triển khai `FLAG_SECURE`

Mô tả:

- Trong `MainActivity.onCreate()`, gọi:
  - `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)`
- Gọi ngay sau `super.onCreate(savedInstanceState)` và trước `setContent`.
- Áp dụng toàn Activity, không theo điều kiện màn hình.
- Mục tiêu: chặn hệ thống cung cấp buffer window cho capture/screen recording.

#### 2.3.4. Triển khai `SecureScreenWrapper`

Mô tả:

- File: `app/src/main/java/com/helpid/app/ui/components/SecureScreenWrapper.kt`
- Dùng `LocalLifecycleOwner.current.lifecycle`.
- Dùng `DisposableEffect(lifecycle)` và `LifecycleEventObserver`.
- Khi `ON_PAUSE`: `isBackground = true`, render overlay fullscreen màu nền.
- Khi `ON_RESUME`: `isBackground = false`, overlay biến mất.
- Không dùng animation/delay để tránh flash dữ liệu.

#### 2.3.5. Các màn hình được bọc

Nêu:

- `EmergencyScreen`: bọc skeleton, error state và main content.
- `EditProfileScreen`: bọc root content.
- `QRScreen`: bọc root content.

Giải thích vì sao không bọc riêng `LoginScreen`, `RegisterScreen`, `AdminScreen`, `LanguageSelectionScreen`, `BiometricLockScreen` bằng wrapper: các màn hình này không hiển thị hồ sơ y tế. Tuy nhiên do `FLAG_SECURE` set toàn Activity, trong kiểm thử thực tế LoginScreen cũng bị screenshot đen; đây là hành vi chấp nhận được.

#### 2.3.6. Hạn chế

Nêu:

- Không chặn camera vật lý chụp màn hình.
- Không thay thế mã hóa dữ liệu local.
- `FLAG_SECURE` có thể làm khó thao tác screenshot hợp lệ khi hỗ trợ người dùng.
- UIAutomator/accessibility tree không giống pixel capture; test dùng UI hierarchy chỉ để xác nhận màn hình đang ở đúng state.

### 2.4. Tính năng Certificate Pinning

#### 2.4.1. Mục đích

Viết rõ:

- Giảm rủi ro MITM khi Android gửi password, access token, refresh token và profile API qua mạng.
- Ngăn trường hợp thiết bị tin CA giả do người dùng/kẻ tấn công cài vào trust store.
- Không fallback HTTP khi TLS/pin thất bại.

#### 2.4.2. Threat model

Nêu kịch bản:

1. Kẻ tấn công cùng WiFi dùng Charles/Burp/mitmproxy.
2. Thiết bị Android tin CA giả.
3. Proxy ký certificate giả cho backend.
4. Không có pinning thì TLS handshake có thể thành công và proxy đọc payload.
5. Có SPKI pinning thì certificate/public key không khớp pin -> connection bị reject trước khi gửi dữ liệu.

#### 2.4.3. Cơ chế SPKI pinning trong dự án

Mô tả theo code hiện tại:

- Dự án không dùng OkHttp cho API backend hiện tại; production code dùng `HttpURLConnection`.
- Pinning được enforce bằng Android Network Security Config ở `app/src/main/res/xml/network_security_config.xml`.
- Cấu hình chính:
  - `base-config cleartextTrafficPermitted="false"`.
  - `domain-config` cho `127.0.0.1`.
  - `<pin-set expiration="2028-01-01">`.
  - Có primary pin và backup pin.
- `CertPins.kt` lưu các pin để tham chiếu và unit test; OS mới là tầng enforce chính.
- `AndroidManifest.xml` khai báo `android:networkSecurityConfig="@xml/network_security_config"` và `android:usesCleartextTraffic="false"`.

Không đưa private key, file `.pfx`, `.pem`, `.key` vào báo cáo.

#### 2.4.4. HTTPS backend dev và cấu hình debug/release

Mô tả:

- Backend dev có HTTPS trên `https://127.0.0.1:7080`.
- `HelpIdApiConfig.BASE_URL_HTTPS = "https://127.0.0.1:7080"`.
- `run-backend.sh` có hỗ trợ HTTP 5080 và HTTPS 7080/localtunnel theo bối cảnh test.
- `app/src/debug/res/xml/network_security_config.xml` override debug để cho phép cleartext local backend và trust user/system CA.
- Vì vậy phần đánh giá phải tách:
  - Thiết kế/release pinning theo main config.
  - Debug/dev convenience theo debug config.
  - Test pinning bằng APK/cấu hình tạm để kích hoạt `SSLHandshakeException` path.

#### 2.4.5. Centralized HTTP client và xử lý lỗi pinning

Mô tả:

- File `HelpIdHttpClient.kt`:
  - `openConnection(url, method)`
  - `connectTimeout = 15000ms`
  - `readTimeout = 30000ms`
  - `logPinFailure()` log warning an toàn, không log URL/header/body/token.
- 4 repository production dùng client này:
  - `HelpIdApiAuthRepository`
  - `HelpIdApiProfileRepository`
  - `HelpIdApiAdminRepository`
  - `HelpIdApiEmergencyLinkRepository`
- Các repository bắt `SSLHandshakeException` riêng trước `IOException`.
- UX:
  - Auth/logout trả `NetworkError` an toàn.
  - Profile load/sync phân biệt MITM/offline.
  - Admin API trả offline state.
  - Emergency link mint fail trả chuỗi rỗng, không render URL hỏng.
  - `EmergencyScreen` và `EditProfileScreen` có `mitmError` và string `error_mitm_detected`.

#### 2.4.6. Hạn chế

Nêu:

- Backup pin hiện là placeholder dev, cần thay bằng key/cert rotation thật trước production.
- Pinning sai có thể gây self-DoS khi cert rotate.
- Không pin Firebase SDK; Firebase tự quản lý TLS và can thiệp có thể phá SDK.
- Không pin Vercel nếu Android không gọi trực tiếp Vercel trong luồng mới.
- Debug config cho phép cleartext để thuận tiện local dev, không phải cấu hình production.

## 9. Chương 3: Kiểm thử và đánh giá

Chương 3 phải có bảng testcase. Mỗi testcase tối thiểu có các cột:

| Mã TC | Mục đích kiểm thử | Cách kiểm thử | Kết quả mong đợi | Kết quả thực tế | Kết luận |
|---|---|---|---|---|---|

Gợi ý độ dài: 10-14 trang.

### 3.1. Kiểm thử tính năng xác thực vân tay

Chỉ ghi PASS cho các kết quả đã có trong `passed-testcases.md`. Có thể chia thành "kiểm thử tự động" và "kịch bản thủ công đề xuất".

Các testcase tiêu biểu đã có thể đưa vào báo cáo:

| Mã TC | Mục đích kiểm thử | Cách kiểm thử | Kết quả mong đợi | Kết quả thực tế | Kết luận |
|---|---|---|---|---|---|
| BIO-01 | Kiểm tra logic quyết định auth state khi bật/tắt biometric | Chạy `BiometricAuthDecisionTest` qua `./gradlew :app:testDebugUnitTest` | Các nhánh enabled/disabled, token còn hạn/hết hạn, userId rỗng trả đúng `AuthState` | 11 tests, 0 failures/errors | PASS |
| BIO-02 | Kiểm tra lưu cấu hình biometric theo từng user | Chạy `BiometricPreferenceStoreTest` | Key dùng SHA-256, user A khác user B, enabled key khác last-unlocked key | 9 tests, 0 failures/errors | PASS |
| BIO-03 | Kiểm tra mapping trạng thái/lỗi biometric sang UI | Chạy `BiometricUtilsTest` | Các lỗi none enrolled, no hardware, lockout, canceled, unknown được map đúng string resource | 8 tests, 0 failures/errors | PASS |
| BIO-04 | Rà soát no-log và clear setting khi logout/refresh lỗi | Grep `Log.` trong file biometric, kiểm tra `performLogout` và `refreshAfterBiometricUnlock`, chạy build/test/lint | Không log PII/token; logout và refresh 401/403 clear biometric setting; 21 key biometric đồng bộ 6 locale | Grep sạch, build/test/lint pass, 59 unit tests 0 failures | PASS |

Nếu muốn trình bày kịch bản thủ công, ghi rõ là "đề xuất kiểm thử thủ công" nếu chưa có kết quả thực tế:

- Thiết bị không hỗ trợ biometric.
- Thiết bị có biometric nhưng chưa enroll.
- Bật biometric thành công.
- Cancel prompt khi bật.
- Mở app lại và unlock thành công.
- Token hết hạn, biometric success rồi refresh backend.
- Refresh bị revoke 401/403.
- Tắt biometric.
- Logout không tự mở user cũ.
- Đổi user không dùng setting biometric của user trước.

### 3.2. Kiểm thử chống chụp màn hình

Các testcase tiêu biểu đã có kết quả PASS trong `passed-testcases.md`:

| Mã TC | Mục đích kiểm thử | Cách kiểm thử | Kết quả mong đợi | Kết quả thực tế | Kết luận |
|---|---|---|---|---|---|
| SCR-01 | EmergencyScreen không lộ qua screenshot | Mở EmergencyScreen trên emulator, dùng `adb shell screencap` | Ảnh đen hoặc hệ thống không cho chụp | PNG toàn đen, không lộ dữ liệu y tế | PASS |
| SCR-02 | Recent Apps không lộ EmergencyScreen | Nhấn Overview/Recent Apps và chụp màn hình | Thumbnail không chứa hồ sơ y tế | HelpID card hiển thị nội dung đen | PASS |
| SCR-03 | EditProfileScreen không lộ qua screenshot/Recent Apps | Mở EditProfileScreen, chụp screenshot và Recent Apps | Không lộ form hồ sơ y tế | Screenshot/thumbnail đen | PASS |
| SCR-04 | QRScreen không lộ QR public profile link | Mở QRScreen, chụp screenshot và Recent Apps | Không lộ QR/link | Screenshot/thumbnail đen | PASS |
| SCR-05 | Navigation và SOS không bị wrapper làm crash | Điều hướng ID -> QR -> Profile nhiều vòng; thử gọi cấp cứu | Không crash, SOS/call intent vẫn hoạt động | Không crash; dialer được mở | PASS |
| SCR-06 | Hardening `FLAG_SECURE` và overlay | Grep `clearFlags|FLAG_SECURE`, grep animation/delay trong wrapper | Không có clear flag, không có animation/delay | Chỉ có set flag; wrapper không animation/delay; 3 màn hình đã bọc | PASS |

Nêu rõ: LoginScreen cũng bị screenshot đen do `FLAG_SECURE` toàn Activity, đây là ghi nhận thực tế và chấp nhận theo thiết kế.

### 3.3. Kiểm thử Certificate Pinning

Các testcase tiêu biểu:

| Mã TC | Mục đích kiểm thử | Cách kiểm thử | Kết quả mong đợi | Kết quả thực tế | Kết luận |
|---|---|---|---|---|---|
| PIN-01 | Kiểm tra cấu hình pin và timeout của HTTP client | Chạy `HelpIdHttpClientTest` | Hostname không rỗng, 2 pin hợp lệ, timeout đúng, log không chứa từ nhạy cảm | 11 tests PASS | PASS |
| PIN-02 | Kiểm tra build/lint sau khi centralize client và HTTPS | Chạy `assembleDebug`, `testDebugUnitTest`, `lintDebug`, hardening grep | Build/test/lint pass, không có trust-all/insecure code, `cleartextTrafficPermitted=false` ở main config | assemble/test/lint pass, grep sạch | PASS |
| PIN-03 | Mô phỏng MITM / SSL failure path | Build APK tạm không có `debug-overrides`, trigger request login | `SSLHandshakeException`/security log xuất hiện, app không crash, hiển thị lỗi thân thiện | Log `[SECURITY] SSL handshake failed...`, UI báo no connection, không crash | PASS |
| PIN-04 | Recovery sau khi restore debug-overrides/correct config | Restore debug-overrides và cài lại APK | App khởi động bình thường, không kẹt MITM error | App hoạt động bình thường | PASS |
| PIN-05 | Phân biệt offline và MITM | Code inspection và test offline path | Offline đi `IOException`/LocalCacheOnly, MITM đi `SSLHandshakeException`/security log | Code path tách riêng, không set nhầm MITM cho offline | PASS |
| PIN-06 | Log không lộ dữ liệu nhạy cảm | Trigger SSL failure rồi grep logcat | Log không chứa Authorization, Bearer, token, password, email, URL | Log chỉ có security message | PASS |
| PIN-07 | Hardening không fallback HTTP | Grep config/source | Không có HTTP fallback khi pin fail; có 2 pin; cleartext false ở main config | Hardening pass | PASS |

Phải ghi rõ hạn chế/ngoại lệ:

- TC SOS trong Prompt 55 phát hiện crash `IllegalArgumentException: Can only use lower 16 bits for requestCode` khi request permission. Đây là lỗi pre-existing của luồng SOS/Compose ActivityResult, không liên quan certificate pinning vì không có HTTPS call trước crash. Không được che giấu lỗi này.
- TC HTTPS bình thường có giới hạn emulator: dotnet dev cert không install được như user CA trên Android API 37 trong lần test đó, nên end-to-end pinning đầy đủ có giới hạn môi trường.

### 3.4. Đánh giá chung

Đánh giá theo tiêu chí:

- Đạt mục tiêu bảo mật nào.
- Không phá luồng khẩn cấp/offline nào.
- Test tự động và thủ công bao phủ tới đâu.
- Hạn chế còn tồn tại.
- Việc cần làm trước production:
  - Sửa bug SOS permission request code.
  - Thay backup pin placeholder bằng key/cert rotation thật.
  - Kiểm thử release build trên thiết bị thật với domain/certificate production.
  - Kiểm thử biometric thủ công trên thiết bị có fingerprint/PIN.
  - Rà soát Firebase legacy và kế hoạch migration nếu cần.

## 10. Kết luận

Kết luận cần tóm tắt:

- Nhóm đã xây dựng ba lớp bảo vệ bổ sung cho HelpID Android:
  - Local biometric gate.
  - Screen privacy layer.
  - Network transport hardening bằng certificate pinning.
- Các tính năng được thiết kế xoay quanh dữ liệu y tế khẩn cấp: bảo mật nhưng không phá khả năng truy cập khi cần cứu hộ.
- Hệ thống vẫn còn giới hạn: Firebase legacy chưa gỡ, debug config khác production, SOS permission bug, cần test release/production certificate.
- Hướng phát triển:
  - Hoàn thiện production pinning.
  - Test biometric và screenshot trên nhiều OEM.
  - Sửa SOS permission bug.
  - Hoàn thiện migration khỏi Firebase legacy nếu có kế hoạch.
  - Bổ sung security review cho public profile link, certificate rotation và release signing.

## 11. Tài liệu tham khảo

Phần tài liệu tham khảo phải theo IEEE. Khi viết báo cáo thật, AI nên tra cứu/đối chiếu nguồn chính thức trước khi trích dẫn, ưu tiên:

- Android Developers: Biometric authentication / `BiometricPrompt`.
- Android Developers: `WindowManager.LayoutParams.FLAG_SECURE`.
- Android Developers: Network Security Configuration và Certificate Pinning.
- Android Developers: Data and file storage / Room.
- OWASP MASVS hoặc OWASP Mobile Application Security Testing Guide cho mobile authentication, network communication, privacy và sensitive data.
- RFC 7519 cho JSON Web Token.
- RFC 8446 cho TLS 1.3.
- Microsoft ASP.NET Core documentation cho authentication/authorization/JWT nếu có nhắc backend.

Không dùng blog không rõ nguồn làm nguồn chính nếu đã có tài liệu chính thức.

## 12. Hình vẽ và bảng nên có

Hình vẽ nên có:

1. Kiến trúc tổng quan HelpID Android - Backend - Web.
2. UML use-case Android từ `harness-engineering/uml-use-case-android.png`.
3. UML use-case API từ `harness-engineering/uml-use-case-api.png`.
4. UML database từ `harness-engineering/uml-database.png`.
5. Sequence flow bật/tắt biometric.
6. Sequence flow mở app bằng biometric và refresh token.
7. Sơ đồ cơ chế `FLAG_SECURE` + lifecycle overlay.
8. Sơ đồ MITM và SPKI pinning.

Bảng nên có:

1. Danh mục ký hiệu và viết tắt.
2. Bảng dữ liệu nhạy cảm và biện pháp bảo vệ.
3. Bảng thành phần code của biometric.
4. Bảng thành phần code của chống chụp màn hình.
5. Bảng thành phần code của certificate pinning.
6. Bảng testcase biometric.
7. Bảng testcase chống chụp màn hình.
8. Bảng testcase certificate pinning.
9. Bảng hạn chế và hướng khắc phục.

## 13. Yêu cầu hình thức

- Số lượng trang: khoảng 50 đến 70 trang, tính cả tài liệu tham khảo và phụ lục.
- Font: Times New Roman.
- Cỡ chữ:
  - Heading/title: 14pt hoặc theo cấp heading phù hợp.
  - Nội dung đoạn văn: 13pt.
- Màu chữ: đen.
- Giãn dòng: 1.5 lines.
- Lề:
  - Trên: 2.5 cm.
  - Dưới: 3 cm.
  - Trái: 3.5 cm.
  - Phải: 2 cm.
- Khoảng cách đoạn: 6pt.
- Dòng đầu tiên của mỗi đoạn văn lùi vào 1.27 cm.
- Đánh số trang ở giữa phía dưới trang.
- Đánh số trang liên tục bắt đầu từ phần Mở đầu/Lời nói đầu đến hết Kết luận. Nếu công cụ tạo file cho phép, front matter trước đó có thể dùng số La Mã hoặc không đánh số theo chuẩn trường.
- Bảng:
  - Đánh số thứ tự liên tục.
  - Chú thích bảng đặt phía trên bảng.
  - Có trong Danh mục bảng.
- Hình:
  - Đánh số thứ tự liên tục.
  - Chú thích hình đặt phía dưới hình.
  - Có trong Danh mục hình vẽ.
- Citation:
  - Đánh số liên tục trong bài theo IEEE dạng `[1]`, `[2]`.
  - Mục Tài liệu tham khảo ghi theo đúng format IEEE.

## 14. Yêu cầu file đầu ra ở prompt sau

AI ở prompt sau phải tạo:

- File `.docx`.
- File `.odt`.

Nên tạo thêm một file nguồn `.md` hoặc `.docx` trung gian nếu cần để kiểm soát nội dung, nhưng đầu ra bắt buộc cuối cùng vẫn là `.docx` và `.odt`.

Tên file đề xuất:

- `bao-cao-an-ninh-thong-tin-helpid.docx`
- `bao-cao-an-ninh-thong-tin-helpid.odt`

Nếu công cụ chuyển đổi tài liệu thiếu dependency, AI phải báo rõ command đã thử và lý do không tạo được file, không được giả vờ đã tạo.

## 15. Checklist trước khi hoàn thành báo cáo

Trước khi trả lời hoàn thành ở prompt sau, AI phải tự kiểm:

- Báo cáo có đủ trang bìa và trang phụ bìa theo yêu cầu.
- Dàn ý không còn mục trống kiểu `1.1`, `2.1.2.1` không có nội dung.
- Chương 1 có đủ cơ sở lý thuyết cho biometric, screen protection và certificate pinning.
- Chương 2 có giải thích đúng code thực tế, không lẫn OkHttp nếu code hiện tại dùng `HttpURLConnection` + Network Security Config.
- Chương 3 có testcase với "Kết quả mong đợi" và "Kết quả thực tế".
- Các testcase PASS/FAIL khớp `passed-testcases.md` và `failed-testcases.md`.
- Có nêu hạn chế của từng tính năng.
- Không có dữ liệu nhạy cảm thật.
- Không có secret/token/public profile token.
- File `.docx` và `.odt` được tạo thật, mở được hoặc kiểm tra được bằng command phù hợp.
