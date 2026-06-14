# Thiết kế frontend Android: đăng ký / đăng nhập

Thời điểm thiết kế: 14/06/2026 23:45:00

Tài liệu này chốt thiết kế chi tiết frontend Android cho tính năng đăng ký/đăng nhập trước khi code. Backend và contract đã được chốt trong `contract-dang-ky-dang-nhap.md`. Chưa sửa runtime UI trong prompt này.

## 1. Auth state và route trong `MainActivity`

### Sealed class mới

```kotlin
sealed class AuthState {
    data object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    // Có cache Room nhưng không có token hợp lệ (offline hoặc phiên hết hạn)
    data class LocalCacheOnly(val cachedUserId: String?) : AuthState()
    data object Unauthenticated : AuthState()
}
```

`AuthState` thay toàn bộ `isInitialized`, `userId`, `initError` hiện có trong `AppNavigation`.

### Startup flow trong `LaunchedEffect`

```
1. Đọc AuthTokenStore (accessToken, refreshToken, userId, accessTokenExpiresAtUtc)
2. Nếu accessToken còn hạn → Authenticated(userId)
3. Nếu accessToken hết hạn VÀ refreshToken tồn tại:
   a. POST /api/v1/auth/refresh
   b. 200: lưu token pair mới → Authenticated(userId)
   c. Lỗi mạng/timeout: nếu Room có cache → LocalCacheOnly(userId)
   d. 401: xóa token; nếu Room có cache → LocalCacheOnly(userId) else Unauthenticated
4. Nếu không có token: nếu Room có cache → LocalCacheOnly(null) else Unauthenticated
```

Không log token, email, userId trong bất kỳ nhánh nào.

### Routes hiện có và routes mới

| Route | Hiện có | Loại |
|---|---|---|
| `"emergency"` | Có | Main screen |
| `"qr"` | Có | Cần `Authenticated` |
| `"edit"` | Có | Cần `Authenticated` |
| `"language"` | Có | Không cần auth |
| `"login"` | **Mới** | Auth screen |
| `"register"` | **Mới** | Auth screen |

### Kết xuất theo `AuthState`

```kotlin
when (authState.value) {
    is AuthState.Initializing ->
        InitSkeleton(errorText = null)

    is AuthState.Unauthenticated ->
        // Không scaffold, không bottom bar
        LoginScreen(
            onLoginSuccess = { userId -> authState.value = AuthState.Authenticated(userId) },
            onGoToRegister = { currentScreen.value = "register" }
        )

    is AuthState.LocalCacheOnly -> {
        // Không scaffold, không bottom bar
        // EmergencyScreen vẫn đọc Room cache
        EmergencyScreenLocalMode(
            cachedUserId = state.cachedUserId,
            onLoginClick = { currentScreen.value = "login" }
        )
    }

    is AuthState.Authenticated -> {
        // Scaffold đầy đủ với bottom bar
        Scaffold(bottomBar = { HelpIdBottomBar(...) }) { ... }
    }
}
```

**Bottom bar chỉ hiển thị khi `Authenticated`.**  
`LocalCacheOnly` ẩn bottom bar để tránh user vào QR/Edit khi không có token.

---

## 2. Màn hình Login

File đề xuất: `ui/LoginScreen.kt`

### Layout (Compose, top-to-bottom)

```
[App logo/icon + tên "HelpID"]
[Tiêu đề: "Đăng nhập" / "Login"]
[Mô tả ngắn: "Đồng bộ hồ sơ khẩn cấp của bạn"]

[OutlinedTextField: Email]
   keyboardType = KeyboardType.Email
   imeAction = ImeAction.Next
   singleLine = true
   [Inline error nếu có]

[OutlinedTextField: Password]
   visualTransformation = PasswordVisualTransformation (default)
   Nút toggle show/hide password (icon Eye ở trailing)
   imeAction = ImeAction.Done → trigger login
   singleLine = true
   [Inline error nếu có]

[Nút "ĐĂNG NHẬP" / "LOGIN"]
   Disabled khi đang loading
   CircularProgressIndicator thay text khi loading

[API error banner: Text màu error, 1–2 dòng, hiển thị khi có lỗi mức request]

[TextButton: "Chưa có tài khoản? Đăng ký" → navigate "register"]
```

