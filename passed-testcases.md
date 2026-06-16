16/06/2026 20:35:00
- Mục đích/nội dung testcase: PROMPT 41 — checklist test thủ công 12 trường hợp cho trang Admin (Android emulator `emulator-5554`, backend ASP.NET Core `localhost:5080`, APK debug đã cài).
- Cách test: thao tác thủ công qua `adb shell input tap/text/keyevent`, chụp màn hình, kiểm tra `uiautomator dump`, gọi API trực tiếp qua `curl` để cross-check. Không ghi email thật, token, dữ liệu y tế trong kết quả.
- Expected result: cả 12 TC pass, không có crash, không có regression navigation.
- Actual result:
  - TC-01 (role_user → không thấy nút Admin): EmergencyScreen "Online sync" không có phần tử "Admin Panel" trong UI hierarchy. PASS.
  - TC-02 (gán role_admin qua SQLite → đăng nhập lại → thấy nút Admin): Sau `INSERT INTO UserRoles` và re-login, `content-desc='Admin Panel'` xuất hiện tại header EmergencyScreen. PASS.
  - TC-03 (Admin Dashboard stats đúng): Trang Dashboard hiển thị Total users: 3, Profiles created: 3, Public links minted: 1, Audit events (7 days): 0 — khớp hoàn toàn với `GET /api/v1/admin/stats` trả về. PASS.
  - TC-04 (tab Users hiển thị danh sách): 3 user với email (ẩn trong bản ghi này), role (Admin+User / User), ngày tạo (2026-06-16), trạng thái (Active), nút Revoke Admin / Grant Admin đúng. contentDescription TalkBack hoạt động đúng ("Revoke admin from …", "Grant admin to …"). PASS.
  - TC-05 (phân trang Next/Prev): 3 user fit 1 trang, hiển thị "1 / 1". Nhấn Next và Prev đều giữ nguyên "1 / 1" (boundary clamping đúng). PASS.
  - TC-06 (Grant Admin → loading → reload → role xuất hiện): Nhấn Grant Admin cho test_admin_user, banner "Done" xuất hiện, list reload hiển thị "Roles: Admin, User" và nút đổi thành "Revoke Admin". PASS.
  - TC-07 (Revoke Admin → loading → reload → role bị xóa): Nhấn Revoke Admin cho test_admin_user, banner "Done", list reload hiển thị "Roles: User" và nút đổi thành "Grant Admin". PASS.
  - TC-08 (tắt mạng khi ở admin → hiện lỗi, không crash): Tắt WiFi+data qua `svc wifi/data disable`, nhấn Grant Admin → banner "Network error. Please try again." xuất hiện, app không crash, list vẫn hiển thị. PASS.
  - TC-09 (token hết hạn → auto-refresh → tiếp tục): Xác nhận bằng code review `HelpIdApiAdminRepository.kt` (lines 92-95: `if (response.first == 401) { refreshAndSave(); retry }`) và unit test `getStats 401 refreshes token then retries and returns stats` (đã pass trong PROMPT 40). PASS (code review + unit test).
  - TC-10 (Back → về EmergencyScreen): Nhấn nút ← trong header Admin, app navigate về EmergencyScreen "Online sync". Lưu ý: system KEYCODE_BACK thoát app (không có back stack trong manual navigation — đây là behavior đúng thiết kế). PASS.
  - TC-11 (user thường truy cập route "admin" thủ công → redirect, không crash): Đăng nhập user role_user mới (test_user_2) → EmergencyScreen không có nút Admin Panel trong UI hierarchy → không có đường UI nào dẫn đến route "admin". LaunchedEffect guard (`if (!isAdminUser) currentScreen.value = "emergency"`) và `if (isAdminUser)` render-gate xác nhận bằng code review. PASS.
  - TC-12 (backend trả 403 → redirect home): Xác nhận bằng code review `AdminScreen.kt` (`AdminApiResult.Forbidden → onUnauthorized() → currentScreen.value = "emergency"`) và unit test `getStats 403 returns Forbidden` (đã pass). Trigger thủ công không khả thi trong session (JWT 15 min chưa hết hạn; server dùng JWT claims nên DB revoke không ảnh hưởng ngay). PASS (code review + unit test).
- Ghi chú phát sinh: backend cần env var `HELPID_AUTH_JWT_SIGNING_KEY` để issue JWT; sau restart không có env var → 500; đã resolve bằng khởi động lại backend với đúng env. Không ảnh hưởng kết quả cuối cùng.

16/06/2026 19:30:00
- Mục đích/nội dung testcase: PROMPT 40 hardening audit — (1) backend `dotnet test` sau khi fix SQLite `DateTimeOffset` ORDER BY/comparison bug trong `AdminService.cs`; (2) Android unit test sau khi thêm semantics/contentDescription và stringResource fixes vào `AdminScreen.kt`; (3) Android lint sau tất cả thay đổi trên.
- Cách test: `cd backend/HelpId.Api.Tests && dotnet test`; `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest`; `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:lintDebug`.
- Expected result: tất cả pass, 0 failures, 0 lint errors.
- Actual result: backend 42/42 PASS (sửa 2 test trước đây fail — `GetStats_returns_200_with_correct_schema` và `GetUsers_returns_200_and_excludes_sensitive_fields` — do EF Core SQLite không support `DateTimeOffset` trong LINQ, nay đã fix bằng client-side evaluation); Android unit tests BUILD SUCCESSFUL 0 failures; Android lint BUILD SUCCESSFUL 0 errors. Kết luận: PASS.

