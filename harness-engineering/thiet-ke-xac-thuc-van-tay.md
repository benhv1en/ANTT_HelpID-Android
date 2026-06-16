# Thiết kế xác thực vân tay cho HelpID Android

Thời điểm thiết kế: 16/06/2026 10:56:09

## Bối cảnh hiện tại

Android đã có đăng ký/đăng nhập backend, lưu access token/refresh token bằng `AuthTokenStore` qua `SecurePrefs`, khôi phục session trong `MainActivity`, và refresh token khi access token hết hạn. `BiometricUtils` đã tồn tại nhưng mới chỉ kiểm tra `BIOMETRIC_STRONG` và hiển thị `BiometricPrompt`; chưa được nối vào auth state, chưa có setting bật/tắt theo user, và chưa có luồng mở app bằng biometric.

Tính năng xác thực vân tay phải là lớp mở khóa cục bộ cho app/session đã có, không thay thế đăng nhập backend. Backend JWT access token và refresh token vẫn là nguồn xác thực khi gọi API.

## Mục tiêu

- Cho người dùng đã đăng nhập bật xác thực vân tay để bảo vệ truy cập lại vào dữ liệu private trong app.
- Không gửi vân tay, template biometric, kết quả biometric hoặc dữ liệu sinh trắc học lên backend.
- Không lưu biometric template trong app, Room, SharedPreferences hoặc backend.
- Không bypass backend auth: sau khi biometric thành công vẫn phải kiểm access token, refresh khi cần, và xử lý 401/403 như hiện tại.
- Không làm hỏng đường khẩn cấp: app không crash khi thiết bị không hỗ trợ biometric, chưa enroll, lockout, offline hoặc token lỗi.

## Không thuộc phạm vi

- Không thêm database/backend schema mới ở giai đoạn thiết kế này.
- Không thêm role/permission backend mới cho biometric.
- Không xây web quản trị hoặc chính sách admin mới.
- Không dùng biometric để đăng ký tài khoản, reset password, ký JWT hoặc thay refresh token.

## Threat model

Rủi ro cần giảm:

- Người cầm máy đã mở khóa thiết bị xem được hồ sơ y tế/private screen khi app còn token local.
- Người khác mở app từ recent apps khi token chưa hết hạn.
- Token local còn tồn tại sau khi người dùng rời app trong thời gian ngắn.

Rủi ro không giải quyết bằng biometric local:

- Backend token bị lộ ở nơi khác.
- Thiết bị bị root/hook runtime hoặc malware có quyền cao.
- Người có quyền mở khóa thiết bị bằng PIN/biometric hệ thống nếu policy cho fallback device credential.
- Public emergency link đã được chia sẻ hợp lệ và token public profile còn hạn.

Biện pháp chính:

- Biometric chỉ mở khóa UI local trước khi vào private surface.
- Token vẫn lưu bằng `SecurePrefs` như hiện tại; nếu `SecurePrefs` fallback về SharedPreferences thường thì phải coi là giảm bảo mật và không log token.
- Mọi API vẫn qua JWT/refresh token backend; backend revoke/expiry thắng biometric success.

## Quyết định backend

Không cần backend mới cho biometric ở giai đoạn này.

Lý do:

- AndroidX Biometric chạy local trên thiết bị; app không nhận hoặc lưu template vân tay.
- Backend không thể và không nên xác thực vân tay người dùng từ Android.
- Backend đã có JWT/refresh token để xác thực API; biometric chỉ là local gate trước khi dùng token đã lưu.
- Nếu muốn chính sách server-side sau này, ví dụ bắt buộc re-auth password cho hành động nhạy cảm, cần thiết kế API riêng; không nằm trong phạm vi này.

## Kết luận rà soát backend và token flow

Thời điểm rà soát: 16/06/2026 11:09:38

Đã đối chiếu với `backend/HelpId.Api/` và token flow Android hiện tại. Kết luận: không cần sửa backend schema, không cần migration, không cần API session policy mới cho biometric ở phạm vi này.

Hiện trạng backend liên quan:

