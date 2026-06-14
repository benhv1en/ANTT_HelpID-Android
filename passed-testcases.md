# Passed Testcases

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