### State

```kotlin
var email by remember { mutableStateOf("") }
var password by remember { mutableStateOf("") }
var emailError by remember { mutableStateOf<String?>(null) }
var passwordError by remember { mutableStateOf<String?>(null) }
var apiError by remember { mutableStateOf<String?>(null) }
var isLoading by remember { mutableStateOf(false) }
var showPassword by remember { mutableStateOf(false) }
```

### Luồng nhấn "ĐĂNG NHẬP"

1. Chạy client validation → nếu lỗi, set field error và dừng.
2. Set `isLoading = true`, `apiError = null`.
3. Gọi `AuthRepository.login(email.trim(), password, deviceName)` trên `Dispatchers.IO`.
4. Nếu thành công: gọi `onLoginSuccess(userId)`.
5. Nếu lỗi: map sang string resource, set `apiError`, `isLoading = false`.

---

## 3. Màn hình Register

File đề xuất: `ui/RegisterScreen.kt`

### Layout

```
[App logo/icon + tên "HelpID"]
[Tiêu đề: "Tạo tài khoản" / "Create Account"]

[OutlinedTextField: Tên hiển thị (tùy chọn)]
   imeAction = ImeAction.Next
   singleLine = true
   [Inline error nếu có]

[OutlinedTextField: Email]
   (như login)

[OutlinedTextField: Mật khẩu]
   (như login)

[OutlinedTextField: Xác nhận mật khẩu]
   imeAction = ImeAction.Done → trigger register
   [Inline error nếu không khớp]

[Nút "ĐĂNG KÝ" / "REGISTER"]
   Disabled khi loading; spinner khi loading

[API error banner]

[TextButton: "Đã có tài khoản? Đăng nhập" → navigate "login"]
```

### State

Tương tự LoginScreen, bổ sung:

```kotlin
var displayName by remember { mutableStateOf("") }
var confirmPassword by remember { mutableStateOf("") }
var displayNameError by remember { mutableStateOf<String?>(null) }
var confirmPasswordError by remember { mutableStateOf<String?>(null) }
```

`deviceName` lấy từ `android.os.Build.MODEL`, không hiển thị, tự gửi lên API.

### Luồng nhấn "ĐĂNG KÝ"

1. Client validation → lỗi → dừng.
2. `isLoading = true`, `apiError = null`.
3. Gọi `AuthRepository.register(...)`.
4. Thành công `201`: gọi `onRegisterSuccess(userId)`.
5. Lỗi: map sang string resource.

---

## 4. Validation phía client

### Login

| Field | Điều kiện | Error string key |
|---|---|---|
| Email | Trống | `auth_error_email_required` |
| Email | Không chứa `@` | `auth_error_email_invalid` |
| Password | Trống | `auth_error_password_required` |

### Register

| Field | Điều kiện | Error string key |
|---|---|---|
| Display name | Dài hơn 80 ký tự | `auth_error_display_name_too_long` |
| Email | Trống | `auth_error_email_required` |
| Email | Không chứa `@` | `auth_error_email_invalid` |
| Email | Dài hơn 254 | `auth_error_email_too_long` |
| Password | Trống | `auth_error_password_required` |
| Password | Ngắn hơn 12 | `auth_error_password_too_short` |
| Password | Dài hơn 128 | `auth_error_password_too_long` |
| Confirm password | Không khớp password | `auth_error_passwords_mismatch` |

Không trim password trước khi validate hoặc gửi lên API (nhất quán với backend).

### Map API error → string key

| Nguyên nhân API | String key |
|---|---|
| 409 Conflict | `auth_error_email_taken` |
| 401 `INVALID_CREDENTIALS` | `auth_error_invalid_credentials` |
| 423 `ACCOUNT_LOCKED` | `auth_error_account_locked` |
| 422, field `email.invalid` | `auth_error_email_invalid` |
| 422, field `password.too_short` | `auth_error_password_too_short` |
| Network / timeout | `auth_error_network` |
| 5xx / khác | `auth_error_server` |