- `AuthService` tạo JWT access token, hash refresh token, rotation refresh token và revoke khi logout/reuse.
- `AuthEndpoints` chỉ expose `register`, `login`, `refresh`, `logout`, `me`; các private API vẫn dựa vào Bearer access token và authorization policy.
- `HelpIdDbContext` đã có `Users`, `RefreshTokens`, RBAC `Roles`/`Permissions`/`UserRoles`/`RolePermissions`, profile và public link. Không có cột hoặc bảng nào cần biết biometric.
- Query backend hiện dùng EF Core LINQ/`SingleOrDefaultAsync`/`AnyAsync`/`Where`/`Join`; không cần raw SQL mới cho tính năng biometric.

Hiện trạng Android token flow liên quan:

- `LoginScreen` và `RegisterScreen` chỉ lưu token sau khi backend login/register thành công qua `AuthTokenStore.saveTokens`.
- `AuthTokenStore` lưu access token, refresh token, user id và hạn token bằng `SecurePrefs`; biometric không được lưu chung dưới dạng token mới.
- `MainActivity` khôi phục session bằng `hasValidSession()`, refresh token khi access token hết hạn, clear token khi refresh 400..403, và logout bằng best-effort revoke refresh token rồi `clearTokens()`.
- `BiometricUtils` hiện chỉ là wrapper local quanh AndroidX `BiometricPrompt`; API này không trả biometric template cho app.

Quyết định thiết kế bắt buộc:

- Biometric là local unlock cho token/session đã có trên thiết bị, không phải một phương thức backend auth mới.
- Không gửi dữ liệu vân tay, biometric template, biometric identifier, trạng thái enroll, lockout hoặc kết quả prompt lên backend.
- Không lưu biometric template trong app, Room, SharedPreferences, `SecurePrefs`, backend SQLite hoặc Firestore legacy.
- Không thêm bảng/cột `Biometric*`, không thêm permission/role biometric, không thêm endpoint kiểu `/biometric/*`.
- Backend JWT/refresh token vẫn là nguồn xác thực API. Nếu backend revoke refresh token hoặc access/refresh token hết hạn, biometric success không được bỏ qua trạng thái đó.
- Logout phải tiếp tục revoke/clear token như hiện tại; biometric setting local nếu có chỉ bị clear/disable ở app và không gọi backend.

Nếu sau này muốn server-side session policy, ví dụ bắt buộc re-auth password trước hành động đặc biệt hoặc quản lý danh sách thiết bị, đó là tính năng khác. Khi đó mới cần thiết kế API/schema/migration riêng và cập nhật UML database.

## Khi nào hỏi biometric

Hỏi biometric khi thỏa tất cả điều kiện:

- User đã đăng nhập backend trước đó và `AuthTokenStore` còn user id/token local.
- User đã bật biometric cho đúng `userId` đó.
- App đang mở lạnh, quay lại foreground sau ngưỡng khóa, hoặc user vào lại private surface sau khi app bị khóa local.
- Thiết bị báo biometric/device credential khả dụng theo policy.

Không hỏi biometric khi:

- Chưa login/register thành công.
- User chưa bật biometric.
- User đang ở Login/Register.
- User vừa logout và token đã bị clear.
- Thiết bị không hỗ trợ hoặc chưa enroll, khi đó hiển thị fallback user-friendly và tiếp tục yêu cầu đăng nhập/password theo flow hiện có.
- App đang xử lý SOS/fallback khẩn cấp đã được thiết kế không crash; không được chặn đường xem cache khẩn cấp nếu policy offline/local-cache cho phép.

## Chính sách fallback

Trạng thái thiết bị:

- `BIOMETRIC_SUCCESS`: cho phép bật và xác thực.
- `BIOMETRIC_ERROR_NONE_ENROLLED`: không bật được; hiển thị hướng dẫn enroll trong Settings, không crash.
- `BIOMETRIC_ERROR_NO_HARDWARE` hoặc `BIOMETRIC_ERROR_HW_UNAVAILABLE`: ẩn/disable toggle bật biometric, hiển thị lý do nếu user cố bật.
- Lockout tạm thời: hiển thị lỗi ngắn, cho thử lại sau hoặc dùng login password nếu token/session không thể mở khóa.
- Lockout lâu dài: yêu cầu device credential nếu policy cho phép; nếu không, chuyển Login.
- User cancel: giữ app ở màn hình khóa/auth, không clear token ngay.
- Lỗi hệ thống: không log PII/token; hiển thị lỗi chung.

