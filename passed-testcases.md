# Passed Testcases

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