---

## 5. Strings cần thêm

Thêm vào **tất cả 6 file**: `values/strings.xml`, `values-vi/`, `values-de/`, `values-es/`, `values-fr/`, `values-hi/`.

### Danh sách key

```xml
<!-- Auth chung -->
<string name="auth_login_title">Login</string>
<string name="auth_login_subtitle">Sync your emergency profile</string>
<string name="auth_register_title">Create Account</string>
<string name="auth_register_subtitle">Save your emergency profile</string>

<!-- Fields -->
<string name="auth_field_email">Email</string>
<string name="auth_field_password">Password</string>
<string name="auth_field_confirm_password">Confirm Password</string>
<string name="auth_field_display_name">Your Name (optional)</string>

<!-- Buttons -->
<string name="auth_btn_login">LOGIN</string>
<string name="auth_btn_register">REGISTER</string>
<string name="auth_btn_logging_in">LOGGING IN…</string>
<string name="auth_btn_registering">REGISTERING…</string>

<!-- Navigation links -->
<string name="auth_link_go_to_register">Don\'t have an account? Register</string>
<string name="auth_link_go_to_login">Already have an account? Login</string>

<!-- Validation errors -->
<string name="auth_error_email_required">Email is required</string>
<string name="auth_error_email_invalid">Enter a valid email address</string>
<string name="auth_error_email_too_long">Email is too long (max 254 characters)</string>
<string name="auth_error_email_taken">This email is already registered. Try logging in.</string>
<string name="auth_error_password_required">Password is required</string>
<string name="auth_error_password_too_short">Password must be at least 12 characters</string>
<string name="auth_error_password_too_long">Password is too long</string>
<string name="auth_error_passwords_mismatch">Passwords do not match</string>
<string name="auth_error_display_name_too_long">Name is too long (max 80 characters)</string>

<!-- API errors -->
<string name="auth_error_invalid_credentials">Invalid email or password</string>
<string name="auth_error_account_locked">Account temporarily locked. Please try again later.</string>
<string name="auth_error_network">No connection. Check your internet and try again.</string>
<string name="auth_error_server">Something went wrong. Please try again.</string>

<!-- LocalCacheOnly banner -->
<string name="auth_banner_session_expired">Session expired — viewing cached profile</string>
<string name="auth_banner_offline">Offline — viewing cached profile</string>
<string name="auth_banner_login_again">Login again</string>

<!-- Logout -->
<string name="auth_logout">Logout</string>
<string name="auth_logout_confirm_title">Log out?</string>
<string name="auth_logout_confirm_body">Your emergency profile will remain available offline.</string>
<string name="auth_logout_confirm_yes">LOG OUT</string>
<string name="auth_logout_confirm_no">CANCEL</string>
```

Mỗi locale phải dịch đầy đủ. Nếu locale chưa có bản dịch chính xác, để fallback English từ `values/strings.xml` theo cơ chế Android resource.

---

## 6. Hành vi khi offline

### Trạng thái `Authenticated` + mất mạng

- EmergencyScreen: đọc Room cache, hiển thị bình thường; badge "offline" nếu đang có.
- EditProfileScreen: lưu local Room; retry sync khi có mạng (WorkManager nếu cần, hoặc retry thủ công).
- QRScreen: nếu không mint được → hiển thị `qr_mint_error` và nút RETRY (giữ nguyên behavior hiện tại).
- SOS: vẫn dùng cached profile + contacts local, không block.
- Login/logout: thông báo `auth_error_network`, không crash.

### Trạng thái `LocalCacheOnly`

- EmergencyScreen hiển thị Room cache với auth banner ở đầu (không thể dismiss).
- QRScreen không khả dụng (route QR bị ẩn khi không `Authenticated`).
- EditProfileScreen không khả dụng (route Edit bị ẩn khi không `Authenticated`).
- SOS vẫn hoạt động bình thường từ EmergencyScreen.