Policy đề xuất: dùng `BIOMETRIC_STRONG` trước. Có thể cho fallback `DEVICE_CREDENTIAL` nếu muốn hỗ trợ lockout/thiết bị không có vân tay nhưng có khóa màn hình; quyết định này phải ghi rõ trong implementation prompt và test thủ công.

## Lưu trạng thái bật/tắt

Đề xuất tạo store riêng, ví dụ `BiometricPreferenceStore`, dùng `SecurePrefs.create(context, "helpid_biometric_prefs")`.

Dữ liệu lưu:

- `biometric_enabled_user_<hash/userId>`: boolean.
- `last_unlocked_at_epoch_ms_<hash/userId>`: optional, dùng nếu có timeout local lock.

Không lưu:

- Vân tay/template biometric.
- Access token/refresh token trong store biometric mới.
- Email, tên, số điện thoại, dữ liệu y tế.

Nếu dùng user id trong key, không log key đó. Nếu muốn giảm lộ định danh trong prefs name/key, hash user id bằng SHA-256 nội bộ trước khi tạo suffix.

## Luồng bật biometric sau login

1. User login/register thành công, token được lưu như hiện tại.
2. Ở màn hình profile/settings, user bật toggle “Xác thực vân tay”.
3. App kiểm tra biometric availability.
4. Nếu khả dụng, hiển thị `BiometricPrompt`.
5. Chỉ khi prompt success mới lưu enabled=true cho user hiện tại.
6. Nếu fail/cancel/không enroll thì không bật; hiển thị lỗi user-friendly.
7. Nếu logout, token bị clear. Setting biometric nên bị tắt hoặc tối thiểu không được tự áp dụng cho user khác. Đề xuất: logout clear biometric enabled cho user hiện tại để tránh nhầm tài khoản.

## Luồng tắt biometric

1. User đang authenticated vào profile/settings.
2. User tắt toggle.
3. App hỏi xác nhận ngắn.
4. Nếu xác nhận, lưu enabled=false cho user hiện tại.
5. Không xóa Room cache khi tắt biometric.
6. Không gọi backend.

## Luồng mở app bằng biometric

1. App mở, `MainActivity` đọc `AuthTokenStore`.
2. Nếu không có token/user id: đi Login/Register hoặc Firebase legacy fallback như hiện tại.
3. Nếu có user id và biometric enabled:
   - Chuyển vào local state khóa UI, ví dụ `AuthState.BiometricLocked(userId)` hoặc state phụ tương đương.
   - Hiển thị màn hình khóa biometric, không render private screens phía sau.
   - Prompt biometric sau khi UI sẵn sàng.
4. Nếu biometric success:
   - Nếu access token còn hạn: chuyển `Authenticated(userId)`.
   - Nếu access token hết hạn: refresh token như flow hiện tại; success thì lưu token mới, fail 401/403 thì clear token và chuyển Login/LocalCacheOnly theo cache policy.
   - Nếu offline và có Room cache: chuyển `LocalCacheOnly(userId, isOffline=true)` theo chính sách hiện tại; không sync remote.
5. Nếu cancel/fail:
   - Giữ ở màn hình khóa/local auth.
   - Cho nút “Đăng nhập bằng mật khẩu” để chuyển Login.
   - Không clear token chỉ vì user cancel.
6. Nếu thiết bị mất biometric/enrollment sau khi đã bật:
   - Disable biometric cho user hoặc yêu cầu login password.
   - Không crash.

## Quan hệ với access token và refresh token

- Biometric success không tạo token mới.
- Biometric success không làm refresh token hợp lệ nếu backend đã revoke hoặc hết hạn.
- Access token hết hạn vẫn phải gọi refresh như hiện tại.
- Refresh 401/403 vẫn phải clear token và không được cho vào authenticated private flow.
- Logout vẫn best-effort revoke refresh token qua backend rồi clear local token.
- Public emergency link mint vẫn cần Bearer access token hợp lệ; nếu token hết hạn thì refresh trước như `HelpIdApiEmergencyLinkRepository` hiện tại.