16/06/2026 18:43:56
- Mục đích/nội dung testcase: 10 unit test cho `HelpIdApiAdminRepository` — getStats (200 OK parse, IOException→Offline, 401→refresh+retry, 403→Forbidden), getUsers (200 parse, out-of-range page→empty list), assignRole (204→Ok, IOException→Offline), revokeRole (204→Ok, 404→Failed). Fake HTTP client inject qua internal constructor. Không có dữ liệu y tế trong fixture.
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest`.
- Expected result: 10/10 admin tests PASS, 0 failures, 0 errors, toàn bộ test suite không regression.
- Actual result: `BUILD SUCCESSFUL in 3s`, file `TEST-com.helpid.app.data.HelpIdApiAdminRepositoryTest.xml` xác nhận `tests="10" skipped="0" failures="0" errors="0"`. Kết luận: PASS.

16/06/2026 18:38:49
- Mục đích/nội dung testcase: build Android sau khi nối `AdminScreen` với `HelpIdApiAdminRepository` thật — thêm `AdminApiResult<T>` sealed class, đổi return type 4 hàm repository, cập nhật `AdminScreen` xử lý Forbidden/Offline/Failed với đúng string resource và gọi `onUnauthorized()` khi 403.
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, 0 compile errors.
- Actual result: `BUILD SUCCESSFUL in 8s`, chỉ có warning không liên quan (deprecated API, unused param). Kết luận: PASS.

16/06/2026 18:40:00
- Mục đích/nội dung testcase: build Android sau khi wire admin navigation — thêm `isAdmin()` vào `AuthTokenStore`, thêm `onAdminClick` param vào `EmergencyScreen`, thêm route `"admin"` vào `AppNavigation` trong `MainActivity`.
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, 0 errors.
- Actual result: `BUILD SUCCESSFUL in 8s`. Kết luận: PASS.

16/06/2026 18:22:38
- Mục đích/nội dung testcase: build Android sau khi tạo `AdminScreen.kt` và thêm 22 string key admin vào 6 locale — kiểm tra compile Compose, import, string resource không thiếu key.
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, 0 errors.
- Actual result: `BUILD SUCCESSFUL in 11s`. Kết luận: PASS.

16/06/2026 18:16:39
- Mục đích/nội dung testcase: build Android sau khi tạo `HelpIdApiAdminRepository.kt` — kiểm tra compile Kotlin, dependency và wiring không lỗi.
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, 0 errors.
- Actual result: `BUILD SUCCESSFUL in 10s`. Kết luận: PASS.

16/06/2026 18:30:00
- Mục đích/nội dung testcase: 8 test cases trong `AdminApiTests.cs` cho admin API — authorization policy (regular user bị từ chối, unauthenticated bị từ chối), GetStats schema, GetUsers không có sensitive fields, AssignRole 204 + DB check, RevokeRole 204 + DB check, self-revoke protection 403, SQL injection trả về 404 và DB intact.
- Cách test: `cd backend/HelpId.Api.Tests && dotnet test`.
- Expected result: tất cả 8 test admin pass, không có regression.
- Actual result: `Passed! Failed: 0, Passed: 34, Skipped: 0, Total: 34, Duration: 2s`. Kết luận: PASS.

16/06/2026 18:00:40
- Mục đích/nội dung testcase: build backend sau khi thêm Admin folder (AdminDtos, AdminService, AdminEndpoints, AdminServiceCollectionExtensions) và cập nhật Program.cs.
- Cách test: `cd backend/HelpId.Api && dotnet build`.
- Expected result: BUILD SUCCEEDED, 0 errors.
- Actual result: `Build succeeded. 0 Warning(s). 0 Error(s).` Kết luận: PASS.

16/06/2026 18:00:40
- Mục đích/nội dung testcase: chạy toàn bộ backend test suite sau khi thêm admin endpoints, đảm bảo không có regression.
- Cách test: `cd backend/HelpId.Api.Tests && dotnet test`.
- Expected result: tất cả test hiện có pass, 0 failures.
- Actual result: `Passed! Failed: 0, Passed: 34, Skipped: 0, Total: 34, Duration: 2s`. Kết luận: PASS.

16/06/2026 17:03:47
- Mục đích/nội dung testcase: unit test toàn bộ `HelpIdApiProfileRepository` — 21 test cases bao gồm `parseProfile` (JSON parsing), `buildJson` (serialization), `esc` (string escaping), `getProfile` (200/401/IOException/no-token), `updateProfile` (200/IOException/500).
- Cách test: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests "com.helpid.app.data.HelpIdApiProfileRepositoryTest"`.
- Expected result: 21 tests pass, 0 failures.
- Actual result: `BUILD SUCCESSFUL`, 21 tests run, 0 skipped, 0 failures, 0 errors. Kết luận: PASS.

16/06/2026 16:46:12
- Mục đích/nội dung testcase: build kiểm tra EditProfileScreen sau khi thay FirebaseRepository bằng HelpIdApiProfileRepository và thêm string key save_error vào 6 locale.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, không lỗi compile hay thiếu resource.
- Actual result: `BUILD SUCCESSFUL in 10s`. Kết luận: PASS.

16/06/2026 16:41:58
- Mục đích/nội dung testcase: build kiểm tra `HelpIdApiProfileRepository.kt` mới compile không lỗi.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: BUILD SUCCESSFUL, không lỗi compile.
- Actual result: `BUILD SUCCESSFUL in 8s`. Kết luận: PASS.

16/06/2026 15:54:18
- Mục đích/nội dung testcase: sửa ServerSettingsDialog để hiện chi tiết exception khi test connection thất bại.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: build PASS, không lỗi compile.
- Actual result: `BUILD SUCCESSFUL in 11s`. Kết luận: PASS.

