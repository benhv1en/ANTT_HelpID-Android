# Contract: Trang Admin Android

Ngày lập: 16/06/2026

---

## 1. Phạm vi tính năng

Trang admin implement trên **Android app** và **web (`helper-id/`)**. Admin vẫn là user thông thường; sự khác biệt duy nhất là role `role_admin` trong JWT và DB, mở khóa các endpoint và màn hình/route riêng.

### 1.1 Dashboard thống kê

Màn hình đầu tiên khi vào Admin. Hiển thị 4 số:

| Metric | Nguồn dữ liệu |
|---|---|
| Tổng số user đã đăng ký | `COUNT(Users)` không tính soft-deleted |
| Tổng số profile đã tạo | `COUNT(UserProfiles)` |
| Tổng số public link đã mint | `COUNT(PublicProfileLinks)` |
| Audit events trong 7 ngày qua | `COUNT(AuditEvents WHERE CreatedAtUtc >= now-7d)` |

### 1.2 Danh sách user

Tab/section thứ hai. Hiển thị metadata người dùng, **không hiển thị dữ liệu y tế**:

- Email
- DisplayName (nếu có)
- Danh sách role hiện tại (e.g., "User", "Admin")
- Trạng thái: đang bị khóa (`LockoutUntilUtc > now`) hay active
- Ngày tạo tài khoản

Phân trang: `page` (bắt đầu từ 1) và `size` (mặc định 20, tối đa 100).

### 1.3 Gán / thu hồi role admin

Từ mỗi dòng trong danh sách user:
- Nếu user chưa có `role_admin` → nút "Gán Admin"
- Nếu user đã có `role_admin` → nút "Thu hồi Admin"
- Admin không thể thu hồi role admin của **chính mình** (bảo vệ khỏi tự khóa)

---

## 2. API Endpoints mới

Tất cả nằm dưới prefix `/api/v1/admin/`. Tất cả đều yêu cầu `RequireAuthorization("AdminMetadata")`.

### 2.1 `GET /api/v1/admin/stats`

**Auth:** Bearer JWT với claim `permission: admin:metadata:read` và role `Admin`.

**Response 200:**
```json
{
  "totalUsers": 42,
  "totalProfiles": 38,
  "totalPublicLinks": 15,
  "auditEventsLast7Days": 127
}
```

**Lỗi:**
- `401` — không có token
- `403` — có token nhưng không phải admin

---

### 2.2 `GET /api/v1/admin/users?page={page}&size={size}`

**Auth:** Bearer JWT admin.

**Query params:**
- `page`: số nguyên ≥ 1, mặc định 1
- `size`: số nguyên 1–100, mặc định 20

**Response 200:**
```json
{
  "users": [
    {
      "userId": "f19b6061cdf24f2886bb521a53a6f4d7",
      "email": "user@example.com",
      "displayName": "Jane Doe",
      "roles": ["User"],
      "isLocked": false,
      "createdAtUtc": "2026-01-15T10:00:00Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "totalCount": 42
}
```

**KHÔNG trả:** `passwordHash`, `securityStamp`, `phoneNumber`, dữ liệu y tế (allergies, medicalNotes, emergencyContacts).

**Lỗi:**
- `400` — `page < 1` hoặc `size` ngoài khoảng cho phép
- `401` — không có token
- `403` — không phải admin

---

### 2.3 `POST /api/v1/admin/users/{userId}/roles/{roleId}`

Gán role cho user.

**Auth:** Bearer JWT admin.

**Path params:**
- `userId`: ID user đích (string, max 64 ký tự)
- `roleId`: phải là một trong `["role_user", "role_admin"]` — whitelist cứng, không nhận giá trị tùy tiện

**Response:**
- `204 No Content` — gán thành công (hoặc đã có role đó rồi, idempotent)
- `400` — `roleId` không nằm trong whitelist
- `401` — không có token
- `403` — không phải admin, hoặc caller đang cố thu hồi role của chính mình
- `404` — `userId` không tồn tại

---

### 2.4 `DELETE /api/v1/admin/users/{userId}/roles/{roleId}`