## UI/copy đề xuất

Các surface cần thêm:

- Toggle trong màn hình profile/settings: bật/tắt xác thực vân tay.
- Dialog xác nhận bật: giải thích biometric chỉ bảo vệ app trên thiết bị này.
- Dialog xác nhận tắt.
- Màn hình khóa biometric khi mở app lại.
- Nút fallback “Đăng nhập bằng mật khẩu”.
- Error text cho thiết bị không hỗ trợ, chưa enroll, lockout, cancel/fail.

## String resource cần thêm

Thêm đầy đủ vào `values`, `values-vi`, `values-de`, `values-es`, `values-fr`, `values-hi`.

| Key | en | vi | de | es | fr | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `biometric_settings_title` | Fingerprint unlock | Mở khóa bằng vân tay | Entsperren per Fingerabdruck | Desbloqueo con huella | Déverrouillage par empreinte | फिंगरप्रिंट अनलॉक |
| `biometric_settings_body` | Require fingerprint when opening HelpID on this device. | Yêu cầu vân tay khi mở HelpID trên thiết bị này. | Fingerabdruck beim Öffnen von HelpID auf diesem Gerät anfordern. | Requerir huella al abrir HelpID en este dispositivo. | Exiger l’empreinte à l’ouverture de HelpID sur cet appareil. | इस डिवाइस पर HelpID खोलते समय फिंगरप्रिंट मांगें। |
| `biometric_enable_confirm_title` | Enable fingerprint unlock? | Bật mở khóa bằng vân tay? | Fingerabdruck-Entsperrung aktivieren? | ¿Activar desbloqueo con huella? | Activer le déverrouillage par empreinte ? | फिंगरप्रिंट अनलॉक चालू करें? |
| `biometric_enable_confirm_body` | Your backend session is still required for sync and API access. | Phiên backend vẫn cần cho đồng bộ và truy cập API. | Deine Backend-Sitzung bleibt für Sync und API-Zugriff erforderlich. | Tu sesión backend sigue siendo necesaria para sincronizar y usar la API. | Votre session backend reste nécessaire pour la synchronisation et l’API. | सिंक और API एक्सेस के लिए backend session अभी भी जरूरी है। |
| `biometric_disable_confirm_title` | Turn off fingerprint unlock? | Tắt mở khóa bằng vân tay? | Fingerabdruck-Entsperrung deaktivieren? | ¿Desactivar desbloqueo con huella? | Désactiver le déverrouillage par empreinte ? | फिंगरप्रिंट अनलॉक बंद करें? |
| `biometric_unlock_title` | Unlock HelpID | Mở khóa HelpID | HelpID entsperren | Desbloquear HelpID | Déverrouiller HelpID | HelpID अनलॉक करें |
| `biometric_unlock_subtitle` | Use fingerprint to continue. | Dùng vân tay để tiếp tục. | Verwende deinen Fingerabdruck, um fortzufahren. | Usa tu huella para continuar. | Utilisez votre empreinte pour continuer. | जारी रखने के लिए फिंगरप्रिंट इस्तेमाल करें। |
| `biometric_use_password` | Use password instead | Dùng mật khẩu | Stattdessen Passwort verwenden | Usar contraseña | Utiliser le mot de passe | पासवर्ड इस्तेमाल करें |
| `biometric_not_available` | Fingerprint unlock is not available on this device. | Thiết bị này không hỗ trợ mở khóa bằng vân tay. | Fingerabdruck ist auf diesem Gerät nicht verfügbar. | La huella no está disponible en este dispositivo. | L’empreinte n’est pas disponible sur cet appareil. | इस डिवाइस पर फिंगरप्रिंट उपलब्ध नहीं है। |
| `biometric_not_enrolled` | Add a fingerprint in system settings first. | Hãy thêm vân tay trong cài đặt hệ thống trước. | Füge zuerst einen Fingerabdruck in den Systemeinstellungen hinzu. | Agrega una huella en ajustes del sistema primero. | Ajoutez d’abord une empreinte dans les réglages système. | पहले system settings में फिंगरप्रिंट जोड़ें। |
| `biometric_locked_out` | Too many attempts. Try again later. | Thử quá nhiều lần. Hãy thử lại sau. | Zu viele Versuche. Bitte später erneut versuchen. | Demasiados intentos. Inténtalo más tarde. | Trop de tentatives. Réessayez plus tard. | बहुत अधिक प्रयास। बाद में फिर कोशिश करें। |
| `biometric_enabled` | Fingerprint unlock is on. | Đã bật mở khóa bằng vân tay. | Fingerabdruck-Entsperrung ist aktiviert. | Desbloqueo con huella activado. | Déverrouillage par empreinte activé. | फिंगरप्रिंट अनलॉक चालू है। |
| `biometric_disabled` | Fingerprint unlock is off. | Đã tắt mở khóa bằng vân tay. | Fingerabdruck-Entsperrung ist deaktiviert. | Desbloqueo con huella desactivado. | Déverrouillage par empreinte désactivé. | फिंगरप्रिंट अनलॉक बंद है। |

