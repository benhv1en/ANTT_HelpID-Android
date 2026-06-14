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