### Không có cache + offline

- `Unauthenticated` → LoginScreen → nhấn login → `auth_error_network`.
- Không có EmergencyScreen fallback; hướng dẫn kiểm tra kết nối.

---

## 7. Khi có profile local nhưng token hết hạn

**Tình huống**: App mở lại sau thời gian dài, access token hết hạn.

### Startup flow

1. `AuthTokenStore.accessTokenExpiresAt < now` → access token hết hạn.
2. `refreshToken` còn trong store → gọi `POST /api/v1/auth/refresh` (có mạng):
   - Thành công → `Authenticated`, app vào bình thường.
   - Lỗi mạng → `LocalCacheOnly(cachedUserId)` → EmergencyScreen với banner.
   - 401 (refresh hết hạn/revoked) → xóa token → `LocalCacheOnly` nếu Room có cache.
3. Không có `refreshToken` → `LocalCacheOnly` nếu Room có cache.

### Auth banner trong `LocalCacheOnly`

Banner hiển thị **cố định** ở đầu EmergencyScreen (không overlay, không che dữ liệu):

```
┌────────────────────────────────────────┐
│ ⚠ Phiên hết hạn — đang xem cache local │
│ [ĐĂNG NHẬP LẠI]                        │
└────────────────────────────────────────┘
[Nội dung EmergencyScreen bình thường bên dưới]
```

String keys: `auth_banner_session_expired`, `auth_banner_login_again`.

Nhấn "ĐĂNG NHẬP LẠI" → navigate `currentScreen = "login"` (trong `AppNavigation`).

Sau login thành công:
- `authState = Authenticated(userId)`.
- `currentScreen = "emergency"`.
- Banner biến mất; bottom bar xuất hiện trở lại.

**Không xóa Room data** khi phiên hết hạn.

---

## 8. Logout UX

### Vị trí nút Logout

Thêm vào `EditProfileScreen`: nút "Logout" ở cuối form, sau nút Save.  
Style: `TextButton` màu `error` để phân biệt với Save.

Hoặc: thêm icon ba chấm vào header `EditProfileScreen` với menu "Logout".

Không thêm vào bottom bar hoặc EmergencyScreen header để tránh tai nạn trong tình huống khẩn cấp.

### Logout flow

1. User nhấn "Logout" → hiện `AlertDialog` (`auth_logout_confirm_title`, `auth_logout_confirm_body`).
2. Nhấn "CANCEL" → đóng dialog, không làm gì.
3. Nhấn "LOG OUT":
   a. Gọi `POST /api/v1/auth/logout` với `refreshToken` (best-effort, fire-and-forget, không blocking UI).
   b. Ngay lập tức: xóa `AuthTokenStore` (access token, refresh token, expiry).
   c. **Không xóa Room `LocalUserProfile`**.
   d. Nếu Room có profile → `authState = LocalCacheOnly(cachedUserId)`.
   e. Nếu Room trống → `authState = Unauthenticated`.
4. Navigation:
   - `LocalCacheOnly` → EmergencyScreen với banner.
   - `Unauthenticated` → LoginScreen.

### Tại sao không xóa Room cache khi logout

Room cache là nguồn dữ liệu khẩn cấp. Nếu người dùng cần cấp cứu ngay sau khi logout (ví dụ: bấm nhầm), hồ sơ vẫn đọc được offline mà không cần đăng nhập lại.

Dữ liệu Room chỉ nên xóa khi: người dùng xác nhận xóa tài khoản hoặc xóa dữ liệu cá nhân (feature riêng, không liên quan logout thông thường).

---

## 9. Quay lại EmergencyScreen an toàn

### Nguyên tắc bất biến

`EmergencyScreen` không bao giờ bị gate hoàn toàn bởi `Authenticated`. Luôn hiển thị Room cache nếu có, kể cả khi `LocalCacheOnly`.

### Sau login/register thành công

```kotlin
onLoginSuccess = { userId ->
    authState.value = AuthState.Authenticated(userId)
    currentScreen.value = "emergency"   // hoặc restore intent screen
}
```