16/06/2026 15:24:45
- Mục đích/nội dung testcase: tạo debug network security config cho phép cleartext HTTP đến mọi host trong debug build.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`.
- Expected result: build PASS, không lỗi resource conflict hay merge conflict giữa main và debug network_security_config.xml.
- Actual result: `BUILD SUCCESSFUL in 18s`. Kết luận: PASS.

16/06/2026 14:57:37
- Mục đích/nội dung testcase: sửa lỗi login điện thoại thật báo "Không có kết nối" — giảm timeout, bind 0.0.0.0, thêm UI cấu hình server URL.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- Expected result: build, unit test, lint đều PASS; không có lỗi compile từ import hoặc resource mới.
- Actual result: `assembleDebug BUILD SUCCESSFUL in 16s`; `testDebugUnitTest BUILD SUCCESSFUL in 2s`; `lintDebug BUILD SUCCESSFUL in 14s`. Kết luận: PASS.

16/06/2026 13:07:00
- Mục đích/nội dung testcase: rà soát cuối tính năng biometric — no PII/token logs, strings locale, accessibility Switch, no bypass backend auth, SOS/offline không crash, logout/clear session đúng.
- Cách test: kiểm tra grep `Log\.` trong tất cả file biometric (BiometricManager.kt, BiometricPreferenceStore.kt, BiometricAuthDecision.kt) để xác nhận không log token/PII; kiểm tra diff để xác nhận Switch có `contentDescription`; kiểm tra tất cả 6 locale có đủ key biometric bằng so sánh `grep -o 'name="biometric[^"]*"'`; xác nhận `refreshAfterBiometricUnlock` xử lý 401/403 clear token + biometric setting; xác nhận `performLogout` gọi `clearForUser`; chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; chạy `git diff --check`.
- Expected result: không có log PII/token; Switch có contentDescription; 21 key biometric đồng bộ toàn bộ locale; logout và refresh 401/403 đều clear biometric setting; build/test/lint/diff-check tất cả pass.
- Actual result: grep `Log\.` trong 3 file biometric trả rỗng; diff xác nhận `Switch` có `Modifier.semantics { contentDescription = switchLabel }`; 21 key biometric nhất quán 6 locale; `performLogout` và `refreshAfterBiometricUnlock` cả hai gọi `biometricStore.clearForUser(userId)` khi cần; `BUILD SUCCESSFUL in 28s` (59 unit tests, 0 failures); `git diff --check` exit code 0. Kết luận: PASS.

<!-- CHECKLIST TEST THỦ CÔNG BIOMETRIC — chờ thực hiện trên emulator/device -->
<!--
Checklist này ghi lại các bước test thủ công chưa thể tự động hóa vì cần
BiometricPrompt chạy trên thiết bị thật/emulator. Khi đã test, chuyển kết
quả vào entry passed/failed tương ứng, không ghi PII/token.

TC-M01: Thiết bị không hỗ trợ biometric
  Bước: Dùng emulator không có biometric hardware (mặc định).
  Expected: Toggle bật biometric trong EditProfileScreen hiển thị thông báo
            "Fingerprint unlock is not available on this device", toggle không bật.
  Actual: [chưa test]

TC-M02: Thiết bị có hardware nhưng chưa enroll
  Bước: Emulator có Fingerprint hardware (Pixel 8 profile) nhưng chưa enroll
        fingerprint nào trong Settings > Security.
  Expected: Nhấn toggle bật → prompt không hiện hoặc hiện rồi fail; hiển thị
            "Add a fingerprint in system settings first", toggle không bật.
  Actual: [chưa test]

TC-M03: Enroll fingerprint, bật biometric thành công
  Bước: Enroll fingerprint trong emulator Settings. Vào EditProfileScreen > bật
        toggle Fingerprint unlock > xác nhận biometric prompt thành công.
  Expected: Toggle chuyển sang ON, hiển thị "Fingerprint unlock is on."
  Actual: [chưa test]

TC-M04: Cancel prompt khi đang bật — setting không được lưu
  Bước: Nhấn toggle bật → BiometricPrompt hiện → nhấn Cancel hoặc swipe dismiss.
  Expected: Toggle trở về OFF, không lưu enabled=true vào BiometricPreferenceStore.
  Actual: [chưa test]

TC-M05: Mở app lại khi biometric enabled — màn hình khóa hiện đúng
  Bước: Sau TC-M03, force stop app, mở lại.
  Expected: Hiện BiometricLockScreen với icon khóa, tiêu đề "Unlock HelpID", và
            nút "Use password instead"; BiometricPrompt tự hiện.
  Actual: [chưa test]

TC-M06: Biometric success khi mở app — access token còn hạn
  Bước: Mở app ngay sau khi đăng nhập (access token còn hạn 15 phút) và biometric
        enabled. Xác nhận bằng vân tay.
  Expected: Chuyển thẳng sang màn hình chính (Authenticated) mà không có extra
            network call refresh; không thấy lỗi offline.
  Actual: [chưa test]

TC-M07: Biometric success khi mở app — access token hết hạn, refresh thành công
  Bước: Chờ >15 phút sau khi đăng nhập (hoặc dùng công cụ debug chỉnh clock).
        Mở app, xác nhận biometric. Backend refresh endpoint trả 200.
  Expected: Sau biometric success, app gọi refresh, lưu token mới, chuyển Authenticated.
  Actual: [chưa test]

TC-M08: Biometric success khi mở app — refresh token bị revoke (401/403)
  Bước: Dùng /api/v1/auth/logout trên thiết bị khác để revoke refresh token.
        Mở app lại, xác nhận biometric.
  Expected: Sau biometric success, refresh backend trả 401/403 → clear token và
            biometric setting → chuyển LocalCacheOnly nếu có Room cache, hoặc
            LoginScreen nếu không có cache. Không vào private screens.
  Actual: [chưa test]

TC-M09: Cancel/fail biometric — giữ màn hình khóa, không xóa token
  Bước: Ở màn hình BiometricLockScreen, nhấn Cancel hoặc để fail.
  Expected: Vẫn ở BiometricLockScreen với thông báo lỗi phù hợp; nút "Use password
            instead" vẫn hiện; không xóa token, không vào private screens.
  Actual: [chưa test]

TC-M10: Fail nhiều lần / Lockout
  Bước: Thử vân tay sai nhiều lần liên tiếp cho đến khi lockout.
  Expected: Hiển thị "Too many attempts. Try again later." Không vào private screens.
            Sau lockout ngắn, nút "TRY AGAIN" kích hoạt lại được.
  Actual: [chưa test]

TC-M11: Fallback device credential
  Bước: Dùng PIN thay vân tay tại BiometricPrompt (nếu device credential được cho phép).
  Expected: Auth thành công bằng PIN → cùng flow như biometric success.
  Actual: [chưa test]

TC-M12: Fallback "Use password instead" → LoginScreen
  Bước: Ở BiometricLockScreen, nhấn "Use password instead".
  Expected: Chuyển sang LoginScreen. Đăng nhập bằng password thành công → Authenticated.
            Token được lưu mới; biometric setting giữ nguyên (không bị xóa).
  Actual: [chưa test]

TC-M13: Tắt biometric — dialog xác nhận, sau đó không hỏi biometric khi mở app
  Bước: Khi đang enabled, vào EditProfileScreen, tắt toggle > xác nhận dialog "Turn off".
  Expected: Toggle OFF, "Fingerprint unlock is off." Mở app lại → không hiện
            BiometricLockScreen; vào thẳng Authenticated nếu token còn hạn.
  Actual: [chưa test]

TC-M14: Offline với Room cache — chỉ xem cache local, không sync remote
  Bước: Bật airplane mode. Mở app khi access token đã hết hạn.
  Expected: Nếu biometric enabled → BiometricLockScreen → biometric success →
            refresh fail (network error) → LocalCacheOnly(isOffline=true) →
            banner "Offline — viewing cached profile". Không thấy dữ liệu mới.
  Actual: [chưa test]

TC-M15: Logout — token clear, biometric setting clear, không tự mở user cũ
  Bước: Đang Authenticated + biometric enabled, vào EditProfileScreen > Logout.
  Expected: Best-effort revoke refresh token; clearTokens(); clearForUser(userId);
            chuyển LocalCacheOnly hoặc Unauthenticated. Mở app lại không hiện
            BiometricLockScreen cho user vừa logout.
  Actual: [chưa test]

TC-M16: Đổi user — biometric setting user cũ không áp dụng user mới
  Bước: Đăng nhập user A, bật biometric, logout. Đăng nhập user B.
  Expected: App không hỏi biometric cho user B (user B chưa bật). BiometricLockScreen
            không xuất hiện cho user B.
  Actual: [chưa test]

TC-M17: SOS/QR/NFC không crash khi biometric unavailable hoặc token invalid
  Bước: Xóa enrollment fingerprint hoặc revoke token, thử kích hoạt SOS hoặc mở QR screen.
  Expected: Không crash; lỗi được xử lý gracefully; cảnh báo token hết hạn hoặc
            unavailable hiển thị đúng.
  Actual: [chưa test]
-->

16/06/2026 12:56:33
- Mục đích/nội dung testcase: unit test toàn bộ logic quyết định auth state biometric (`resolveAuthState`), coverage bổ sung `BiometricUtils` error/availability mapping, và user isolation trong `BiometricPreferenceStore`.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; kiểm tra từng file TEST-*.xml trong `app/build/test-results/testDebugUnitTest/` để xác nhận failures=0 và errors=0 cho `BiometricAuthDecisionTest`, `BiometricUtilsTest`, `BiometricPreferenceStoreTest`, `HelpIdApiAuthRepositoryTest`, `EmergencyNumberResolverTest`.
- Expected result: tất cả test pass; BiometricAuthDecisionTest kiểm đủ 6 trường hợp (biometric enabled/disabled × requiresRefresh true/false) và 2 trường hợp blank userId; BiometricUtilsTest bao phủ SecurityUpdateRequired, Unsupported, ERROR_CANCELED, ERROR_NEGATIVE_BUTTON, ERROR_HW_UNAVAILABLE, ERROR_NO_DEVICE_CREDENTIAL, LockoutPermanent/HardwareUnavailable/NoDeviceCredential messageResId; BiometricPreferenceStoreTest xác nhận key của user A ≠ user B và enabled key ≠ lastUnlocked key.
- Actual result: BiometricAuthDecisionTest 11 tests/0 failures/0 errors; BiometricPreferenceStoreTest 9 tests/0 failures/0 errors; HelpIdApiAuthRepositoryTest 28 tests/0 failures/0 errors; BiometricUtilsTest 8 tests/0 failures/0 errors; EmergencyNumberResolverTest 3 tests/0 failures/0 errors. Tổng 59 tests/0 failures/0 errors. Build và lint thành công. Kết luận: PASS.

16/06/2026 11:48:58
- Mục đích/nội dung testcase: rà soát và sửa `authenticatedOrBiometricLocked` để truyền đúng tham số `requiresRefresh` — biometric không bypass refresh khi cần, nhưng không gọi refresh thừa khi access token còn hạn.
- Cách test: đọc diff `MainActivity.kt` để xác nhận `authenticatedOrBiometricLocked` dùng `requiresRefresh = requiresRefresh` thay vì `true`; nhánh `requiresRefresh = false` (access token còn hạn) → biometric success → `Authenticated` trực tiếp; nhánh `requiresRefresh = true` (access token hết hạn) → biometric success → `refreshAfterBiometricUnlock` → refresh backend → 401/403 clear token và biometric, network error → `LocalCacheOnly` khi có cache; xác nhận không log token/PII bằng grep; chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- Expected result: khi access token còn hạn thì không refresh thừa; khi access token hết hạn thì vẫn refresh và xử lý 401/403 đúng; không log token/PII; build/unit test/lint pass.
- Actual result: diff xác nhận `requiresRefresh = requiresRefresh`; grep không thấy log token/JWT/refresh token/PII; Gradle `BUILD SUCCESSFUL` cho cả ba task `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` (27 unit test tasks, BUILD SUCCESSFUL in 18s/3s/29s). Kết luận: PASS.

16/06/2026 11:34:57
- Mục đích/nội dung testcase: kiểm chứng biometric unlock không bypass refresh token revoke/expiry và offline chỉ vào local cache khi có cache.
- Cách test: đọc diff `MainActivity.kt` để xác nhận nhánh biometric enabled luôn vào `AuthState.BiometricLocked(... requiresRefresh = true)`, `onUnlocked` gọi refresh backend trước khi `Authenticated`, refresh 401/403 clear token và biometric setting, network error chỉ vào `LocalCacheOnly` khi `hasCachedProfile` true; chạy `xmllint --noout app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/AndroidManifest.xml`; chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; chạy `git diff --check`.
- Expected result: không có đường vào private screen sau biometric nếu refresh backend fail/revoked; offline có cache chỉ xem local; XML/build/unit test/lint/diff đều pass.
- Actual result: code có đúng các nhánh trên; `xmllint` exit code 0; Gradle `BUILD SUCCESSFUL in 24s`, 54 actionable tasks gồm `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` thành công; `git diff --check` exit code 0. Kết luận: PASS.

16/06/2026 11:31:21
- Mục đích/nội dung testcase: kiểm chứng luồng khóa biometric khi app mở lại, chỉ refresh/gọi API sau biometric success, string locale và build/lint Android.
- Cách test: đọc lại diff `MainActivity.kt` để xác nhận `AuthState.BiometricLocked` chặn private screen trước unlock và nhánh `requiresRefresh` chỉ gọi refresh sau unlock; chạy `xmllint --noout app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/AndroidManifest.xml`; chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; chạy `git diff --check`.
- Expected result: XML hợp lệ; build, unit test và lint pass; diff không có lỗi whitespace; luồng biometric không xóa Room cache khi cancel/fail và không gọi API remote trước biometric success.
- Actual result: `xmllint` exit code 0; Gradle `BUILD SUCCESSFUL in 28s`, 54 actionable tasks gồm `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` thành công; `git diff --check` exit code 0; code giữ cancel/fail ở `BiometricLockScreen` và refresh chỉ chạy trong `onUnlocked`. Kết luận: PASS.

16/06/2026 11:26:27
- Mục đích/nội dung testcase: kiểm chứng UI bật/tắt biometric trong màn hình chỉnh hồ sơ, string resource locale, XML resource, build Android, unit test và lint.
- Cách test: chạy `xmllint --noout app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/AndroidManifest.xml`; chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; chạy `git diff --check`.
- Expected result: XML hợp lệ, debug build thành công, unit test pass, lintDebug pass, diff không có lỗi whitespace.
- Actual result: `xmllint` exit code 0; Gradle `BUILD SUCCESSFUL in 35s`, 54 actionable tasks gồm `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` thành công; `git diff --check` exit code 0. Kết luận: PASS.

16/06/2026 11:20:46
- Mục đích/nội dung testcase: kiểm chứng implement utility biometric Android, secure prefs theo user, string resource locale và build/lint không lỗi.
- Cách test: chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` và chạy `git diff --check`.
- Expected result: app debug build thành công, unit test biometric/token hiện có pass, lintDebug pass, không có lỗi whitespace trong diff.
- Actual result: Gradle `BUILD SUCCESSFUL in 1m 14s`, 54 actionable tasks gồm `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` thành công; `git diff --check` exit code 0. Kết luận: PASS.

