# Tổng quan kiến trúc

## Thành phần chính

Repo có ba bề mặt liên quan với nhau:

- Ứng dụng Android native trong `app/`.
- Backend ASP.NET Core/EF Core SQLite trong `backend/HelpId.Api/`.
- Website React/Vite và API Vercel trong `helper-id/`.

Android là bề mặt chính của sản phẩm. Backend mới xử lý auth user-facing, session, profile private và public emergency link cho luồng mới. Website hỗ trợ marketing và public emergency profile link được tạo từ app. Firebase Auth/Firestore vẫn tồn tại cho luồng legacy và chưa được gỡ/migrate dữ liệu trong repo hiện tại.

## Android app

Stack chính:

- Kotlin.
- Jetpack Compose Material3.
- Firebase Auth ẩn danh và Firestore cho legacy profile sync.
- Backend HelpID API cho đăng ký/đăng nhập, refresh token và mint public link mới.
- Room cho cache local offline-first.
- AndroidX Security Crypto và AndroidKeyStore cho dữ liệu nhạy cảm.
- WorkManager cho SMS follow-up sau SOS.
- Google Play Services Location.
- ZXing cho QR code.
- iTextG cho PDF export.

Luồng khởi động hiện tại:

1. `MainActivity.attachBaseContext` áp dụng ngôn ngữ đã lưu.
2. `MainActivity.onCreate` gọi `AppNavigation`.
3. `AppNavigation` khởi tạo auth backend qua `HelpIdApiAuthRepository` và token store; nếu không có session hợp lệ thì vào Login/Register.
4. Hồ sơ khẩn cấp đã cache trong Room vẫn là đường an toàn khi offline hoặc token lỗi.
5. Các route chính vẫn điều hướng thủ công bằng state: `login`, `register`, `emergency`, `qr`, `edit`, `language`.

Luồng dữ liệu profile:

1. Profile local đọc từ Room trước để phục vụ tình huống khẩn cấp offline.
2. Luồng mới dùng backend `/api/v1/profile` với access token và ownership lấy từ `sub` claim.
3. Code Firebase/Firestore legacy vẫn còn trong `FirebaseRepository` để tương thích, pending sync và user cũ.
4. Khi sửa profile sync phải tránh xóa Room cache khi logout nếu chưa có xác nhận riêng.

Luồng public emergency link:

1. Android mới mint link qua backend `POST /api/v1/emergency-links/mint` bằng Bearer access token.
2. Backend tạo hoặc tái dùng public key `HID-*`, verify ownership, ký public profile JWT 3 giờ và trả URL `PublicWeb__BaseUrl/e/:publicKey?t=...`.
3. Web route `/e/:publicKey` gọi `/api/profile?key=...&t=...`.
4. Vercel `/api/profile` proxy server-side sang backend `GET /api/v1/public/profile` bằng `HELPID_BACKEND_URL`, rồi sanitize whitelist lần nữa.
5. Legacy `/api/mint` Firebase vẫn tồn tại cho backward compatibility.

## Backend HelpID API

Stack chính:

- ASP.NET Core Web API, target `net8.0`.
- Entity Framework Core SQLite code-first.
- JWT access token HS256, lifetime 15 phút.
- Refresh token opaque random, lưu hash trong SQLite, lifetime 30 ngày, rotation mỗi lần refresh.
- Public profile JWT riêng, lifetime 3 giờ.
- Role/permission seed và policy authorization cho self-owned profile/link.

Endpoint chính:

- `GET /health`.
- `POST /api/v1/auth/register`.
- `POST /api/v1/auth/login`.
- `POST /api/v1/auth/refresh`.
- `POST /api/v1/auth/logout`.
- `GET /api/v1/auth/me`.
- `GET /api/v1/profile`.
- `PUT /api/v1/profile`.
- `POST /api/v1/emergency-links/mint`.
- `GET /api/v1/public/profile?key=...&t=...`.

Database chính:

- `Users`, `RefreshTokens`, `Roles`, `Permissions`, `UserRoles`, `RolePermissions`.
- `UserProfiles`, `ProfileAllergies`, `MedicalNotes`, `EmergencyContacts`.
- `PublicProfileLinks`, `AuditEvents`.

## Website/API

Stack chính:

- React 19.
- Vite 6.
- React Router.
- Tailwind CDN cấu hình trong `index.html`.
- Lucide React icons.
- Vercel serverless functions trong `helper-id/api`.

Routes:

- `/`: marketing home.
- `/product`: trang sản phẩm.
- `/about`: trang giới thiệu.
- `/mission`: mission.
- `/terms-of-service`: điều khoản.
- `/privacy-and-cookies`: quyền riêng tư.
- `/e/:publicKey`: emergency profile public, không hiển thị navbar/footer marketing.

API Vercel:

- `GET /api/profile?key=...&t=...`: proxy public profile sang backend mới, giữ no-store/noindex và whitelist.
- `POST /api/mint`: legacy Firebase mint, cần Firebase ID token.
- `POST /api/gemini`: server-only Gemini proxy, tùy chọn bearer token riêng.

## File nhị phân và artifact

- `gradle/wrapper/gradle-wrapper.jar`: wrapper jar.
- `Gemini_Generated_Image_h8qgpch8qgpch8qg.png`: ảnh lớn.
- `app/src/main/res/mipmap-*/ic_launcher*.png`: launcher icons, hiện rất lớn.
- `harness-engineering/uml-use-case-*.png`: artifact render từ PlantUML, chỉ render lại khi `.puml` thay đổi hoặc ảnh thiếu/lỗi.
- `helper-id/package-lock.json`: lockfile npm, chỉ sửa khi dependency thay đổi.
- `.git/`: không đọc/sửa như source trừ khi cần thao tác git.
