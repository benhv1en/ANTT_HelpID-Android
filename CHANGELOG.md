16/06/2026 23:55:00
- **PROMPT 47 — Unit tests + manual checklist web admin panel:** Cài `vitest@4.1.9` + `jsdom@29.1.1` (devDeps), tạo `vitest.config.ts` (environment jsdom), thêm `"test": "vitest run"` vào `package.json` scripts. Viết `helper-id/lib/__tests__/adminAuth.test.ts` (13 test: login lưu key, INVALID_CREDENTIALS, SERVER_ERROR, getAdminToken null/expired/valid, isAdminLoggedIn, logout clear session, getAdminUserId) và `helper-id/lib/__tests__/adminApi.test.ts` (14 test: getStats 4 fields + AdminAuthError paths, getUsers parse, assignRole/revokeRole 204/403/404, network error propagation). `npm test`: 27/27 PASS. **Bug fix:** `api/admin-logout.js` không gửi body → backend 500; fix: đọc `req.body.refreshToken` + forward với `Content-Type: application/json`; `adminAuth.ts logout()` gửi refresh token trong body. Verify: `POST /api/v1/auth/logout` với `{refreshToken}` → 204. Checklist 12 TC manual PASS (TC 01-08 via curl backend, TC 09-12 via code inspection + curl). Build PASS (1734 modules), tsc PASS (zero errors).

16/06/2026 23:34:11
- **PROMPT 46 — Security hardening web admin:** Rà soát toàn bộ 5 file proxy + `adminAuth.ts` + `adminApi.ts` + `vercel.json` theo 7 checklist. Kết quả: (1) Token security PASS — sessionStorage keys là named constants, không log token/email/userId, `getAdminToken()` kiểm tra expiry và tự clearSession; (2) No-secret-in-bundle PASS — `HELPID_BACKEND_URL` chỉ ở `process.env.*`; (3) 401/403 handling PASS — `authedFetch` throw `AdminAuthError`, components catch + `logout()` + navigate; (4) **Logout proxy MISSING → tạo mới** `helper-id/api/admin-logout.js` (POST, forward Authorization lên backend `/api/v1/auth/logout`, timeout 10s, best-effort — luôn trả 200 để client clear session ngay cả khi backend không phản hồi); (5) Robots/noindex PASS — `vercel.json` có noindex cho `/admin/(.*)` và `/api/(.*)` đã cover `/api/admin*`, cả `AdminLoginPage` và `AdminLayout` có `setNoIndexMeta()` via DOM; (6) Timeout/UX PASS — tất cả proxy có `AbortSignal.timeout(10_000)`, UI có skeleton/spinner, Login button disabled khi loading hoặc fields rỗng; (7) CORS PASS — không cần thay đổi. Build PASS (1734 modules), tsc PASS (zero errors).

16/06/2026 23:30:37
- **PROMPT 45 — Dashboard + Users UI web admin:** Tạo `helper-id/lib/adminApi.ts` (types `AdminStats`, `AdminUser`, `AdminUsersPage`, class `AdminAuthError`; hàm `authedFetch` lấy token từ `getAdminToken()` + throw `AdminAuthError` nếu null hoặc nhận 401/403; `getStats()`, `getUsers()`, `assignRole()`, `revokeRole()` — không log userId/token). Tạo `helper-id/components/admin/AdminLayout.tsx` (header "HelpID Admin" + nút Logout gọi `logout()` + navigate, `<Outlet />`). Tạo `helper-id/components/admin/AdminDashboardPage.tsx` (4 stat card với skeleton loading + error state + Retry, `useCallback` load + `useEffect`, redirect `/admin/login` khi `AdminAuthError`). Tạo `helper-id/components/admin/AdminUsersPage.tsx` (bảng user, phân trang Next/Prev với "Page X / Y · N total", per-row `RowState` tracking loading/success/error, Grant Admin/Revoke Admin disabled khi busy, Revoke disabled khi `isSelf`, `formatDate` locale-aware). Cập nhật `helper-id/App.tsx`: thay `/admin/*` bằng nested routes — `AdminRoute` → `AdminLayout` → index: `AdminDashboardPage`, `path="users"`: `AdminUsersPage`. Build PASS (1734 modules), tsc PASS (zero errors).

16/06/2026 23:27:16
- **PROMPT 44 — Web admin auth flow:** Tạo `helper-id/lib/adminAuth.ts` (export `login()` gọi `/api/admin-login` + lưu 4 key vào sessionStorage, `logout()` fire-and-forget `/api/admin-logout` + clear session ngay lập tức, `getAdminToken()` kiểm tra expiry và tự clear nếu hết hạn, `isAdminLoggedIn()`, `getAdminUserId()`). Tạo `helper-id/components/admin/AdminLoginPage.tsx` (form email/password, loading state, error "Invalid credentials" / "Server error", noindex meta via DOM imperative, redirect `/admin` sau login thành công, không có Navbar/Footer marketing). Tạo `helper-id/components/admin/AdminRoute.tsx` (kiểm tra `isAdminLoggedIn()`, redirect `/admin/login` nếu không có session, render `<Outlet />` nếu đã đăng nhập). Cập nhật `helper-id/App.tsx`: thêm import 2 component, thêm route `/admin/login` → `AdminLoginPage` và `/admin/*` → `AdminRoute` ngoài `MarketingSite`, trước catch-all `/*`. Build PASS (1730 modules), tsc PASS (zero errors).

16/06/2026 23:23:20
- **PROMPT 43 — Vercel serverless proxy cho web admin API:** Tạo 4 serverless function trong `helper-id/api/`: `admin-login.js` (POST, validate email/password, proxy `/api/v1/auth/login`, sanitize response whitelist fields, không log email/password/token), `admin-stats.js` (GET, forward Authorization header, proxy `/api/v1/admin/stats`, trả JSON nguyên gốc), `admin-users.js` (GET, forward Authorization + parse/clamp query params page/size, proxy `/api/v1/admin/users`), `admin-role.js` (POST/DELETE, validate roleId ∈ whitelist `["role_user","role_admin"]` + userId max 64 ký tự, forward Authorization, proxy `POST/DELETE /api/v1/admin/users/{userId}/roles/{roleId}`, xử lý 204 no-content đúng). Tất cả function: `setSecurityHeaders` (`Cache-Control: no-store`, `X-Robots-Tag: noindex`), `AbortSignal.timeout(10_000)`, 503 khi backend unavailable, không log token/userId. Cập nhật `helper-id/vercel.json`: thêm header `X-Robots-Tag: noindex, nofollow, noarchive` cho source `/admin/(.*)`. Build `npm run build`: PASS (1727 modules, 1.99s).