Các key đã có nhưng nên rà lại locale vì một số locale đang giữ tiếng Anh: `biometric_access_title`, `biometric_access_subtitle`, `biometric_cancel`, `biometric_auth_failed`.

## State/code đề xuất

- Thêm state như `AuthState.BiometricLocked(userId: String)` hoặc state UI riêng đặt trước render private screens.
- Thêm wrapper `BiometricGate` hoặc composable màn hình khóa; không đặt logic prompt trực tiếp rải rác trong từng màn hình.
- Tách mapper lỗi biometric thành hàm thuần để unit test được.
- `BiometricUtils.isBiometricAvailable()` nên trả enum/sealed class thay vì boolean để phân biệt unavailable/not enrolled/lockout.
- `BiometricUtils.showBiometricPrompt()` không nên truyền raw error text ra UI nếu error có thể không locale-aware; map error code sang string resource.

## Failure modes bắt buộc

- Không hỗ trợ biometric: không bật được, vẫn dùng login/password.
- Chưa enroll: hướng dẫn user vào Settings, không bật silently.
- User cancel: giữ locked, không xóa token.
- Fail nhiều lần: hiện lỗi, không vào private screen.
- Lockout: dùng fallback credential nếu policy cho phép hoặc yêu cầu login password.
- Offline: biometric success chỉ mở cache local nếu refresh không thể chạy và có Room cache; không sync remote.
- Backend revoke token: biometric success không được bỏ qua revoke; clear token theo 401/403.
- Logout: clear token, best-effort revoke, clear/disable biometric setting cho user hiện tại.
- Đổi user: biometric enabled phải theo user id, không dùng chung toàn app.

## Test plan

Automated:

- Unit test mapper availability/error code -> UI state/string key.
- Unit test biometric setting store theo user id: enable/disable/user A không áp dụng user B.
- Unit test auth decision: valid token + enabled -> biometric locked; success -> authenticated; refresh 401 -> clear token/unauthenticated; offline + cache -> local cache only.
- Regression test parser auth nếu có sửa `AuthTokenStore` hoặc startup flow.

Manual emulator/device:

- Thiết bị không có biometric.
- Thiết bị có biometric nhưng chưa enroll.
- Enroll fingerprint, bật biometric thành công.
- Cancel prompt khi bật: setting không bật.
- Mở app lại, success vào app.
- Mở app lại, cancel/fail giữ locked và có fallback login.
- Fail nhiều lần/lockout.
- Tắt biometric thành công.
- Logout: token clear, biometric không tự mở user cũ.
- Login user khác: setting user cũ không áp dụng.
- Offline với Room cache: xem cache theo policy, không sync remote.
- Token hết hạn: biometric success rồi refresh; 401/403 chuyển đúng state.
- SOS/QR/NFC không crash khi biometric unavailable hoặc token invalid.

Commands tối thiểu khi implement runtime:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Ghi kết quả vào `passed-testcases.md` hoặc `failed-testcases.md`, không ghi token, email, số điện thoại, vị trí hoặc dữ liệu y tế thật.