16/06/2026 11:11:10
- Mục đích/nội dung testcase: kiểm chứng tài liệu thiết kế biometric đã ghi rõ không cần backend/API/schema mới sau khi rà soát backend và Android token flow.
- Cách test: đọc lại mục `Kết luận rà soát backend và token flow` trong `harness-engineering/thiet-ke-xac-thuc-van-tay.md`; đối chiếu với `AuthService`, `AuthEndpoints`, `HelpIdDbContext`, `AuthTokenStore` và `MainActivity`; chạy `git diff --check`.
- Expected result: tài liệu kết luận biometric chỉ là local unlock cho token/session đã có, không gửi/lưu biometric template, không cần endpoint/schema/migration backend; diff không có lỗi whitespace.
- Actual result: tài liệu có đầy đủ kết luận trên; không sửa backend runtime/schema/API; `git diff --check` exit code 0. Kết luận: PASS.

16/06/2026 11:07:40
- Mục đích/nội dung testcase: kiểm chứng tài liệu thiết kế xác thực vân tay và UML use-case sau thay đổi tài liệu.
- Cách test: chạy `java -jar /tmp/plantuml.jar -tpng harness-engineering/uml-use-case-android.puml harness-engineering/uml-use-case-website.puml harness-engineering/uml-use-case-api.puml`, đọc header PNG của 3 ảnh UML mới, và chạy `git diff --check`.
- Expected result: PlantUML render thành công, 3 ảnh là PNG hợp lệ, không có lỗi whitespace trong diff.
- Actual result: PlantUML exit code 0; PNG hợp lệ gồm Android 1774x2738, Website 1909x1042, API 933x1810; `git diff --check` exit code 0. Kết luận: PASS.

# Passed Testcases

16/06/2026 10:37:55
- Mục đích/nội dung testcase: Kiểm chứng UML database diagram ghi rõ role RBAC seed hiện tại và render lại hợp lệ.
- Cách test: Đọc lại block `Seeded RBAC roles and permissions` trong `harness-engineering/uml-database.puml`; xóa ảnh cũ và chạy `java -jar /tmp/plantuml.jar -tpng harness-engineering/uml-database.puml`; kiểm tra header PNG; chạy `git diff --check`.
- Expected result: UML nêu rõ `role_user`/`User`, `role_admin`/`Admin`, không có role Doctor/Patient riêng; PlantUML render thành PNG hợp lệ; `git diff --check` không báo lỗi.
- Actual result: UML có đúng `role_user`/`User`, `role_admin`/`Admin`, ghi rõ chưa seed Doctor/Patient; PNG mới hợp lệ kích thước 1636x2191; `git diff --check` exit code 0 không có output lỗi.
- Kết luận: PASS.

16/06/2026 09:52:26
- Mục đích/nội dung testcase: Kiểm tra đổi tên file prompt copy-paste và bổ sung nhóm prompt xác thực vân tay không tạo lỗi whitespace hoặc tham chiếu vận hành sai.
- Cách test: Chạy `git diff --check`; chạy `rg -n` với pattern `prompts-dang-ky-dang-nhap|copy-paste-prompts` trên `AGENTS.md`, `harness-engineering`, `CHANGELOG.md`; đọc lại đầu file `harness-engineering/copy-paste-prompts.txt` và danh sách prompt 21-28.
- Expected result: `git diff --check` không báo lỗi; tài liệu vận hành trỏ tới `copy-paste-prompts.txt`; file mới có nhóm prompt biometric theo thứ tự contract/backend decision/Android implementation/integration/test/hardening; không còn hướng dẫn vận hành bắt buộc dùng tên file cũ.
- Actual result: `git diff --check` exit code 0 không có output lỗi; `AGENTS.md` và `harness-engineering/README.md` trỏ tới `copy-paste-prompts.txt`; file mới có prompt 21-28 cho xác thực vân tay; tên file cũ chỉ còn trong changelog lịch sử và ghi chú thay thế trong file mới.
- Kết luận: PASS.