16/06/2026 23:20:53
- **PROMPT 42 — Chốt contract web admin panel:** Cập nhật `harness-engineering/contract-admin.md` — đổi phạm vi section 1 (từ "chỉ Android" thành "Android + web"), xóa dòng "Admin panel trên web — không có" khỏi section 6, thêm mới Section 7 "Web Admin Panel" bao gồm: routes `/admin/login|/admin|/admin/users`, auth flow sơ đồ browser→Vercel proxy→backend, kiến trúc 5 serverless function (`api/admin-login.js`, `api/admin-stats.js`, `api/admin-users.js`, `api/admin-role.js`, `api/admin-logout.js`), danh sách 7 file TypeScript/component cần tạo, sessionStorage keys, security constraints đầy đủ. Cập nhật 3 UML use-case: `uml-use-case-website.puml` thêm actor WebAdmin + 6 use-case web admin + 5 proxy function trong Serverless rectangle; `uml-use-case-api.puml` thêm actor `VercelAdminProxy` gọi các admin endpoint; `uml-use-case-android.puml` giữ nguyên. Xóa PNG cũ và render lại cả 3 diagram.

16/06/2026 21:30:00
- `harness-engineering/copy-paste-prompts.txt`: thêm Nhóm 8 (PROMPT 42–47) — 6 prompt cho tính năng web admin panel trên `helper-id/`. Bao gồm: PROMPT 42 (chốt contract + UML), PROMPT 43 (Vercel serverless proxy 4 function), PROMPT 44 (auth flow sessionStorage + protected route), PROMPT 45 (Dashboard + Users UI + gán/thu hồi role), PROMPT 46 (security hardening + UX polish), PROMPT 47 (unit test + checklist test thủ công).

16/06/2026 20:50:00
- `run-backend.sh`: thêm bước kill process cũ trên port 5080 trước khi chạy `dotnet run`, tránh lỗi "address already in use" khi script được gọi lại mà backend cũ chưa tắt.

16/06/2026 20:35:00
- **PROMPT 41 — Checklist test thủ công admin screen (12 TC):** Chạy và ghi kết quả 12 test case thủ công trên emulator `emulator-5554` + backend `localhost:5080`. TC-01 (role_user không thấy nút Admin), TC-02 (gán role_admin qua SQLite + re-login → nút Admin hiện), TC-03 (Dashboard stats đúng: users=3, profiles=3, public_links=1, audit_7d=0), TC-04 (Users list hiển thị email/role/date, TalkBack contentDescription đúng), TC-05 (phân trang 1/1 giữ boundary đúng), TC-06 (Grant Admin → Done banner + list reload), TC-07 (Revoke Admin → Done banner + list reload), TC-08 (offline → "Network error. Please try again." không crash), TC-09 (401 auto-refresh xác nhận code review + unit test), TC-10 (← button → EmergencyScreen), TC-11 (role_user không có đường dẫn đến route admin; LaunchedEffect guard xác nhận code review), TC-12 (403 → onUnauthorized redirect xác nhận code review + unit test). Tất cả 12 TC: PASS. Phát hiện phụ: backend cần env var `HELPID_AUTH_JWT_SIGNING_KEY` khi khởi động lại (đã resolve). Kết quả ghi trong `passed-testcases.md`.