Thu hồi role của user.

**Auth:** Bearer JWT admin.

**Path params:**
- `userId`: ID user đích
- `roleId`: phải là một trong `["role_user", "role_admin"]`

**Ràng buộc quan trọng:** Backend kiểm tra `userId` trong path có trùng với `sub` trong JWT của caller không. Nếu trùng và `roleId == "role_admin"` → trả `403` (admin không thể tự thu hồi role admin của mình).

**Response:**
- `204 No Content` — thu hồi thành công (hoặc user không có role đó, idempotent)
- `400` — `roleId` không nằm trong whitelist
- `401` — không có token
- `403` — không phải admin, hoặc admin tự thu hồi role admin của mình
- `404` — `userId` không tồn tại

---

## 3. Security contract

| Rule | Chi tiết |
|---|---|
| Authorization | Tất cả 4 endpoint: `RequireAuthorization("AdminMetadata")` — policy này đã tồn tại trong `HelpIdAuthorizationServiceCollectionExtensions.cs`, yêu cầu role `Admin` VÀ permission `admin:metadata:read` |
| userId từ JWT | Backend lấy caller identity từ `HttpContext.User` (JWT `sub` claim), **không tin** `userId` trong path/body để xác định caller |
| No sensitive fields | Response tuyệt đối không trả: `passwordHash`, `securityStamp`, `tokenHash`, `phoneNumber`, `allergies`, `medicalNotes`, `emergencyContacts`, `publicKey`, refresh token |
| Role whitelist | Chỉ cho phép gán/thu hồi `role_user` và `role_admin`. Mọi `roleId` khác → 400 |
| Self-protection | Admin không thể DELETE role `role_admin` của chính mình → 403 |
| No health data | Admin chỉ thấy metadata user (email, role, ngày tạo, trạng thái khóa). Không có API nào trả hồ sơ y tế người dùng khác |
| SQL injection | Tất cả query qua EF Core LINQ, không ghép chuỗi raw SQL |
| Audit log | Tất cả thao tác gán/thu hồi role phải ghi `AuditEvent` (không ghi dữ liệu nhạy cảm) |

---

## 4. Android UX

### 4.1 Phát hiện admin từ JWT

`AuthTokenStore` hiện chỉ lưu token string thô, không decode payload. Cần thêm method `isAdmin(): Boolean`:

```kotlin
// Trong AuthTokenStore.kt
fun isAdmin(): Boolean {
    val token = getAccessToken() ?: return false
    return try {
        val parts = token.split(".")
        if (parts.size != 3) return false
        val payloadJson = String(
            android.util.Base64.decode(
                parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
                        .replace('-', '+').replace('_', '/'),
                android.util.Base64.DEFAULT
            ),
            Charsets.UTF_8
        )
        // JWT permission claims là array: "permission": ["admin:metadata:read", ...]
        payloadJson.contains("\"admin:metadata:read\"")
    } catch (_: Exception) {
        false
    }
}
```

**Lưu ý:** Đây chỉ là kiểm tra client-side để ẩn/hiện UI. Backend vẫn luôn enforce `RequireAuthorization("AdminMetadata")` — không phụ thuộc vào kết quả `isAdmin()` phía client.

### 4.2 Điểm vào trang admin

- Nút "Quản trị" (icon shield hoặc text) xuất hiện ở cuối màn hình `EditProfileScreen` (tab Profile), chỉ khi `tokenStore.isAdmin() == true`
- Khi nhấn: `onAdminClick()` callback → `currentScreen.value = "admin"` trong `AppNavigation`

### 4.3 Navigation state mới

Trong `AppNavigation` (`MainActivity.kt`), thêm vào `when(currentScreen.value)` bên trong `else` (Authenticated):

```kotlin
"admin" -> {
    AdminScreen(
        onBackClick = { currentScreen.value = "edit" },
        onUnauthorized = { currentScreen.value = "emergency" }
    )
}
```

Kiểm tra quyền khi vào màn hình: nếu `!tokenStore.isAdmin()` → `currentScreen.value = "emergency"` ngay lập tức.