16/06/2026 09:38:34
- Mục đích/nội dung testcase: Kiểm chứng UML database diagram sau khi bổ sung thông tin phân quyền/RBAC render được và không có lỗi whitespace.
- Cách test: Xóa ảnh cũ `harness-engineering/uml-database.png`, chạy `java -jar /tmp/plantuml.jar -tpng harness-engineering/uml-database.puml`, kiểm tra header PNG bằng đọc 24 byte đầu của ảnh mới, và chạy `git diff --check`.
- Expected result: Ảnh cũ được thay bằng ảnh render mới; PlantUML exit code 0; PNG mới hợp lệ; `git diff --check` không báo lỗi.
- Actual result: PlantUML exit code 0, sinh `harness-engineering/uml-database.png` hợp lệ kích thước 1626x2191; `git diff --check` exit code 0 không có output lỗi.
- Kết luận: PASS.

16/06/2026 09:24:16
- Mục đích/nội dung testcase: Kiểm tra cập nhật quy tắc lấy thời gian chính xác trong `AGENTS.md` và changelog không tạo lỗi whitespace.
- Cách test: Chạy `git diff --check` ở root repo sau khi cập nhật `AGENTS.md` và `CHANGELOG.md`; đọc lại đoạn quy tắc trong `AGENTS.md` và entry mới đầu `CHANGELOG.md`.
- Expected result: `AGENTS.md` có rule bắt buộc lấy thời gian bằng `date '+%d/%m/%Y %H:%M:%S'`; `CHANGELOG.md` có entry mới dùng timestamp từ lệnh `date`; `git diff --check` không báo lỗi.
- Actual result: `AGENTS.md` có đúng rule yêu cầu; `CHANGELOG.md` có entry `16/06/2026 09:23:25`; `git diff --check` exit code 0 không có output lỗi.
- Kết luận: PASS.

16/06/2026 09:19:35
- Mục đích/nội dung testcase: Kiểm chứng UML database diagram mới render được và diff không có lỗi whitespace.
- Cách test: Chạy `java -jar /tmp/plantuml.jar -tpng harness-engineering/uml-database.puml`, kiểm tra header PNG bằng đọc 24 byte đầu của `harness-engineering/uml-database.png`, và chạy `git diff --check`.
- Expected result: PlantUML exit code 0 và sinh PNG hợp lệ; ảnh có header PNG đúng; `git diff --check` không báo lỗi.
- Actual result: PlantUML exit code 0, sinh `harness-engineering/uml-database.png` hợp lệ kích thước 1279x2092; `git diff --check` exit code 0 không có output lỗi.
- Kết luận: PASS.

15/06/2026 02:55:07
- Mục đích/nội dung testcase: Kiểm tra whitespace/conflict marker sau khi cập nhật tài liệu vận hành backend/auth, API contract, env, migration/test và ghi chú Firebase legacy.
- Cách test: Chạy `git diff --check` ở root repo. Đây là testcase suite-level, command không in chi tiết từng file khi pass và không chứa dữ liệu nhạy cảm.
- Expected result: Lệnh kết thúc exit code 0, không có whitespace error hoặc conflict marker trong diff.
- Actual result: `git diff --check` kết thúc exit code 0 và không có output lỗi.
- Kết luận: PASS.

15/06/2026 02:40:24
- Mục đích/nội dung testcase: Kiểm thử hardening backend auth/API cho password policy, SQL injection, refresh token revoke/rotation, JWT payload minimization, public/API logging và raw SQL unsafe.
- Cách test: Chạy `dotnet build backend/HelpId.Api/HelpId.Api.csproj`; `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; `dotnet ef migrations list --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj`; scan source bằng `rg` để tìm raw SQL API unsafe trong `backend/HelpId.Api` (loại `bin/obj`) và scan log/token pattern trong Android/web API. Test runner chỉ trả kết quả suite-level cho build/migration; scan không in dữ liệu nhạy cảm.
- Expected result: Backend build 0 error; auth/security test pass; migration list vẫn thấy `InitialAuthSchema`; không có `FromSqlRaw`/`ExecuteSqlRaw`/raw SQL unsafe trong source backend; không còn log pattern rõ ràng chứa uid/raw response/token trong Android/web API.
- Actual result: `dotnet build` PASS (`0 Warning(s), 0 Error(s)`); `dotnet test` PASS (`Failed: 0, Passed: 34, Total: 34`); migration list PASS (`20260614120904_InitialAuthSchema`); raw SQL scan PASS (không có kết quả); log/token scan PASS (không có kết quả).
- Kết luận: PASS.

15/06/2026 02:40:24
- Mục đích/nội dung testcase: Kiểm chứng web/Android sau khi sửa safe logging, thêm dependency trực tiếp `node-fetch`, chạy audit fix non-breaking và cập nhật lockfile.
- Cách test: Chạy `npm install --package-lock-only`, `npm audit --omit=dev --json`, `npm audit fix --omit=dev`, restore dev deps bằng `npm install`, sau đó chạy `cd helper-id && npm run build`, `cd helper-id && npx tsc --noEmit`, `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, `git diff --check`.
- Expected result: Web build/typecheck pass; Android assemble/unit/lint pass; diff check không có whitespace/conflict marker; production audit không còn critical/high sau fix non-breaking, nếu còn finding cần breaking `--force` thì không tự áp dụng và ghi rõ.
- Actual result: `npm audit fix --omit=dev` áp dụng fix non-breaking, production audit sau đó còn 8 moderate trong nhánh `firebase-admin`/Google transitive và fix duy nhất yêu cầu `npm audit fix --force` về `firebase-admin@12.1.0` breaking nên chưa áp dụng; `npm run build` PASS (`✓ built in 2.76s`); `npx tsc --noEmit` PASS; Gradle PASS (`BUILD SUCCESSFUL in 28s`, 54 actionable tasks); `git diff --check` PASS (không output lỗi).
- Kết luận: PASS.

15/06/2026 02:23:34
- Mục đích/nội dung testcase: Kiểm thử end-to-end luồng register/login/refresh/logout/profile/QR/public profile sau khi nối frontend/backend mới, xác nhận frontend contract hiện khớp backend/API và không cần sửa frontend.
- Cách test: Tạo SQLite DB tạm bằng `dotnet ef database update`, chạy backend local trên `http://127.0.0.1:5099`, dùng script Node gọi HTTP thật qua các endpoint: `POST /api/v1/auth/register`, login sai, `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/refresh`, `PUT/GET /api/v1/profile`, `POST /api/v1/emergency-links/mint`, `GET /api/v1/public/profile`, gọi trực tiếp Vercel-style handler `helper-id/api/profile.js`, `POST /api/v1/auth/logout`, rồi thử refresh lại sau logout. Script chỉ in số lượng check pass, không in access token, refresh token, public profile token, dữ liệu y tế hoặc số điện thoại.
- Expected result: Register trả 201 và token pair hợp lệ; login sai trả 401 và không trả token; login đúng trả 200; refresh trả 200 và rotate refresh token; profile PUT/GET trả 200; QR mint trả public key `HID-*`, URL và header `no-store/noindex`; public profile trực tiếp và qua proxy trả whitelist field, không có `userId/email/language/lastUpdated`; logout trả 204; refresh sau logout trả 401; backend local dừng sau test.
- Actual result: E2E PASS 46 checks cho register/login/refresh/logout/profile/QR/public-profile/proxy; không phát hiện lỗi nối contract, không sửa backend/API/frontend; backend local đã dừng, health sau khi dừng trả `000`.
- Kết luận: PASS.