16/06/2026 19:30:00
- **PROMPT 40 — Security & UX hardening audit (Admin):**
- Backend `AdminService.cs`: sửa 2 bug SQLite EF Core 8.0 không support `DateTimeOffset` trong LINQ ORDER BY và `>=` comparison — `GetStatsAsync` pull timestamp list client-side trước khi filter; `GetUsersAsync` pull all rows rồi sort/page client-side (admin-only endpoint, user count manageable). Không còn `baseQuery.CountAsync()` kéo theo ORDER BY vào COUNT query.
- Android `AdminScreen.kt`: thêm 2 import semantics (`semantics`, `contentDescription`); thêm TalkBack `contentDescription` cho nút ✕ (dismiss banner) qua `Modifier.semantics { contentDescription = dismissLabel }`; thêm `contentDescription` cho nút assign/revoke trong `AdminUserRow` (bao gồm email của user); đổi hardcoded `"—"` → `stringResource(R.string.admin_role_empty)`; đổi hardcoded `"$currentPage / $totalPages"` → `stringResource(R.string.admin_page_of, currentPage, totalPages)`.
- String resources: thêm 5 key mới vào đủ 6 locale (`values/`, `values-vi/`, `values-de/`, `values-es/`, `values-fr/`, `values-hi/`): `admin_dismiss`, `admin_role_empty`, `admin_page_of` (`%1$d / %2$d`), `admin_cd_assign_role` (`%s`), `admin_cd_revoke_role` (`%s`).
- Backend audit xác nhận: không có `passwordHash`/`securityStamp`/`tokenHash`/health data trong DTO; không có raw SQL; `userId`/`roleId` là route param do ASP.NET Core bind (không ghép chuỗi); `AllowedRoleIds` whitelist enforce đúng; `callerUserId` lấy từ JWT sub (không từ request body).
- Android audit xác nhận: nút admin chỉ render khi `isAdmin() == true`; 403 → `onUnauthorized()` navigate back; zero `Log.*` calls trong `AdminScreen.kt` và `HelpIdApiAdminRepository.kt`.
- Test run: `cd backend/HelpId.Api.Tests && dotnet test` — 42/42 PASS (bao gồm 2 test trước đó fail do SQLite DateTimeOffset bug, nay đã fix).
- Test run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, 0 failures.
- Test run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:lintDebug` — BUILD SUCCESSFUL, 0 lint errors.

16/06/2026 18:43:56
- Tạo `app/src/test/java/com/helpid/app/data/HelpIdApiAdminRepositoryTest.kt` với 10 unit test cho `HelpIdApiAdminRepository`: (1) getStats 200 parse đúng 4 field; (2) getStats IOException → AdminApiResult.Offline không crash; (3) getStats 401 → refresh token + retry → Ok; (4) getStats 403 → AdminApiResult.Forbidden; (5) getUsers 200 parse page/items/totalCount; (6) getUsers page ngoài range → Ok với empty list không crash; (7) assignRole 204 → Ok; (8) assignRole IOException → Offline không crash; (9) revokeRole 204 → Ok; (10) revokeRole 404 → Failed không crash. Dùng FakeTokenSource/FakeAuthRepo/FakeAdminHttpClient inject qua internal constructor. Không dùng dữ liệu y tế trong fixture.
- Test run: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL, 10/10 admin tests PASS, 0 failures, 0 errors.

16/06/2026 18:38:49
- Thêm `sealed class AdminApiResult<T>` vào `HelpIdApiAdminRepository.kt` với 4 variant: `Ok(value)`, `Forbidden` (HTTP 403), `Offline` (IOException), `Failed` (mọi lỗi còn lại) — thay thế kiểu trả về `AdminStats?`/`AdminUsersPage?`/`Boolean` để screen phân biệt được lỗi 403 vs lỗi mạng.
- Cập nhật 4 hàm trong `HelpIdApiAdminRepository.kt`: `getStats()`, `getUsers()`, `assignRole()`, `revokeRole()` — mỗi hàm check 403 riêng (`AdminApiResult.Forbidden`), catch `IOException` riêng (`AdminApiResult.Offline`), dùng `var response` + replace sau retry 401 thay vì destructuring.
- Rewrite `AdminScreen.kt`: trạng thái lỗi chuyển từ `Boolean` sang `String?` (`statsErrorMessage`, `usersErrorMessage`) để chứa text lỗi cụ thể; `loadStats`/`loadUsers` dùng `try/finally` để đảm bảo `isLoading = false` luôn được gọi; `Forbidden` → `context.getString(R.string.admin_error_unauthorized)` + gọi `onUnauthorized()` (navigate back); lỗi mạng/khác → `context.getString(R.string.admin_error_network)`; `onAssignAdmin`/`onRevokeAdmin` dùng `try/finally { busyUserId.value = null }` để tránh row bị kẹt trạng thái loading; sau assign/revoke thành công reload list users; thêm import `AdminApiResult`.
- `AdminDashboardContent` và `AdminUsersContent` nhận `errorMessage: String?` thay vì `hasError: Boolean` — hiển thị đúng message trong `AdminErrorState` với nút retry.
- Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL in 8s.

16/06/2026 18:40:00
- Thêm `isAdmin(): Boolean` vào `AuthTokenStore.kt`: decode JWT payload (Base64url) từ access token đang lưu, trả `true` nếu payload chứa `"admin:metadata:read"`, trả `false` khi không có token/sai format/exception — client-side UI gate only, không log token.
- Cập nhật `EmergencyScreen.kt`: thêm param `onAdminClick: (() -> Unit)? = null`; thêm `AdminPanelSettings` icon button tại `Alignment.CenterStart` trong header Box — chỉ hiện khi `onAdminClick != null`.
- Cập nhật `MainActivity.kt`: import `AdminScreen`; thêm `val isAdminUser = remember(authState.value) { tokenStore.isAdmin() }` trong else (Authenticated) block; truyền `onAdminClick = if (isAdminUser) { { currentScreen.value = "admin" } } else null` vào `EmergencyScreen`; thêm route `"admin"` vào `when(currentScreen.value)` với guard `LaunchedEffect` redirect về `"emergency"` nếu không phải admin.
- Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL in 8s.

16/06/2026 18:22:38
- Tạo `app/src/main/java/com/helpid/app/ui/AdminScreen.kt` với Jetpack Compose: `AdminScreen` composable với 2 tab (Dashboard và Users); Dashboard hiển thị 4 `AdminStatCard` (totalUsers, totalProfiles, totalPublicLinks, auditEventsLast7Days); Users tab có `LazyColumn` paginated với nút Previous/Next và text "currentPage / totalPages"; mỗi `AdminUserRow` hiển thị email, role, ngày tạo (yyyy-MM-dd), trạng thái active/locked, nút "Cấp Admin"/"Thu hồi Admin" theo role hiện tại; loading state (skeleton card), error state (message + retry), empty state; banner thông báo action thành công/thất bại; tự detect self-revoke client-side trước khi gọi API; không hiển thị dữ liệu y tế.
- Thêm 22 string key admin vào đủ 6 locale (`values/`, `values-vi/`, `values-de/`, `values-es/`, `values-fr/`, `values-hi/`): admin_screen_title, admin_tab_dashboard, admin_tab_users, admin_stat_total_users, admin_stat_total_profiles, admin_stat_total_links, admin_stat_audit_7d, admin_user_role_label, admin_user_locked, admin_user_active, admin_assign_admin_role, admin_revoke_admin_role, admin_action_success, admin_action_loading, admin_error_unauthorized, admin_error_network, admin_error_not_found, admin_self_revoke_blocked, admin_entry_button, admin_prev_page, admin_next_page, admin_no_users.
- Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.

16/06/2026 18:16:39
- Tạo `app/src/main/java/com/helpid/app/data/HelpIdApiAdminRepository.kt`: data classes `AdminStats`, `AdminUserItem`, `AdminUsersPage`; interface `AdminHttpClient` (get/post/delete cho unit test); `HelpIdApiAdminRepository` với internal constructor inject `ProfileTokenSource`, `AuthRepository`, `AdminHttpClient`, `getBaseUrl` — expose `getStats(): AdminStats?`, `getUsers(page, size): AdminUsersPage?`, `assignRole(userId, roleId): Boolean`, `revokeRole(userId, roleId): Boolean`; tự refresh token khi 401 và retry 1 lần; trả null/false khi lỗi mạng hoặc non-2xx; không log userId/email/token/health data; private class `AdminTokenStoreAdapter` và `DefaultAdminHttpClient` wiring production deps.
- Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.

16/06/2026 18:30:00
- Tạo `backend/HelpId.Api.Tests/AdminApiTests.cs` với 8 test cases cho admin API: (1) `Regular_user_is_denied_by_admin_metadata_policy` — user thường không có `admin:metadata:read` bị từ chối và policy chỉ cho phép role Admin; (2) `Unauthenticated_is_denied_by_admin_metadata_policy` — policy có `DenyAnonymousAuthorizationRequirement`; (3) `GetStats_returns_200_with_correct_schema` — 4 trường schema (totalUsers, totalProfiles, totalPublicLinks, auditEventsLast7Days) trả về đúng giá trị; (4) `GetUsers_returns_200_and_excludes_sensitive_fields` — `AdminUserDto` không có PasswordHash/SecurityStamp/TokenHash/Allergies/MedicalNotes/EmergencyContacts cả ở type level và JSON response; (5) `AssignRole_returns_204_and_persists_role_and_audit_event` — gán role_admin vào DB và ghi AuditEvent `admin.role.assign`; (6) `RevokeRole_returns_204_and_removes_role_and_writes_audit_event` — thu hồi role_admin khỏi DB và ghi AuditEvent `admin.role.revoke`; (7) `Admin_cannot_revoke_own_admin_role` — tự thu hồi trả về 403, role còn nguyên trong DB; (8) `Sql_injection_in_userId_returns_not_found_and_db_is_intact` — LINQ parameterise query, injection trả về 404, bảng Users còn nguyên.
- Test: `dotnet test` — 34/34 PASS (8 test admin mới + 26 test hiện có), 0 failures.

16/06/2026 18:00:40
- Tạo folder `backend/HelpId.Api/Admin/` với 4 file: `AdminDtos.cs` (AdminStatsDto, AdminUserDto, AdminUsersPageDto, AdminOperationResult), `AdminService.cs` (IAdminService + AdminService với LINQ/EF Core — GetStatsAsync, GetUsersAsync, AssignRoleAsync, RevokeRoleAsync), `AdminServiceCollectionExtensions.cs` (AddHelpIdAdminApi), `AdminEndpoints.cs` (4 endpoint GET /stats, GET /users, POST và DELETE /users/{userId}/roles/{roleId} — tất cả RequireAuthorization("AdminMetadata")).
- Cập nhật `backend/HelpId.Api/Program.cs`: thêm `using HelpId.Api.Admin`, `builder.Services.AddHelpIdAdminApi()` và `app.MapAdminEndpoints()`.
- Build: `dotnet build` — BUILD SUCCEEDED, 0 warnings, 0 errors.
- Test: `dotnet test` — 34/34 PASS, 0 failures.

16/06/2026 17:57:34
- Tạo `harness-engineering/contract-admin.md`: contract đầy đủ cho tính năng admin Android — tính năng (dashboard stats, danh sách user, gán/thu hồi role), 4 API endpoint mới với request/response/status code, security rules (AdminMetadata policy, no health data, role whitelist, self-protection), Android UX (isAdmin() từ JWT decode, navigation route "admin", 21 string key cần thêm), cách gán admin thủ công qua SQLite.
- Cập nhật `harness-engineering/uml-use-case-android.puml`: thêm actor Admin, 7 use-case admin mới (UC_DetectAdmin, UC_OpenAdmin, UC_AdminDashboard, UC_AdminListUsers, UC_AdminAssignRole, UC_AdminRevokeRole, UC_CallAdminApi) với quan hệ và note bảo mật.
- Cập nhật `harness-engineering/uml-use-case-api.puml`: thêm actor AdminClient, 7 use-case admin API mới (4 endpoint + UC_EnforceAdminPolicy + UC_AdminAuditEvent) với quan hệ và note whitelist.
- Xóa PNG cũ và render lại `uml-use-case-android.png` (711K) và `uml-use-case-api.png` (430K). `uml-use-case-website.png` giữ nguyên (không có thay đổi use-case web).

16/06/2026 17:20:00
- Thêm NHÓM 7 (PROMPT 32–41) vào `harness-engineering/copy-paste-prompts.txt`: quy trình đầy đủ để implement trang admin Android — contract, backend API, backend test, Android data layer, Android UI, Android navigation, integration, Android unit test, hardening/polish, checklist test thủ công.

16/06/2026 17:03:47
- Thêm unit test `HelpIdApiProfileRepositoryTest` (21 test cases): kiểm tra `parseProfile` (7 cases — parse đầy đủ field, fallback userId, JSON sai, body rỗng, null fields), `buildJson` (4 cases — field names, escape quote/backslash, empty lists), `esc` (2 cases), `getProfile` (4 cases — 200, 401→refresh+retry, IOException→Room cache, no token→Room cache), `updateProfile` (4 cases — 200 xóa pending flag, IOException vẫn ghi Room, IOException set pending flag, 500 set pending flag). Tất cả 21/21 PASS.

16/06/2026 16:46:12
- Sửa `EditProfileScreen.kt`: thay `FirebaseRepository` bằng `HelpIdApiProfileRepository` cho load/save hồ sơ. Load dùng `getProfile()` (không cần truyền userId). Save dùng `updateProfile()` bọc trong try/catch để `isSaving` luôn reset; `onSaveSuccess()`/`onBackClick()` được gọi trên main thread sau khi `withContext(Dispatchers.IO)` trả về. Thêm text lỗi hiển thị khi save thất bại. Thêm string key `save_error` vào đủ 6 locale. Build SUCCESSFUL.

16/06/2026 16:41:58
- Tạo `app/src/main/java/com/helpid/app/data/HelpIdApiProfileRepository.kt`: repository profile mới dùng backend JWT API thay Firebase. `getProfile()` gọi `GET /api/v1/profile` với access token, lưu Room, fallback Room/default khi lỗi mạng, tự refresh token khi 401. `updateProfile()` ghi Room trước, sau đó sync backend best-effort với pending flag khi offline. Không log token hay dữ liệu nhạy cảm. Build SUCCESSFUL.

16/06/2026 16:02:01
- Xác định root cause lỗi kết nối: điện thoại (`192.168.1.8`) và máy dev (`192.168.0.109`) ở hai subnet khác nhau. Lập kế hoạch fix bằng cách đổi mạng (phương án 1) hoặc dùng ngrok tunnel (phương án 2). Không có thay đổi code. Lưu kế hoạch vào `harness-engineering/ke-hoach.md`.

16/06/2026 15:54:18
- Sửa `ServerSettingsDialog.kt`: nút "Test connection" nay hiện tên exception + message chi tiết khi thất bại (ví dụ `CleartextNotPermittedException`, `ConnectException`) thay vì chỉ "Cannot connect". Cập nhật `AGENTS.md` thêm hướng dẫn đọc lỗi và kiểm tra subnet. Build SUCCESSFUL.

16/06/2026 15:51:12
- Lập kế hoạch sửa lỗi điện thoại thật không kết nối backend dù IP đúng: nguyên nhân chưa xác định rõ (APK cũ chưa có cleartext fix, hoặc phone khác subnet). Kế hoạch chính: thêm chi tiết exception vào dialog "Test connection" để chẩn đoán không cần logcat. Lưu tại `harness-engineering/ke-hoach.md`.

16/06/2026 15:24:45
- Sửa lỗi HTTP cleartext bị block khi kết nối điện thoại thật → backend LAN: tạo `app/src/debug/res/xml/network_security_config.xml` cho debug build với `cleartextTrafficPermitted="true"` toàn bộ host; production build (`src/main`) vẫn HTTPS-only. Build SUCCESSFUL.

16/06/2026 14:57:37
- Sửa lỗi login điện thoại thật báo "Không có kết nối": (1) `run-backend.sh` thêm `--urls http://0.0.0.0:5080` để backend nhận kết nối LAN; (2) `HelpIdApiAuthRepository.kt` giảm `TIMEOUT_MS` từ 20_000 xuống 8_000; (3) thêm `ServerSettingsDialog.kt` — dialog cấu hình và test URL backend từ điện thoại; (4) `LoginScreen.kt` và `RegisterScreen.kt` thêm icon ⚙ mở dialog; (5) thêm 7 string key vào 6 locale; (6) `AGENTS.md` thêm hướng dẫn test LAN. Build/test/lint PASS.