### 4.4 Màn hình / composable cần tạo

| File | Mô tả |
|---|---|
| `app/src/main/java/com/helpid/app/ui/AdminScreen.kt` | Màn hình admin chính, 2 tab: Dashboard và Users |
| `app/src/main/java/com/helpid/app/data/HelpIdApiAdminRepository.kt` | Repository gọi admin API, inject `ProfileTokenSource` + `ProfileHttpClient` để unit test |

### 4.5 String keys cần thêm (6 locale)

| Key | Nội dung (values/ — tiếng Anh) |
|---|---|
| `admin_screen_title` | "Admin" |
| `admin_tab_dashboard` | "Dashboard" |
| `admin_tab_users` | "Users" |
| `admin_stat_total_users` | "Total users" |
| `admin_stat_total_profiles` | "Profiles created" |
| `admin_stat_total_links` | "Public links minted" |
| `admin_stat_audit_7d` | "Audit events (7 days)" |
| `admin_user_role_label` | "Roles" |
| `admin_user_locked` | "Locked" |
| `admin_user_active` | "Active" |
| `admin_assign_admin_role` | "Grant Admin" |
| `admin_revoke_admin_role` | "Revoke Admin" |
| `admin_action_success` | "Done" |
| `admin_action_loading` | "Processing..." |
| `admin_error_unauthorized` | "You do not have admin access." |
| `admin_error_network` | "Network error. Please try again." |
| `admin_error_not_found` | "User not found." |
| `admin_self_revoke_blocked` | "You cannot remove your own admin role." |
| `admin_entry_button` | "Admin Panel" |
| `admin_prev_page` | "Previous" |
| `admin_next_page` | "Next" |

---

## 5. Cách gán role admin thủ công để test

Lấy userId của tài khoản cần nâng quyền:

```bash
sqlite3 backend/HelpId.Api/App_Data/helpid-dev.db \
  "SELECT Id, Email FROM Users;"
```

Gán role admin:

```bash
sqlite3 backend/HelpId.Api/App_Data/helpid-dev.db \
  "INSERT OR IGNORE INTO UserRoles (UserId, RoleId, AssignedAtUtc)
   VALUES ('<userId>', 'role_admin', datetime('now'));"
```

**Quan trọng:** Sau khi gán role, phải **đăng nhập lại** (login endpoint tạo JWT mới). JWT cũ không tự động cập nhật claim. Access token mới sẽ chứa `"role": ["User","Admin"]` và `"permission": [..., "admin:metadata:read"]`.

Thu hồi role admin (nếu cần reset):

```bash
sqlite3 backend/HelpId.Api/App_Data/helpid-dev.db \
  "DELETE FROM UserRoles
   WHERE UserId = '<userId>' AND RoleId = 'role_admin';"
```

---

## 6. Không nằm trong phạm vi tính năng này

- Xem chi tiết hồ sơ y tế của user khác — admin không được xem, và API không trả
- Xóa tài khoản user — chưa implement
- Export audit log — không có
- Thông báo email khi bị gán/thu hồi admin — không có

---

## 7. Web Admin Panel (`helper-id/`)

### 7.1 Routes

| Route | Mô tả |
|---|---|
| `/admin/login` | Trang đăng nhập admin |
| `/admin` | Dashboard thống kê (4 stat card) |
| `/admin/users` | Danh sách user + phân trang + gán/thu hồi role |

Tất cả route `/admin/*` nằm **ngoài** `MarketingSite` layout — không có Navbar/Footer marketing. Truy cập `/admin` hoặc `/admin/users` khi chưa có session → redirect về `/admin/login`.

### 7.2 Auth flow

```
Browser              Vercel Proxy          Backend API
  |                       |                     |
  |-- POST /api/admin-login (email, pwd) ------->|
  |                       |-- POST /api/v1/auth/login -->|
  |                       |<-- { accessToken, refreshToken, ... } --|
  |<-- { accessToken, refreshToken, ... } --------|
  |   (lưu vào sessionStorage)
  |
  |-- GET /api/admin-stats (Authorization: Bearer <token>) -->|
  |                       |-- GET /api/v1/admin/stats ------>|
  |                       |<-- { totalUsers, ... } ----------|
  |<-- { totalUsers, ... } ----------------------------------------|
```