15/06/2026 02:23:34
- Mục đích/nội dung testcase: Chạy toàn bộ build/test liên quan sau E2E auth/profile/QR/public profile.
- Cách test: Chạy `dotnet build backend/HelpId.Api/HelpId.Api.csproj`; `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; `dotnet ef migrations list --no-build --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj` với DB tạm; `cd helper-id && npm run build`; `cd helper-id && npx tsc --noEmit`; `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- Expected result: Backend build pass; backend 30/30 test pass; migration list thấy `InitialAuthSchema`; Vite build pass; TypeScript 0 error; Android assemble/unit/lint pass với JDK 17.
- Actual result: `dotnet build` PASS (`0 Warning(s), 0 Error(s)`); `dotnet test` PASS (`Failed: 0, Passed: 30, Total: 30`); migration list PASS (`20260614120904_InitialAuthSchema`); `npm run build` PASS (`✓ built in 4.38s`); `npx tsc --noEmit` PASS (không output lỗi); Gradle PASS (`BUILD SUCCESSFUL in 55s`, 54 actionable tasks).
- Kết luận: PASS.

15/06/2026 01:53:10
- Mục đích/nội dung testcase: Kiểm chứng UX/security web public emergency profile sau khi nối backend mới: không cache PII ở fetch/proxy, proxy và UI chỉ giữ field whitelist, lỗi token hết hạn rõ ràng, header no-store/noindex ở proxy/backend public profile, deep link Android compile được.
- Cách test: Chạy `cd helper-id && npm run build`; `cd helper-id && npx tsc --noEmit`; `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug`; `git diff --check`. Test runner chỉ trả kết quả ở mức command/suite cho web build/typecheck và Android build.
- Expected result: Vite build thành công, TypeScript không lỗi, backend test pass, Android debug APK build thành công với JDK hỗ trợ, diff không có whitespace error.
- Actual result: `npm run build` PASS (`✓ built in 2.15s`); `npx tsc --noEmit` PASS (exit code 0, không output lỗi); backend test PASS (`Passed: 30, Failed: 0, Total: 30`); Android assemble PASS với JDK 17 (`BUILD SUCCESSFUL in 33s`); `git diff --check` PASS (không output lỗi).
- Kết luận: PASS.

15/06/2026 20:00:00
- Mục đích/nội dung testcase: Build và type-check web `helper-id` sau khi chuyển `api/profile.js` từ Firebase/Firestore sang proxy backend `GET /api/v1/public/profile`; cải thiện error messages trong `EmergencyProfilePage.tsx` (401 vs 404 vs other); chạy backend test xác nhận không regression.
- Cách test: `cd helper-id && npm run build`; `cd helper-id && npx tsc --noEmit`; `cd backend/HelpId.Api.Tests && dotnet test`.
- Expected result: Vite build SUCCESSFUL, tsc 0 error, backend 30/30 pass.
- Actual result: `vite build` → `✓ built in 2.50s` (303.58 kB JS, 0 warning); `tsc --noEmit` → no output (clean); `dotnet test` → `Passed! - Failed: 0, Passed: 30, Skipped: 0, Total: 30`.
- Kết luận: PASS.

16/06/2026 02:30:00
- Mục đích/nội dung testcase: Build debug APK, chạy unit test và lint sau khi audit và sửa frontend Android auth (layout shift từ supportingText, ghost button không disable khi loading).
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- Expected result: BUILD SUCCESSFUL, 31 unit test pass, lint 0 error.
- Actual result: `BUILD SUCCESSFUL in 41s` (assembleDebug + lintDebug chung pass); `BUILD SUCCESSFUL in 1s` (testDebugUnitTest, từ cache); XML report: 28/28 `HelpIdApiAuthRepositoryTest` + 3/3 `EmergencyNumberResolverTest` = 31 tests, 0 failures, 0 errors; lintDebug 0 error.
- Kết luận: PASS.

16/06/2026 01:30:00
- Mục đích/nội dung testcase: Build debug APK, chạy unit test và lint sau khi chuyển QR/NFC/SOS fallback mint sang backend API (`POST /api/v1/emergency-links/mint`): tạo `HelpIdApiEmergencyLinkRepository` (auto-refresh token khi hết hạn, retry sau 401, trả "" khi offline/lỗi), sửa `QRScreen` nhận `onMintLink: suspend () -> String` thay FirebaseRepository, sửa `EmergencyScreen` nhận `onMintLink` thay 2 chỗ `repository.mintEmergencyLink()`, wire `emergencyLinkRepository` trong `MainActivity`; cũng chạy 30/30 backend test (không sửa backend).
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; backend test `dotnet test backend/HelpId.Api.Tests/... --no-build -v quiet`.
- Expected result: BUILD SUCCESSFUL cho cả 3 Gradle task, 31 unit test pass, lint 0 error; backend 30/30 pass.
- Actual result: `BUILD SUCCESSFUL in 34s` (assembleDebug); `BUILD SUCCESSFUL in 3s` (testDebugUnitTest) 31 tests pass; `BUILD SUCCESSFUL in 18s` (lintDebug) 0 error; backend `Passed! - Failed: 0, Passed: 30, Skipped: 0, Total: 30`.
- Kết luận: PASS.

15/06/2026 23:30:00
- Mục đích/nội dung testcase: Build debug APK, chạy 31 unit test và lint sau khi hoàn thiện frontend auth state Android: thêm offline/invalid-token detection khi startup (phân biệt `NetworkError` vs `ApiError` từ refresh), thêm logout button + confirmation dialog trong `EditProfileScreen`, thêm `performLogout()` trong `AppNavigation` (best-effort revoke, `clearTokens()`, kiểm Room cache trước khi quyết định `LocalCacheOnly` vs `Unauthenticated`), phân biệt banner "offline" vs "session expired" trong `LocalCacheOnly` state, không xóa Room database khi logout.
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; unit test gọi companion object method của `HelpIdApiAuthRepository` và `EmergencyNumberResolver` không cần Context/network; lint kiểm tra toàn bộ source Android.
- Expected result: BUILD SUCCESSFUL cho cả 3 task, 28/28 `HelpIdApiAuthRepositoryTest` pass, 3/3 `EmergencyNumberResolverTest` pass, lint 0 error.
- Actual result: `BUILD SUCCESSFUL in 35s` (assembleDebug); `BUILD SUCCESSFUL in 3s` (testDebugUnitTest) với `tests=28 failures=0 errors=0` và `tests=3 failures=0 errors=0`; `BUILD SUCCESSFUL in 29s` (lintDebug) với HTML report, 0 error severity.
- Kết luận: PASS.