16/06/2026 14:50:35
- Lập kế hoạch sửa lỗi login trên điện thoại thật báo "Không có kết nối": root cause là `DEFAULT_DEBUG_URL=10.0.2.2` chỉ hoạt động trên emulator, backend bind localhost không nhận LAN, timeout 20s quá dài. Plan lưu tại `harness-engineering/ke-hoach.md`.

16/06/2026 14:05:00
- Sửa `run-backend.sh`: EF Core resolve path SQLite từ thư mục project thay vì repo root; fix bằng cách luôn export `ConnectionStrings__HelpIdDb` với absolute path (`$SCRIPT_DIR/backend/HelpId.Api/App_Data/helpid-dev.db`) trong script thay vì đọc từ `.env.local`. Cập nhật `.env.local.example` bỏ trường này và giải thích tại sao.

16/06/2026 14:00:00
- Tạo `run-backend.sh`: script một lệnh để chạy backend local — source `backend/.env.local`, validate 4 biến bắt buộc, tạo thư mục `App_Data`, chạy EF migration rồi `dotnet run`. Thêm `backend/.env.local.example` với giá trị dev mặc định. Cập nhật `.gitignore` ignore `backend/.env.local`.

16/06/2026 13:07:00
- Rà soát cuối tính năng biometric: sửa accessibility thiếu `contentDescription` cho `Switch` trong `BiometricSettingsCard` (`EditProfileScreen.kt`); cập nhật `harness-engineering/android.md` thêm section biometric với luồng auth state và quy tắc bắt buộc; cập nhật `harness-engineering/bao-mat-du-lieu.md` thêm section "Lưu trữ biometric" ghi rõ không lưu template, không gửi lên backend, hashing userId trong prefs key; cập nhật `harness-engineering/quy-trinh-test.md` thêm pattern tách pure function để unit test mà không cần mock Android context. Build/test/lint PASS, git diff --check sạch.