Token lưu trong **`sessionStorage`** (không phải `localStorage`):
- Tự xóa khi đóng tab hoặc trình duyệt
- Không persist qua session mới

### 7.3 Vercel serverless proxy (5 function)

| File | Method | Backend endpoint |
|---|---|---|
| `api/admin-login.js` | POST | `POST /api/v1/auth/login` |
| `api/admin-stats.js` | GET | `GET /api/v1/admin/stats` |
| `api/admin-users.js` | GET | `GET /api/v1/admin/users?page=&size=` |
| `api/admin-role.js` | POST / DELETE | `POST/DELETE /api/v1/admin/users/{userId}/roles/{roleId}` |
| `api/admin-logout.js` | POST | `POST /api/v1/auth/logout` |

Tất cả function: đọc `HELPID_BACKEND_URL` từ `process.env.*`. **Không** để `HELPID_BACKEND_URL` vào Vite bundle hoặc biến `VITE_*`.

### 7.4 File TypeScript / component cần tạo

| File | Vai trò |
|---|---|
| `helper-id/lib/adminAuth.ts` | `login()`, `logout()`, `getAdminToken()`, `isAdminLoggedIn()`, `getAdminUserId()` |
| `helper-id/lib/adminApi.ts` | `getStats()`, `getUsers()`, `assignRole()`, `revokeRole()` — attach Authorization header từ sessionStorage |
| `helper-id/components/admin/AdminLoginPage.tsx` | Form đăng nhập — loading state, error text, redirect sau login |
| `helper-id/components/admin/AdminRoute.tsx` | Protected route wrapper — redirect `/admin/login` nếu không có session |
| `helper-id/components/admin/AdminLayout.tsx` | Header "HelpID Admin" + nút Logout |
| `helper-id/components/admin/AdminDashboardPage.tsx` | 4 stat card, loading skeleton, error state + Retry |
| `helper-id/components/admin/AdminUsersPage.tsx` | Bảng user, Next/Prev, Grant/Revoke per-row với loading state |

### 7.5 sessionStorage keys

| Key | Giá trị |
|---|---|
| `helpid_admin_access_token` | access token JWT |
| `helpid_admin_refresh_token` | refresh token |
| `helpid_admin_expires_at` | ISO timestamp `accessTokenExpiresAtUtc` |
| `helpid_admin_user_id` | userId (`sub` claim) — dùng để disable nút Revoke cho account đang đăng nhập |

### 7.6 Security constraints

| Constraint | Chi tiết |
|---|---|
| No backend URL in bundle | `HELPID_BACKEND_URL` chỉ trong `process.env.*` của serverless function |
| sessionStorage only | Token không lưu vào `localStorage`, không embed vào URL |
| No token logging | Token không được log ra `console.*` ở bất kỳ đâu |
| noindex | `vercel.json` thêm `X-Robots-Tag: noindex, nofollow, noarchive` cho source `/admin/(.*)` và `/api/admin(.*)`. Component login và layout có `<meta name="robots" content="noindex">` |
| 401/403 handling | `adminApi.ts` nhận 401/403 từ proxy → clear `sessionStorage` → redirect về `/admin/login` |
| Logout revoke | `logout()` gọi `POST /api/admin-logout` (forward token lên backend `/api/v1/auth/logout`) **trước** khi clear `sessionStorage` |
| Self-revoke protection | Nút Revoke bị disable cho row có `userId == getAdminUserId()` |
| Input validation | `admin-role.js`: validate `roleId` ∈ `["role_user","role_admin"]`, `userId` tối đa 64 ký tự |
| Timeout | Tất cả serverless function dùng `AbortSignal.timeout(10_000)` |
| No health data | Proxy không log và không trả `allergies`, `medicalNotes`, `emergencyContacts` |