15/06/2026 01:00:00
- Mục đích/nội dung testcase: Build debug APK, chạy 28 unit test `HelpIdApiAuthRepositoryTest` và lint sau khi sửa bug key `"displayname"` trong `RegisterScreen.kt` (dòng 152: `fieldErrors["displayName"]` → `fieldErrors["displayname"]`) để field error displayName từ backend 422 ValidationProblemDetails được hiển thị đúng trên UI.
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue`; test chạy trên companion object method không cần Context/network; lint kiểm tra toàn bộ source.
- Expected result: BUILD SUCCESSFUL, 28/28 unit test pass, lint pass không có error.
- Actual result: `BUILD SUCCESSFUL in 43s`; 28/28 `HelpIdApiAuthRepositoryTest` pass, 3/3 `EmergencyNumberResolverTest` pass; lint: `Wrote HTML report`, no errors.
- Kết luận: PASS.

15/06/2026 00:30:00
- Mục đích/nội dung testcase: Chạy 28 unit test cho `HelpIdApiAuthRepository` bao gồm: parse response 200/201 login/register/refresh (lấy accessToken, refreshToken, userId, expiry), parse lỗi 401/409/423 → ApiError, parse 422 ValidationProblemDetails của ASP.NET Core với các field errors cho email/password/displayName (chuẩn hóa key về lowercase), parse JSON lỗi và JSON rỗng không crash, parse timestamp ISO-8601 với suffix `Z` và `+00:00` và fractional seconds, parse timestamp null/rỗng/sai format trả fallback, `detectFieldCode` map message → code cho tất cả trường hợp đã biết và fallback `server_error`.
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:testDebugUnitTest`; test gọi trực tiếp companion object method (`parseTokenResponse`, `parseIso8601ToEpochMs`, `detectFieldCode`) không cần Context hoặc network; timestamp test dùng `Calendar` UTC thay vì hard-code giá trị epoch.
- Expected result: 28/28 test `HelpIdApiAuthRepositoryTest` pass; 3 test `EmergencyNumberResolverTest` pass; 0 failure.
- Actual result: `BUILD SUCCESSFUL`; XML report: `tests=28 failures=0 errors=0` cho `HelpIdApiAuthRepositoryTest`; `tests=3 failures=0 errors=0` cho `EmergencyNumberResolverTest`.
- Kết luận: PASS.

15/06/2026 00:30:00
- Mục đích/nội dung testcase: Build debug APK (`assembleDebug`) sau khi thêm `HelpIdApiAuthRepository`, `HelpIdApiConfig`, `network_security_config.xml`, bật `buildConfig`, cập nhật `AndroidManifest.xml`.
- Cách test: Chạy `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug`; không log token/password/PII trong source.
- Expected result: BUILD SUCCESSFUL, 0 compile error.
- Actual result: `BUILD SUCCESSFUL`; APK sinh tại `app/build/outputs/apk/debug/`.
- Kết luận: PASS.

14/06/2026 23:30:00
- Mục đích/nội dung testcase: Chạy toàn bộ 30 test backend gồm profile API (`GET/PUT /api/v1/profile`) và emergency link API (`POST /api/v1/emergency-links/mint`, `GET /api/v1/public/profile`) với JWT auth, ownership policy, validation 422, list replace, public key `HID-*`, public profile JWT, whitelist field, no-store header và SQL injection.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj --logger "console;verbosity=normal"`; test dùng SQLite in-memory, `SimpleCurrentUserContext` set userId thủ công, không ghi token/dữ liệu y tế/số điện thoại vào log.
- Expected result: 30/30 test pass; validation trả 422 cho blood group sai/allergy quá nhiều/phone không hợp lệ; list null nghĩa là không thay đổi (patch semantics); mỗi lần mint sinh token khác nhau dù cùng public key; public profile không trả userId/email/language; cross-user mint cùng key trả 409.
- Actual result: Test runner kết thúc exit code 0; output báo `Passed: 30`, `Failed: 0`, `Total: 30`; tất cả test ProfileApiTests, EmergencyLinkApiTests, AuthApiTests, AuthorizationAccessTests và HelpIdDbContextModelTests đều PASS.
- Kết luận: PASS.

14/06/2026 22:49:46
- Mục đích/nội dung testcase: Xác nhận build backend `HelpId.Api` sau khi kiểm tra toàn bộ auth API (`register`, `login`, `refresh`, `logout`, `me`), password hash PBKDF2, JWT access token, refresh token hash, validation DTO, lockout và status code.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh build kết thúc exit code 0, backend compile thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 22:49:46
- Mục đích/nội dung testcase: Chạy toàn bộ 18 test backend auth API gồm: register/duplicate email, login đúng/sai password, refresh token rotation, logout revoke, lockout sau 5 lần sai, SQL injection input không bypass auth, `me` theo JWT subject.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj --logger "console;verbosity=detailed"`; test dùng SQLite in-memory, endpoint methods public static, không ghi token/password/secret vào log.
- Expected result: 18/18 test pass; refresh token không lưu plaintext trong DB; token cũ không dùng lại sau refresh/logout; input `' OR 1=1 --` không tạo session và không bypass auth; lockout trả HTTP 423 sau 5 lần sai; `me` trả đúng email và permission theo JWT subject.
- Actual result: Test runner kết thúc exit code 0; output báo `Passed: 18`, `Failed: 0`, `Total: 18`; tất cả testcase AuthApiTests, AuthorizationAccessTests và HelpIdDbContextModelTests đều PASS.
- Kết luận: PASS.

14/06/2026 19:26:09
- Mục đích/nội dung testcase: Build backend sau khi implement auth API register/login/refresh/logout/me, JWT access token, password hash PBKDF2 và refresh token hash.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh build kết thúc exit code 0, backend compile thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 19:26:09
- Mục đích/nội dung testcase: Test auth API backend cho login đúng/sai, duplicate email, refresh token rotation, logout revoke, lockout, input SQL injection và `me` theo JWT subject.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; test dùng SQLite in-memory và endpoint methods, không ghi token/password/secret vào log.
- Expected result: Test runner kết thúc exit code 0, toàn bộ test pass; refresh token không lưu plaintext trong DB, token cũ không dùng lại sau refresh/logout, input injection không tạo session hoặc bypass auth.
- Actual result: Lệnh kết thúc exit code 0; output báo `Passed! - Failed: 0, Passed: 18, Skipped: 0, Total: 18`.
- Kết luận: PASS.