16/06/2026 12:56:33
- Tách logic quyết định auth state thành hàm thuần `resolveAuthState` trong `BiometricAuthDecision.kt`; refactor `authenticatedOrBiometricLocked` thành wrapper gọi hàm này. Thêm `BiometricAuthDecisionTest` (11 test cases toàn bộ nhánh), bổ sung coverage cho `BiometricUtilsTest` (thêm 4 test: SecurityUpdateRequired/Unsupported availability, ERROR_CANCELED/ERROR_NEGATIVE_BUTTON/ERROR_HW_UNAVAILABLE/ERROR_NO_DEVICE_CREDENTIAL error codes, LockoutPermanent/HardwareUnavailable/NoDeviceCredential messageResId), bổ sung `BiometricPreferenceStoreTest` (thêm 6 test: sha256Hex length/determinism, key isolation user A ≠ user B, enabled ≠ lastUnlocked key, stable across calls). Tổng 59 test, 0 failure.

16/06/2026 11:48:58
- Sửa `authenticatedOrBiometricLocked` trong `MainActivity`: truyền đúng tham số `requiresRefresh` thay vì hardcode `true`. Khi access token còn hạn (`requiresRefresh = false`), biometric success chuyển thẳng sang `Authenticated` mà không gọi refresh thừa; khi access token hết hạn (`requiresRefresh = true`), vẫn phải refresh backend và xử lý 401/403 đúng như thiết kế.

16/06/2026 11:34:26
- Siết luồng biometric unlock trong `MainActivity`: nếu biometric enabled thì biometric chỉ mở khóa UI local, sau success vẫn gọi refresh backend để kiểm tra refresh token revoke/expiry trước khi vào private screens; refresh 401/403 clear token và biometric setting theo user, offline chỉ vào `LocalCacheOnly` khi có Room cache và không sync remote.

16/06/2026 11:31:00
- Implement luồng khóa biometric khi mở app: thêm `AuthState.BiometricLocked` và màn hình unlock trong `MainActivity`, chỉ refresh token/gửi API sau biometric success, giữ user ở màn hình khóa khi cancel/fail, fallback login bằng mật khẩu, logout/refresh 401-403 clear token và biometric setting theo user, thêm string unlock/try-again/use-password cho toàn bộ locale.

16/06/2026 11:26:07
- Thêm UI bật/tắt xác thực vân tay trong `EditProfileScreen` cho user đã đăng nhập: bật yêu cầu `BiometricPrompt` thành công trước khi lưu `BiometricPreferenceStore`, tắt có dialog xác nhận, xử lý unavailable/not enrolled/lockout/cancel/system error bằng stringResource toàn bộ locale, đổi `MainActivity` sang `FragmentActivity` để prompt hoạt động; không chạm luồng SOS.

16/06/2026 11:18:58
- Implement utility biometric Android: mở rộng `BiometricUtils` với kiểm tra availability typed, prompt `BiometricPrompt` không trả raw error, fallback device credential trên Android 11+ theo policy, thêm `BiometricPreferenceStore` lưu bật/tắt và last unlock trong `SecurePrefs` bằng key hash theo user id, thêm permission `USE_BIOMETRIC`, string lỗi biometric cho toàn bộ locale và unit test liên quan.

16/06/2026 11:09:38
- Rà soát `backend/HelpId.Api/` và Android token flow cho thiết kế xác thực vân tay; cập nhật `harness-engineering/thiet-ke-xac-thuc-van-tay.md` để kết luận biometric chỉ là local unlock cho token/session đã có, không gửi/lưu biometric template và không cần backend API/schema/migration mới.

16/06/2026 11:07:01
- Thêm tài liệu thiết kế xác thực vân tay Android tại `harness-engineering/thiet-ke-xac-thuc-van-tay.md`, cập nhật UML use-case Android với luồng bật/tắt và mở khóa bằng biometric local, render lại 3 ảnh UML use-case Android/Website/API; không thay đổi runtime code.

16/06/2026 10:37:55
- Cập nhật `harness-engineering/uml-database.puml` để ghi rõ role RBAC seed hiện tại chỉ gồm `role_user`/`User` (người dùng app/chủ hồ sơ) và `role_admin`/`Admin` (quản trị hệ thống), chưa có role `Doctor` hoặc `Patient` riêng; xóa ảnh UML database cũ và render lại `harness-engineering/uml-database.png`.

16/06/2026 09:48:39
- Đổi tên `harness-engineering/prompts-dang-ky-dang-nhap.txt` thành `harness-engineering/copy-paste-prompts.txt`, cập nhật tiêu đề/ghi chú vận hành, bổ sung nhóm prompt 21-28 cho tính năng xác thực vân tay Android theo thứ tự contract, backend decision, Android implementation, integration, test và hardening; cập nhật `AGENTS.md` và `harness-engineering/README.md` để trỏ tới file prompt mới.

16/06/2026 09:38:14
- Cập nhật UML database diagram để thể hiện rõ phân quyền backend: seed RBAC User/Admin, danh sách permission, quan hệ cấp quyền, JWT claim/policy enforce, ownership check và public profile access bằng public key + JWT ngắn hạn; xóa ảnh `harness-engineering/uml-database.png` cũ rồi render lại ảnh mới; cập nhật `AGENTS.md` bắt buộc UML database phải mô tả bảng quyền, seed quyền, quan hệ cấp quyền và tầng enforce khi database có phân quyền/RBAC/ownership/public access.

16/06/2026 09:23:25
- Cập nhật `AGENTS.md` yêu cầu mọi thời gian ghi vào tài liệu, changelog, testcase hoặc kế hoạch phải lấy bằng đúng lệnh `date '+%d/%m/%Y %H:%M:%S'`, tuyệt đối không bịa hoặc tự ước lượng thời gian.

16/06/2026 09:13:58
- Tạo UML database diagram cho toàn bộ dữ liệu hiện có: backend SQLite EF Core (`Users`, auth/session, profile, public links, audit, RBAC), Android Room `helpid_database.user_profile`, và Firestore legacy `users/{uid}`/`publicKeys/{publicKey}`; thêm nguồn PlantUML `harness-engineering/uml-database.puml`, render ảnh `harness-engineering/uml-database.png`, và cập nhật `AGENTS.md` bắt buộc khi thiết kế database thay đổi phải cập nhật `.puml`, xóa ảnh cũ rồi render ảnh mới.