Nếu intent có `open_screen = "emergency"`, ưu tiên giá trị đó.

### Từ intent/notification

- Intent `open_screen = "emergency"`:
  - Nếu `Authenticated` → render EmergencyScreen bình thường.
  - Nếu `LocalCacheOnly` → render EmergencyScreen với banner.
  - Nếu `Unauthenticated` → lưu `pendingScreen = "emergency"`, hiển thị LoginScreen; sau login thành công navigate đến `pendingScreen`.

### QR/Edit cần auth

- Nếu user ở `LocalCacheOnly` và cố navigate đến `"qr"` hoặc `"edit"` qua bottom bar (bottom bar không hiển thị trong `LocalCacheOnly`, nhưng phòng ngừa):
  - Route về `"login"` thay vì crash.

### Sau refresh thành công khi background

- Nếu background task refresh thành công → cập nhật `authState = Authenticated` → bottom bar xuất hiện, banner biến mất.
- Nếu app đang ở `LocalCacheOnly` và có mạng → có thể trigger refresh tự động một lần (ví dụ khi `onResume`).

---

## 10. AuthTokenStore — interface tối thiểu

File đề xuất: `data/AuthTokenStore.kt`

Dùng `SecurePrefs.create(context, "auth_tokens")`:

```kotlin
interface AuthTokenStore {
    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        accessTokenExpiresAtEpochMs: Long,
        refreshTokenExpiresAtEpochMs: Long
    )

    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getUserId(): String?
    fun isAccessTokenExpired(): Boolean   // compare với System.currentTimeMillis()

    fun clearTokens()   // dùng khi logout hoặc refresh fail 401
}
```

Không log giá trị token ở bất kỳ đâu trong implementation.

---

## 11. AuthRepository — interface tối thiểu

File đề xuất: `data/AuthRepository.kt`

```kotlin
interface AuthRepository {
    suspend fun login(email: String, password: String, deviceName: String): AuthResult
    suspend fun register(email: String, password: String, displayName: String?, deviceName: String): AuthResult
    suspend fun refresh(refreshToken: String, deviceName: String): AuthResult
    suspend fun logout(refreshToken: String): LogoutResult
}

sealed class AuthResult {
    data class Success(val userId: String, val accessToken: String, val refreshToken: String,
                       val accessTokenExpiresAtEpochMs: Long, val refreshTokenExpiresAtEpochMs: Long) : AuthResult()
    data class ValidationError(val fieldErrors: Map<String, String>) : AuthResult()
    data class ApiError(val httpStatus: Int, val errorCode: String?) : AuthResult()
    data object NetworkError : AuthResult()
}

sealed class LogoutResult {
    data object Success : LogoutResult()
    data object NetworkError : LogoutResult()  // vẫn xóa token local
}
```

---

## 12. Checklist trước khi code

- [ ] `AuthTokenStore` implementation dùng `SecurePrefs`, không log token.
- [ ] `AuthRepository` implementation: `HttpURLConnection` hoặc Retrofit gọi đúng endpoint/headers.
- [ ] `LoginScreen` và `RegisterScreen`: dùng `stringResource`, cập nhật toàn bộ locale.
- [ ] `AppNavigation` refactor: `AuthState` thay thế `isInitialized`/`userId`/`initError`.
- [ ] `EmergencyScreenLocalMode` hoặc tham số `showAuthBanner` trong `EmergencyScreen`.
- [ ] Logout thêm vào `EditProfileScreen`, có `AlertDialog` xác nhận.
- [ ] Không xóa Room khi logout.
- [ ] Intent `open_screen` vẫn hoạt động cho `LocalCacheOnly`.
- [ ] Build/lint: `./gradlew :app:assembleDebug :app:lintDebug`.
- [ ] Test unit: `AuthTokenStore` đọc/ghi/clear; `AuthRepository` map response/error.
- [ ] Manual test: login/register/logout/offline/token hết hạn/quay về EmergencyScreen.