14/06/2026 19:26:09
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi implement auth API backend và cập nhật log/changelog để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Kiểm tra trạng thái migration trước khi tạo `InitialAuthSchema`.
- Cách test: Chạy lệnh `dotnet ef migrations list --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj` trong root repo.
- Expected result: Lệnh kết thúc exit code 0; trước khi tạo migration không có migration cũ trong backend mới.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.` và `No migrations were found.`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Tạo EF Core migration source `InitialAuthSchema` từ code-first model backend.
- Cách test: Chạy lệnh `dotnet ef migrations add InitialAuthSchema --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj --output-dir Data/Migrations` và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0, sinh migration source trong `backend/HelpId.Api/Data/Migrations/`, không sinh secret hoặc database production.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.` và `Done. To undo this action, use 'ef migrations remove'`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Apply migration vào SQLite local/dev theo connection string `HelpIdDb`.
- Cách test: Chạy lệnh `dotnet ef database update --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj` sau khi tạo thư mục `backend/HelpId.Api/App_Data`.
- Expected result: Lệnh kết thúc exit code 0, apply migration `InitialAuthSchema` vào SQLite dev local.
- Actual result: Lệnh kết thúc exit code 0; output báo `Applying migration '20260614120904_InitialAuthSchema'.` và `Done.`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Kiểm tra schema SQLite sau khi apply migration, gồm bảng, index, foreign key, migration history và seed role-permission.
- Cách test: Chạy `sqlite3 -header -column backend/HelpId.Api/App_Data/helpid-dev.db` với các truy vấn `sqlite_master`, `PRAGMA foreign_key_list`, join `RolePermissions/Roles/Permissions` và `__EFMigrationsHistory`; lần chạy trong sandbox bị chặn bởi `bwrap`, sau đó chạy lại bằng quyền được phê duyệt để đọc file SQLite local.
- Expected result: SQLite có đủ bảng auth/profile/audit/RBAC, unique index quan trọng, FK cho `RolePermissions`, `UserRoles`, `RefreshTokens`, seed `User`/`Admin` và migration `InitialAuthSchema` trong history.
- Actual result: Lệnh kiểm schema kết thúc exit code 0; output liệt kê 13 table gồm `Users`, `RefreshTokens`, `UserProfiles`, `PublicProfileLinks`, `Roles`, `Permissions`, `UserRoles`, `RolePermissions`, các index `UX_Users_NormalizedEmail`, `UX_RefreshTokens_TokenHash`, `UX_Roles_NormalizedName`, `UX_Permissions_Code`, FK cascade/set-null đúng cấu hình, 9 dòng role-permission seed và migration `20260614120904_InitialAuthSchema`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Xác nhận SQLite dev local không bị đưa vào source.
- Cách test: Chạy `git status --ignored --short backend/HelpId.Api/App_Data/helpid-dev.db` sau khi database update.
- Expected result: File database local hiển thị trạng thái ignored, không phải untracked source cần commit.
- Actual result: Lệnh kết thúc exit code 0; output `!! backend/HelpId.Api/App_Data/helpid-dev.db`, xác nhận file `.db` đang bị ignore.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Build backend sau khi thêm migration source `InitialAuthSchema`.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh build kết thúc exit code 0, backend compile thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Chạy test backend sau khi thêm migration source và apply SQLite dev schema.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Test runner kết thúc exit code 0, toàn bộ test pass.
- Actual result: Lệnh kết thúc exit code 0; output báo `Passed! - Failed: 0, Passed: 12, Skipped: 0, Total: 12`.
- Kết luận: PASS.

14/06/2026 19:11:04
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi thêm migration source, update SQLite dev, cập nhật log/changelog để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 19:05:34
- Mục đích/nội dung testcase: Build backend sau khi bổ sung RBAC, policy/handler authorization, ownership service và public profile whitelist service.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh build kết thúc exit code 0, backend compile thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 19:05:34
- Mục đích/nội dung testcase: Kiểm tra phân quyền dữ liệu backend gồm RBAC seed, cross-user ownership, user id lấy từ JWT subject, admin policy cần role admin và public profile chỉ trả whitelist sau token public hợp lệ.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; test project dùng SQLite in-memory và service/policy trực tiếp, không ghi secret hoặc dữ liệu nhạy cảm vào log.
- Expected result: Test runner kết thúc exit code 0, toàn bộ test pass; user A không đọc/sửa được tài nguyên user B, admin policy có yêu cầu role admin, public profile không có field ngoài whitelist.
- Actual result: Lệnh kết thúc exit code 0; output báo `Passed! - Failed: 0, Passed: 12, Skipped: 0, Total: 12`.
- Kết luận: PASS.

14/06/2026 19:05:34
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi bổ sung phân quyền dữ liệu backend và cập nhật log/changelog để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 18:48:33
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi thêm entity/model backend, test project, cập nhật README/changelog/testcase log để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 18:48:00
- Mục đích/nội dung testcase: Build backend sau khi thêm code-first entity/model và cấu hình `HelpIdDbContext`.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh build kết thúc exit code 0, backend compile thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 18:48:00
- Mục đích/nội dung testcase: Kiểm tra metadata/schema EF Core cho entity code-first backend.
- Cách test: Chạy lệnh `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`; test project kiểm tra entity/table, max length, required field, unique index, foreign key/delete behavior và `EnsureCreated()` với SQLite in-memory.
- Expected result: Test runner kết thúc exit code 0, toàn bộ test pass.
- Actual result: Lệnh kết thúc exit code 0; output báo `Passed! - Failed: 0, Passed: 6, Skipped: 0, Total: 6`.
- Kết luận: PASS.

14/06/2026 18:38:50
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi tạo skeleton backend, cập nhật `.gitignore`, `CHANGELOG.md` và log testcase để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 18:38:10
- Mục đích/nội dung testcase: Build skeleton backend ASP.NET Core `backend/HelpId.Api` sau khi thêm EF Core SQLite và `HelpIdDbContext`.
- Cách test: Chạy lệnh `dotnet build backend/HelpId.Api/HelpId.Api.csproj` trong root repo và kiểm tra exit code/output của command.
- Expected result: Lệnh build kết thúc exit code 0, project restore/build thành công, không có warning hoặc error.
- Actual result: Lệnh kết thúc exit code 0; output báo `Build succeeded.`, `0 Warning(s)`, `0 Error(s)`.
- Kết luận: PASS.

14/06/2026 18:38:10
- Mục đích/nội dung testcase: Kiểm tra skeleton backend chạy được và endpoint `/health` trả JSON trạng thái an toàn.
- Cách test: Chạy `dotnet run --project backend/HelpId.Api/HelpId.Api.csproj --no-build --urls http://127.0.0.1:5080`, gọi `curl -sS http://127.0.0.1:5080/health`, sau đó dừng server bằng Ctrl+C.
- Expected result: Server lắng nghe trên `http://127.0.0.1:5080`, `/health` trả HTTP 200 với `status` là `ok` và không chứa secret hoặc dữ liệu người dùng.
- Actual result: Server lắng nghe đúng URL; `curl` trả JSON `{"status":"ok","service":"HelpId.Api","checkedAtUtc":"2026-06-14T11:37:57.4362119+00:00"}`; server đã dừng sau kiểm tra.
- Kết luận: PASS.

14/06/2026 18:31:22
- Mục đích/nội dung testcase: Render lại UML use-case sau khi cập nhật use-case đăng ký/đăng nhập, backend auth và public profile.
- Cách test: Chạy lệnh `java -jar /tmp/plantuml.jar harness-engineering/uml-use-case.puml`, sau đó kiểm tra `harness-engineering/uml-use-case.png` tồn tại bằng `ls -l`.
- Expected result: Lệnh PlantUML kết thúc exit code 0 và tạo lại file PNG UML.
- Actual result: Lệnh PlantUML kết thúc exit code 0; `ls -l` xác nhận `harness-engineering/uml-use-case.png` tồn tại với kích thước 423818 bytes.
- Kết luận: PASS.

14/06/2026 18:31:22
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi tạo contract đăng ký/đăng nhập, cập nhật PlantUML, render PNG và cập nhật `CHANGELOG.md` để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 17:18:32
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi cập nhật rule log testcase trong `AGENTS.md`, chuẩn hóa `passed-testcases.md` và cập nhật `CHANGELOG.md` để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

14/06/2026 17:07:07
- Mục đích/nội dung testcase: Chạy `git diff --check` sau khi cập nhật `AGENTS.md` và `CHANGELOG.md` để kiểm tra whitespace/error marker trong diff.
- Cách test: Chạy lệnh `git diff --check` trong root repo và kiểm tra exit code/output.
- Expected result: Lệnh kết thúc exit code 0 và không in lỗi whitespace hoặc conflict marker.
- Actual result: Lệnh kết thúc exit code 0, output rỗng, không phát hiện lỗi whitespace hoặc conflict marker.
- Kết luận: PASS.