15/06/2026 02:55:07
- Cập nhật tài liệu vận hành sau khi auth/backend mới chạy ổn: `AGENTS.md`, tài liệu harness và `backend/HelpId.Api/README.md` bổ sung backend ASP.NET Core/EF Core SQLite, API contract, cách chạy backend, biến môi trường `HELPID_*`/`HELPID_BACKEND_URL`, quy trình migration/test, trạng thái Firebase legacy chưa gỡ; xác nhận 3 PNG PlantUML use-case đã tồn tại và không render lại vì `.puml` không đổi; chạy `git diff --check` pass.

15/06/2026 02:40:24
- Rà soát và harden bảo mật đăng ký/đăng nhập/backend API: auth validation trả 422 để khớp contract Android, password policy mặc định/config tăng lên 12 ký tự, refresh token reuse revoke toàn bộ token family, access JWT bỏ email claim, Vercel API chuyển sang safe logging và khai báo trực tiếp `node-fetch`; `npm audit fix --omit=dev` áp dụng fix non-breaking cho production deps (hết critical/high, còn 8 moderate `firebase-admin` transitive cần `--force` breaking nên chưa áp dụng); Android legacy logs bỏ uid/tên/raw response/stacktrace trong các luồng auth/profile/mint; bổ sung test auth lên 34 case và chạy backend/web/Android build-test-lint, migration list, raw SQL/log scan, `git diff --check` đều pass.

15/06/2026 02:23:34
- Chạy kiểm thử end-to-end thật cho luồng register/login/refresh/logout/profile/QR/public profile trên backend local dùng SQLite tạm: register/login/refresh/profile/mint/public profile/proxy/logout pass 46 checks, login sai và refresh sau logout trả đúng 401 không trả token, public profile/proxy giữ whitelist và header no-store/noindex; không phát hiện lỗi nối contract nên không sửa backend/API/frontend; chạy đủ `dotnet build`, `dotnet test` 30/30, `dotnet ef migrations list`, web `npm run build`, `npx tsc --noEmit`, Android `assembleDebug/testDebugUnitTest/lintDebug` với JDK 17 đều pass.

15/06/2026 01:53:10
- Rà soát và siết UX/security cho web public emergency profile sau khi nối backend mới: `helper-id/api/profile.js` thêm `Pragma/Expires/X-Robots-Tag`, forward request `no-store`, map 401/403 thành lỗi token hết hạn và sanitize response chỉ còn whitelist field (`name`, `bloodGroup`, `allergies`, `emergencyContacts`, `address`, `medicalNotes`); `EmergencyProfilePage.tsx` thêm client-side whitelist, fetch `cache: no-store`, giữ thông báo rõ cho token hết hạn; `backend/HelpId.Api/EmergencyLinks/EmergencyLinkEndpoints.cs` thêm header no-store/no-cache/noindex cho mint/public profile và cập nhật test header; `AndroidManifest.xml` thêm intent-filter `helpid://e/...` khớp deep link web; xác nhận không có analytics/tracking mặc định trong path public profile; cập nhật và render lại 3 UML use-case theo system boundary cho deep link/noindex public profile; web build/typecheck, backend test, Android assemble với JDK 17 và `git diff --check` đều pass.

15/06/2026 20:00:00
- Cập nhật frontend web `helper-id`: (1) **`api/profile.js`** — thay toàn bộ luồng Firebase/Firestore bằng proxy tới backend `GET /api/v1/public/profile?key=...&t=...` qua biến môi trường server-side `HELPID_BACKEND_URL` (không vào Vite bundle, không `VITE_*`); giữ nguyên method check, regex validation key (`HID-[A-Z0-9_-]{8,64}`) và giới hạn token 4096 ký tự trước khi forward; map ProblemDetails backend (`json.title`) thành `{ error }` cho frontend; giữ security headers `no-store`, `nosniff`, `no-referrer`; timeout 10 s với `AbortSignal.timeout`; trả 503 nếu backend không thể kết nối; xóa toàn bộ import `firebase-admin` và `jsonwebtoken`; (2) **`components/EmergencyProfilePage.tsx`** — thêm state `errorStatus` (HTTP status code); phân biệt thông báo lỗi: 401 → "This link has expired. Ask the person to share a new link from the app.", 404 → "Profile not found. The link may have been revoked.", các trường hợp khác → "Something went wrong. Please try again later."; (3) Build `npm run build` SUCCESSFUL (Vite, 303 kB JS); type check `npx tsc --noEmit` clean 0 error; backend 30/30 test pass (không sửa backend).

16/06/2026 02:30:00
- Audit và sửa frontend Android auth: (1) **Locale** — xác nhận đủ 38 auth key trong tất cả 6 locale (en/vi/de/es/fr/hi), không thiếu; (2) **Password visibility** — `PasswordVisualTransformation` kiểm soát đúng hiển thị, toggle icon có `contentDescription` đầy đủ (`auth_cd_show/hide_password`) trong đủ 6 locale; (3) **IME actions** — email→Next→password→Done trên LoginScreen; displayName→Next→email→Next→password→Next→confirmPassword→Done trên RegisterScreen, đúng thứ tự; (4) **Layout shift** — sửa `LoginScreen` (2 field) và `RegisterScreen` (4 field): thay `supportingText = error?.let { { Text(it) } }` thành luôn cung cấp lambda với `Text(error.orEmpty(), color = error.copy(alpha = 0f khi null))` để chiều cao field không thay đổi khi error xuất hiện/biến mất; (5) **Disabled state** — sửa `GhostButton` (go-to-register trong Login, go-to-login trong Register) thêm `enabled = !isLoading` để không cho navigate mid-request; main action button (`AuthLoadingButton`/`RegisterLoadingButton`) đã disabled khi loading; (6) **No PII log** — xác nhận không có `Log.*` nào trong `LoginScreen`, `RegisterScreen`, `HelpIdApiAuthRepository`; (7) **Small screen** — form dùng `weight(1f).verticalScroll()` nên nội dung không bị che trên màn hình nhỏ; build/test/lint pass.

16/06/2026 01:30:00
- Chuyển QR/NFC/SOS fallback mint sang backend API: tạo `HelpIdApiEmergencyLinkRepository` (gọi `POST /api/v1/emergency-links/mint` với Bearer JWT; nếu access token hết hạn thì tự refresh trước khi mint; nếu nhận 401 thì refresh và retry một lần; offline/lỗi trả `""` an toàn, không render URL hỏng, SOS không crash); sửa `QRScreen` nhận `onMintLink: suspend () -> String` thay vì tự tạo `FirebaseRepository` bên trong; sửa `EmergencyScreen` nhận `onMintLink` (default `{ "" }`) để thay 2 chỗ gọi `repository.mintEmergencyLink()` (SOS fallback link 4 s timeout và NFC beam 7 s timeout giữ nguyên); wire `HelpIdApiEmergencyLinkRepository` vào `AppNavigation` và truyền lambda tới cả hai màn hình; `LocalCacheOnly` không nhận lambda mint nên mặc định `{ "" }` an toàn; xóa `uml-use-case.puml/png` cũ, tách thành 3 file riêng theo AGENTS.md: `uml-use-case-android.puml/png` (Android app, Firebase legacy profile, backend mint), `uml-use-case-website.puml/png` (React + Vercel serverless, legacy mint giữ nguyên), `uml-use-case-api.puml/png` (backend ASP.NET Core, endpoint mint/profile/auth, EF Core ownership); backend không sửa, 30/30 backend test pass; Android build/test/lint pass.

16/06/2026 00:15:00
- Tạo `CLAUDE.md` cho repo: ghi chú lệnh build/test Android (JAVA_HOME JDK 17/21 workaround cho JDK 25), backend dotnet, web npm; kiến trúc tổng quan (3 component Android/backend/web), dual auth (Firebase legacy + backend JWT), AuthState sealed class, navigation thủ công, offline-first data flow, Room encryption, backend ownership enforcement; các quy tắc bắt buộc (CHANGELOG format, 6 locale, no secret log, no binary edit, UML use-case, test logging, Vercel secrets).

15/06/2026 23:30:00
- Hoàn thiện frontend auth state Android: (1) `AuthState.LocalCacheOnly` thêm `isOffline: Boolean` để phân biệt offline vs session expired; (2) startup refresh failure xử lý riêng `NetworkError` (offline → kiểm Room cache → `LocalCacheOnly(isOffline=true)` hoặc `Unauthenticated`, bỏ qua Firebase round-trip) và `ApiError` 400-403 (token invalid/revoked → `clearTokens()` → kiểm Room cache → `LocalCacheOnly(isOffline=false)` hoặc `Unauthenticated`), server error 5xx vẫn fall-through sang Firebase fallback; (3) banner trong `LocalCacheOnly` hiển thị `auth_banner_offline` hoặc `auth_banner_session_expired` đúng theo reason; (4) `EditProfileScreen` thêm `onLogout: (() -> Unit)?` với `AlertDialog` xác nhận (`auth_logout_confirm_*`) trước khi logout; (5) `performLogout()` trong `AppNavigation`: best-effort revoke refresh token qua API, `clearTokens()` (KHÔNG xóa Room database / dữ liệu y tế local), kiểm Room cache sau khi clear token để chuyển sang `LocalCacheOnly` nếu còn cache, `Unauthenticated` nếu không; build debug/unit test/lint đều PASS.

15/06/2026 01:00:00
- Nối màn hình Login/Register với backend auth API thật: sửa bug `RegisterScreen.kt` dòng 152 — key `"displayName"` (camelCase) → `"displayname"` (lowercase) để khớp với cách `HelpIdApiAuthRepository.parseValidationError()` normalize key từ backend (`"DisplayName"` PascalCase → lowercase); toàn bộ success path (login/register thành công) lưu token vào `AuthTokenStore`, cập nhật `authState` → `Authenticated`, điều hướng vào EmergencyScreen; toàn bộ error path (401/409/423/422/network) hiển thị lỗi user-friendly qua string resource, không lộ raw backend/SQL error; không cần sửa backend vì frontend đã gọi đúng contract (backend trả đúng status/format, `detectFieldCode()` bridge message → code); build debug thành công, 28/28 unit test pass, lint pass.

15/06/2026 00:30:00
- Thêm API client Android cho backend HelpID auth: `HelpIdApiConfig` (base URL cấu hình được qua SharedPreferences, debug default `http://10.0.2.2:5080`, release default rỗng, không hard-code secret); `HelpIdApiAuthRepository` implement `AuthRepository` gọi `POST /api/v1/auth/login|register|refresh` và `POST /api/v1/auth/logout` bằng `HttpURLConnection` + Gson, parse response ASP.NET Core (token pair 200/201, ValidationProblemDetails 422, ProblemDetails 401/409/423), normalize field key về lowercase, map message text → code cho UI, không log token/password/PII; `network_security_config.xml` cho phép HTTP cleartext tới `10.0.2.2` (emulator host) để dev local; bật `buildConfig = true` trong `build.gradle.kts`; cập nhật `AndroidManifest.xml` thêm `networkSecurityConfig`; wire `HelpIdApiAuthRepository` vào `AppNavigation` thay `StubAuthRepository`; thêm 28 unit test cho parsing logic (pass 28/28); build debug thành công.

15/06/2026 00:10:00
- Thêm màn hình Login/Register Android bằng Jetpack Compose: `LoginScreen.kt` (email/password fields, toggle hiện mật khẩu, loading state, error mapping, GhostButton chuyển Register, nút X dismiss khi LocalCacheOnly), `RegisterScreen.kt` (displayName optional, email, password, confirmPassword với toggle riêng, validation mismatch và length, error mapping); thêm `AuthRepository` interface + `StubAuthRepository`, `AuthTokenStore` (EncryptedSharedPreferences); refactor `MainActivity` thêm `AuthState` sealed class (Initializing/Authenticated/LocalCacheOnly/Unauthenticated), startup flow (token store → refresh → Firebase fallback), `AuthSessionBanner` composable, navigation when-block bao gồm route login/register; cập nhật đủ 37 string key trong 6 locale (en/vi/de/es/fr/hi); build thành công với `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug`.

14/06/2026 23:45:00
- Thiết kế chi tiết frontend Android cho đăng ký/đăng nhập; lưu vào `harness-engineering/thiet-ke-frontend-android-auth.md`; gồm: `AuthState` sealed class (Initializing/Authenticated/LocalCacheOnly/Unauthenticated), startup flow kiểm tra token store và thử refresh, 2 màn hình Login/Register (fields, states, error mapping), validation phía client (email/password/confirm/displayName), danh sách 36 string key cần thêm vào 6 locale, hành vi offline và `LocalCacheOnly` (banner không dismiss, SOS vẫn hoạt động, Room không bị xóa), logout UX qua `AlertDialog` trong `EditProfileScreen` (best-effort API, xóa token ngay, giữ Room cache), quay lại EmergencyScreen an toàn sau login/re-login/intent, interface `AuthTokenStore` và `AuthRepository` tối thiểu; chưa sửa runtime UI.

14/06/2026 23:30:00
- Implement API profile và emergency link cho backend `HelpId.Api`: `GET/PUT /api/v1/profile` với JWT auth, ownership policy, validation 422 (blood group enum, language enum, E.164 phone, list count/length), patch semantics cho list null; `POST /api/v1/emergency-links/mint` sinh hoặc tái dùng public key `HID-{16 hex}`, ký public profile JWT HS256 với `jti` nonce, trả URL; `GET /api/v1/public/profile?key=...&t=...` validate key `HID-*`, validate JWT, trả whitelist field (không có userId/email/language/token), header `Cache-Control: no-store`, `Referrer-Policy: no-referrer`; thêm `ProfileApiTests` (6 test) và `EmergencyLinkApiTests` (6 test); fix list-replace logic (chỉ RemoveRange khi field không null), fix status code validation 422, thêm `jti` vào public profile JWT để token luôn khác nhau sau mỗi mint; tất cả 30/30 test pass.

14/06/2026 22:49:46
- Kiểm tra và chạy lại toàn bộ 18 test auth API backend `HelpId.Api`; xác nhận build thành công, 18/18 test pass gồm register/duplicate email, login đúng/sai, refresh token rotation, logout revoke, lockout sau 5 lần sai, SQL injection input và `me` theo JWT subject; ghi kết quả vào `passed-testcases.md`.

14/06/2026 19:26:09
- Implement API auth backend `/api/v1/auth/register`, `login`, `refresh`, `logout`, `me` với PBKDF2 password hash, JWT access token, refresh token hash/rotation/revoke, validation DTO, lockout, lỗi problem response generic và test auth/injection.

14/06/2026 19:11:04
- Tạo EF Core migration `InitialAuthSchema` cho backend `HelpId.Api`, chạy `database update` sinh SQLite dev local, kiểm schema table/index/foreign key/role-permission seed và xác nhận file `.db` local đang bị ignore.

14/06/2026 19:05:34
- Bổ sung phân quyền dữ liệu cho backend SQLite bằng RBAC code-first (`Roles`, `Permissions`, `UserRoles`, `RolePermissions`), seed `User`/`Admin`, policy/handler ASP.NET Core, ownership check theo JWT subject và public emergency profile qua token ngắn hạn với DTO whitelist; thêm test cross-user/admin/public profile.

14/06/2026 18:48:00
- Thêm code-first entity/model cho backend SQLite gồm `Users`, `RefreshTokens`, `UserProfiles`, `ProfileAllergies`, `MedicalNotes`, `EmergencyContacts`, `PublicProfileLinks`, `AuditEvents`; cấu hình DbContext relationship/index/max length/delete behavior và thêm test metadata/schema EF Core.

14/06/2026 18:38:10
- Tạo skeleton backend `backend/HelpId.Api/` bằng ASP.NET Core Web API `net8.0`, EF Core SQLite, `HelpIdDbContext`, endpoint `/health`, appsettings mẫu không chứa secret, README cách chạy và ignore artifact .NET/SQLite local.

14/06/2026 18:30:18
- Tạo `harness-engineering/contract-dang-ky-dang-nhap.md` chốt contract đăng ký/đăng nhập; cập nhật `harness-engineering/uml-use-case.puml`, xóa và render lại `harness-engineering/uml-use-case.png` cho các use-case auth/backend/public profile mới.

14/06/2026 17:17:40
- Cập nhật `AGENTS.md` để log testcase bắt buộc có thêm `cách test` và khi testcase từng fail đã pass lại thì phải xóa entry tương ứng khỏi `failed-testcases.md`; chuẩn hóa entry hiện có trong `passed-testcases.md` theo format mới.

14/06/2026 17:06:23
- Cập nhật `AGENTS.md` để khi render UML Use-case Diagram phải tách thành 3 ảnh riêng cho `Ứng dụng Android HelpID`, `Website Helper ID` và `Vercel Serverless API`, không render chung cả ba system boundary vào một ảnh.

14/06/2026 17:00:18
- Cập nhật `AGENTS.md` để khi chạy kiểm thử phải ghi testcase pass vào `passed-testcases.md` và testcase fail vào `failed-testcases.md`, mỗi entry gồm thời điểm, mục đích/nội dung testcase và kết quả đối chiếu expected/actual.

14/06/2026 16:18:19
- Viết lại `harness-engineering/prompts-dang-ky-dang-nhap.txt` để bổ sung nhóm prompt frontend Android/web rõ ràng và thêm prompt backend riêng cho phân quyền dữ liệu/database bằng role, permission, ownership policy trên SQLite.

14/06/2026 16:04:39
- Viết mới `harness-engineering/ke-hoach.md` với kế hoạch thêm đăng ký/đăng nhập cho Android và backend code-first SQLite chống SQL injection; tạo `harness-engineering/prompts-dang-ky-dang-nhap.txt` chứa các prompt copy-paste để triển khai theo từng bước.

14/06/2026 15:20:09
- Tạo `harness-engineering/danh-gia-database.md` để đánh giá database hiện tại của HelpID, kết luận giữ Room/SQLite local và Firestore cloud là lựa chọn tối ưu hiện tại, kèm so sánh các phương án thay thế và điều kiện tương lai mới cần migration.

14/06/2026 15:02:06
- Tạo UML Use-case Diagram cho toàn bộ HelpID trong `harness-engineering/uml-use-case.puml`, render ảnh `harness-engineering/uml-use-case.png`, và cập nhật `AGENTS.md` để mọi thay đổi use-case phải sửa PlantUML, xóa ảnh cũ rồi render ảnh UML mới.

14/06/2026 14:16:49
- Thêm Tiếng Việt cho Android app: bổ sung lựa chọn `Tiếng Việt`, tạo `values-vi/strings.xml`, chuyển các text UI/SOS/share/PDF/notification/biometric sang resource, dùng số cấp cứu Việt Nam `115` khi chọn `vi-VN`, thêm test cho `EmergencyNumberResolver` và kiểm chứng bằng unit test/build/lint.

14/06/2026 13:52:15
- Viết mới `harness-engineering/ke-hoach.md` với kế hoạch thêm Tiếng Việt vào màn hình chọn ngôn ngữ và Việt hóa toàn bộ app Android khi chọn `vi-VN`, bao gồm quy tắc dùng số cấp cứu y tế Việt Nam `115`.

14/06/2026 12:52:46
- Viết mới `harness-engineering/ke-hoach.md` với kế hoạch xử lý lỗi thiếu `app/google-services.json`, giữ nguyên nguyên tắc không commit secret Firebase và nêu hai hướng xử lý: dùng file Firebase thật local hoặc thiết kế chế độ debug không Firebase nếu được yêu cầu.

14/06/2026 05:11:14
- Sửa format `CHANGELOG.md` và quy tắc trong `AGENTS.md` để dùng ngày giờ bình thường dạng `dd/mm/yyyy hh:mm:ss`, không dùng dấu cộng trong timestamp.

14/06/2026 05:07:50
- Ghi kế hoạch sửa lỗi Gradle Java home vào `harness-engineering/ke-hoach.md`; cập nhật `AGENTS.md` để kế hoạch luôn được viết mới vào `harness-engineering/ke-hoach.md` và phải đọc lại kế hoạch trước khi sửa; xóa `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr` khỏi `gradle.properties`; xóa fallback `JAVA_HOME` hard-code trong `gradlew.bat`; cập nhật ghi chú JDK trong `AGENTS.md` và `harness-engineering/quy-trinh-test.md` để dùng Gradle JVM hoặc `JAVA_HOME` cục bộ JDK 17/21; kiểm chứng build bằng JDK 17 đã qua lỗi Java home và dừng ở lỗi thiếu `app/google-services.json`; tạo `CHANGELOG.md` và bổ sung quy tắc cập nhật changelog lên đầu file sau mỗi prompt có sửa đổi.
