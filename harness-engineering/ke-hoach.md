# Kế hoạch thêm đăng ký/đăng nhập cho HelpID

Thời điểm lập kế hoạch: 14/06/2026 16:04:39

## Bối cảnh hiện tại

Repo hiện chưa có đăng ký/đăng nhập user-facing. Android app chỉ tự động dùng Firebase Auth anonymous trong `FirebaseRepository.initializeUser()`. Khi chưa có user Firebase, app gọi `signInAnonymously()`, lấy `uid`, lưu profile vào Firestore và dùng Firebase ID token để mint public emergency link qua Vercel API.

Hiện trạng liên quan database/backend:

- Android local: Room/SQLite database `helpid_database` để cache hồ sơ offline.
- Cloud hiện tại: Firestore collections `users/{uid}` và `publicKeys/{publicKey}`.
- Web/API hiện tại: `helper-id/api/mint.js`, `profile.js`, `gemini.js` chạy kiểu Vercel serverless JavaScript.
- Chưa có màn hình Login/Register trong Android.
- Chưa có backend SQL code-first/migration.

Yêu cầu mới:

- Thêm tính năng đăng ký/đăng nhập cho app hiện tại.
- Làm cả frontend và backend.
- Backend phải có thiết kế database chống SQL injection.
- Quy trình backend phải đi theo 5 bước người dùng đưa ra:
  1. Viết code mô tả bảng/cột theo code-first.
  2. Chạy migrate để sinh SQLite database.
  3. Viết API nối backend với frontend.
  4. Nối backend với frontend.
  5. Nếu nối lỗi thì sửa backend hoặc API, coi frontend là đúng sau khi frontend đã theo contract đã thống nhất.
- Chưa code trong prompt này.
- Tạo thêm file `.txt` chứa prompt copy-paste cho các bước triển khai sau.

## Quyết định kỹ thuật đề xuất

### Backend mới

Đề xuất thêm backend mới trong repo, ví dụ `backend/HelpId.Api/`, dùng:

- ASP.NET Core Web API.
- Entity Framework Core.
- EF Core SQLite provider.
- EF Core migrations.
- JWT access token + refresh token.
- Password hash bằng cơ chế chuẩn của ASP.NET Core `PasswordHasher<T>` hoặc thư viện Argon2id nếu chấp nhận thêm dependency bảo mật chuyên dụng.

Lý do chọn ASP.NET Core + EF Core + SQLite:

- Khớp trực tiếp với quy trình code-first -> migration -> SQLite database.
- EF Core tạo migration và schema SQLite từ class/entity rõ ràng hơn so với viết SQL tay.
- EF Core LINQ mặc định parameterize query, giảm nguy cơ SQL injection khi không dùng raw SQL string concatenation.
- ASP.NET Core có middleware auth/JWT, validation, DI, test integration tương đối chuẩn.
- Dễ tách backend stateful khỏi Vercel serverless hiện tại.

Lý do không chọn các phương án khác lúc này:

- Không chọn tiếp Firebase Auth/Firestore cho tính năng này vì yêu cầu mới nói rõ backend code-first, migrate ra SQLite database.
- Không chọn Vercel serverless + SQLite file vì Vercel không phù hợp để ghi database file SQLite bền vững; production cần backend có persistent disk/volume hoặc managed SQLite-compatible service.
- Không chọn Node/Prisma vì Prisma thiên về schema file hơn code-first class/entity; vẫn tốt nhưng không khớp bằng EF Core với 5 bước người dùng mô tả.
- Không chọn Drizzle/TypeScript dù code-first khá tốt, vì auth/password/JWT/migration/test bảo mật sẽ phải tự ráp nhiều hơn trong project hiện tại.
- Không chọn PostgreSQL ngay vì người dùng yêu cầu SQLite và giai đoạn này chưa cần relational database server riêng. Nếu production sau này lớn hơn, PostgreSQL có thể là kế hoạch migration riêng.

### Frontend

Frontend ở đây gồm Android app là chính, và web public emergency profile nếu luồng public link được chuyển sang backend mới.

Android cần thêm:

- Màn hình đăng nhập.
- Màn hình đăng ký.
- Auth state khi app khởi động.
- Token storage trong `EncryptedSharedPreferences` qua `SecurePrefs` hoặc wrapper mới dùng AndroidX Security.
- Backend API client gắn `Authorization: Bearer <accessToken>`.
- Cơ chế refresh token khi access token hết hạn.
- Logout.
- Luồng offline an toàn: hồ sơ đã cache vẫn đọc được trong tình huống khẩn cấp, nhưng các API remote cần đăng nhập hợp lệ.

Web/API public profile cần quyết định trong lúc triển khai:

- Hoặc để Vercel web gọi backend mới cho `/api/profile`.
- Hoặc thay route public profile sang backend mới.
- Không để secret/backend database lộ vào Vite bundle.

## Thiết kế database code-first đề xuất

Database backend SQLite nên tách rõ auth, profile và public link. Không nhét list liên hệ y tế vào một JSON blob nếu cần validate/sort/query; nên normalize các list chính.

### Bảng `Users`

Mục đích: tài khoản đăng nhập.

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `Email` TEXT NOT NULL.
- `NormalizedEmail` TEXT NOT NULL UNIQUE.
- `PasswordHash` TEXT NOT NULL.
- `DisplayName` TEXT NULL.
- `PhoneNumber` TEXT NULL.
- `IsEmailVerified` INTEGER/BOOLEAN NOT NULL DEFAULT 0.
- `FailedLoginCount` INTEGER NOT NULL DEFAULT 0.
- `LockoutUntilUtc` TEXT/INTEGER NULL.
- `CreatedAtUtc` TEXT/INTEGER NOT NULL.
- `UpdatedAtUtc` TEXT/INTEGER NOT NULL.
- `LastLoginAtUtc` TEXT/INTEGER NULL.

Index/constraint:

- Unique index `NormalizedEmail`.
- Max length validation ở entity/API.

### Bảng `RefreshTokens`

Mục đích: quản lý phiên đăng nhập dài hạn, revoke được.

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `UserId` TEXT NOT NULL foreign key -> `Users.Id`.
- `TokenHash` TEXT NOT NULL UNIQUE.
- `CreatedAtUtc` TEXT/INTEGER NOT NULL.
- `ExpiresAtUtc` TEXT/INTEGER NOT NULL.
- `RevokedAtUtc` TEXT/INTEGER NULL.
- `ReplacedByTokenId` TEXT NULL.
- `DeviceName` TEXT NULL.
- `UserAgentHash` TEXT NULL.

Index/constraint:

- Index `UserId`.
- Unique index `TokenHash`.
- Không lưu refresh token plaintext trong database.

### Bảng `UserProfiles`

Mục đích: hồ sơ y tế chính của user.

Cột đề xuất:

- `UserId` TEXT primary key, foreign key -> `Users.Id`.
- `FullName` TEXT NOT NULL DEFAULT ''.
- `BloodGroup` TEXT NOT NULL DEFAULT ''.
- `Address` TEXT NOT NULL DEFAULT ''.
- `Language` TEXT NOT NULL DEFAULT 'en'.
- `LastUpdatedUtc` TEXT/INTEGER NOT NULL.

### Bảng `ProfileAllergies`

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `UserId` TEXT NOT NULL foreign key -> `Users.Id`.
- `Value` TEXT NOT NULL.
- `SortOrder` INTEGER NOT NULL DEFAULT 0.

### Bảng `MedicalNotes`

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `UserId` TEXT NOT NULL foreign key -> `Users.Id`.
- `Value` TEXT NOT NULL.
- `SortOrder` INTEGER NOT NULL DEFAULT 0.

### Bảng `EmergencyContacts`

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `UserId` TEXT NOT NULL foreign key -> `Users.Id`.
- `Name` TEXT NOT NULL.
- `Phone` TEXT NOT NULL.
- `Relationship` TEXT NULL.
- `SortOrder` INTEGER NOT NULL DEFAULT 0.

### Bảng `PublicProfileLinks`

Mục đích: thay Firestore `publicKeys/{key}`.

Cột đề xuất:

- `PublicKey` TEXT primary key, format `HID-*`.
- `UserId` TEXT NOT NULL foreign key -> `Users.Id`.
- `CreatedAtUtc` TEXT/INTEGER NOT NULL.
- `UpdatedAtUtc` TEXT/INTEGER NOT NULL.
- `RevokedAtUtc` TEXT/INTEGER NULL.

JWT public profile vẫn nên ngắn hạn, ví dụ 3 giờ như hiện tại.

### Bảng optional `AuditEvents`

Chỉ thêm nếu cần trace bảo mật tối thiểu.

Cột đề xuất:

- `Id` TEXT primary key, UUID.
- `UserId` TEXT NULL.
- `EventType` TEXT NOT NULL.
- `CreatedAtUtc` TEXT/INTEGER NOT NULL.
- `IpHash` TEXT NULL.
- `UserAgentHash` TEXT NULL.

Không log dữ liệu y tế, số điện thoại, vị trí, access token, refresh token, JWT public profile hoặc password.

## Thiết kế chống SQL injection

Lưu ý quan trọng: không có “database chống SQL injection” theo nghĩa tự bản thân SQLite chặn được mọi injection. SQL injection được phòng ở tầng backend bằng cách không ghép chuỗi SQL từ input người dùng.

Quy tắc bắt buộc:

- Dùng EF Core LINQ cho query CRUD chính.
- Cấm string interpolation/concatenation vào raw SQL.
- Nếu bắt buộc raw SQL, chỉ dùng parameterized API, ví dụ `FromSqlInterpolated` hoặc tham số SQL rõ ràng, không dùng `FromSqlRaw` với input chưa kiểm soát.
- Validation DTO bằng whitelist, max length, format email/phone/public key.
- Unique index và foreign key ở database để backend không chỉ dựa vào validation frontend.
- Không trả raw exception SQL ra client.
- Viết test injection với input như `' OR 1=1 --`, `x'); DROP TABLE Users; --`, email có quote, public key sai format.
- Luôn hash password và refresh token; không bao giờ lưu plaintext password/token.
- Bật SQLite foreign keys trong connection nếu EF Core setup chưa bật.

## API contract đề xuất

Dùng prefix version, ví dụ `/api/v1`.

Auth:

- `POST /api/v1/auth/register`
  - Body: `email`, `password`, `displayName?`.
  - Response: `accessToken`, `refreshToken`, `user`.
- `POST /api/v1/auth/login`
  - Body: `email`, `password`.
  - Response: `accessToken`, `refreshToken`, `user`.
- `POST /api/v1/auth/refresh`
  - Body: `refreshToken`.
  - Response: token pair mới.
- `POST /api/v1/auth/logout`
  - Auth required hoặc refresh token body; revoke token.
- `GET /api/v1/auth/me`
  - Auth required; trả user hiện tại.

Profile:

- `GET /api/v1/profile`
  - Auth required; trả hồ sơ đầy đủ của user.
- `PUT /api/v1/profile`
  - Auth required; cập nhật hồ sơ, allergies, medical notes, emergency contacts.

Public emergency link:

- `POST /api/v1/emergency-links/mint`
  - Auth required; tạo/tái dùng `HID-*`, ký JWT ngắn hạn, trả URL public.
- `GET /api/v1/public/profile?key=...&t=...`
  - Không cần auth user; cần public key và JWT; trả whitelist field y tế cho người quét QR.

Health:

- `GET /health`
  - Không trả dữ liệu nhạy cảm.

## Quy trình frontend đề xuất

Frontend Android nên làm theo các bước:

1. Thiết kế route/state auth trong `MainActivity` theo pattern navigation thủ công hiện có.
2. Thêm màn hình Login và Register bằng Jetpack Compose, dùng `stringResource`, cập nhật mọi locale nếu thêm key.
3. Thêm `AuthTokenStore` dùng secure prefs/encrypted prefs để lưu access token, refresh token, user id, expiry.
4. Thêm API client cho backend. Để giữ thay đổi hẹp, có thể bắt đầu bằng `HttpURLConnection` giống `mintEmergencyLink`; nếu API phức tạp quá thì tách prompt thêm Retrofit/OkHttp.
5. Thêm `AuthRepository` gọi register/login/refresh/logout/me.
6. Startup flow: nếu có token hợp lệ thì vào app; nếu refresh được thì vào app; nếu không thì vào Login/Register. Trường hợp khẩn cấp cần vẫn cho xem profile local đã cache nếu có.
7. Sau khi auth ổn, chuyển profile sync từ FirebaseRepository sang backend repository hoặc tạo lớp adapter để tránh sửa màn hình quá rộng trong một lượt.
8. Chuyển QR/NFC/SOS fallback mint link sang backend `/emergency-links/mint`.
9. Đảm bảo logout không xóa nhầm hồ sơ y tế local nếu cần offline emergency; nếu xóa local phải có xác nhận rõ ràng.
10. Chạy build/test/lint và kiểm tra thủ công login/register/offline/token hết hạn.

## Quy trình backend theo 5 bước của người dùng

### Bước 1: Viết code-first model

- Tạo project backend.
- Tạo entity class cho các bảng đã nêu.
- Tạo `DbContext` và relationship/index/constraint.
- Tạo DTO request/response.
- Tạo service password hashing, token service, public key service.
- Viết test cho validation và mapping nếu có thể.

### Bước 2: Chạy migrate ra SQLite database

- Cấu hình connection string SQLite.
- Chạy `dotnet ef migrations add InitialAuthSchema`.
- Chạy `dotnet ef database update`.
- Kiểm tra file SQLite sinh ra đúng table/index/foreign key.
- Không commit database production có dữ liệu thật.

### Bước 3: Viết API nối backend với frontend

- Implement auth endpoints.
- Implement profile endpoints.
- Implement emergency link/public profile endpoints.
- Add JWT middleware.
- Add CORS đúng domain nếu web gọi backend.
- Add request validation, status code rõ ràng, no-store cho endpoint nhạy cảm.
- Add tests chống SQL injection và auth flow.

### Bước 4: Nối backend với frontend

- Android gọi register/login/refresh/logout/me.
- Android lưu token bảo mật.
- Android attach Bearer token cho profile/mint link.
- Android chuyển profile sync và QR/SOS fallback sang backend mới.
- Web public emergency page gọi endpoint public profile mới hoặc Vercel API proxy sang backend.

### Bước 5: Nếu nối lỗi thì sửa backend/API

- Sau khi frontend đã theo contract đã thống nhất, coi frontend là đúng trong vòng fix integration.
- Nếu lỗi do field name/status code/shape response/header/token refresh, sửa backend hoặc API adapter.
- Chỉ sửa frontend nếu phát hiện frontend chưa làm đúng contract đã chốt, có crash UI, hoặc có lỗi bảo mật client-side rõ ràng; khi đó phải nói rõ vì sao ngoại lệ.

## Tác động tới Firebase hiện tại

Không nên xóa Firebase ngay trong prompt đầu tiên. Nên triển khai theo hướng an toàn:

1. Thêm backend auth + SQLite song song.
2. Android dùng backend auth cho tài khoản mới.
3. Chuyển profile CRUD và mint public link sang backend mới.
4. Khi luồng mới ổn, tạo task riêng để gỡ Firebase Auth/Firestore hoặc viết migration dữ liệu nếu cần giữ user cũ.

Lý do: xóa Firebase ngay sẽ chạm vào nhiều luồng SOS/QR/public profile cùng lúc, rủi ro cao.

## Kiểm chứng bắt buộc khi triển khai

Backend:

```bash
dotnet build backend/HelpId.Api/HelpId.Api.csproj
dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj
dotnet ef migrations list --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
dotnet ef database update --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Android:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Web nếu sửa `helper-id`:

```bash
cd helper-id
npm run build
npx tsc --noEmit
```

Toàn repo:

```bash
git diff --check
```

Manual test:

- Register account mới.
- Login đúng password.
- Login sai password không lộ thông tin nhạy cảm.
- Refresh token hoạt động.
- Logout revoke refresh token.
- App restart vẫn giữ session nếu token hợp lệ.
- Token hết hạn thì refresh hoặc quay về login.
- Profile vẫn đọc được offline từ Room khi đã cache.
- QR/public profile vẫn chỉ trả whitelist field.
- Input SQL injection không bypass login, không phá database.

## Tài liệu/UML cần cập nhật khi triển khai thật

Vì tính năng đăng ký/đăng nhập làm thay đổi use-case, khi bắt đầu code phải cập nhật:

- `harness-engineering/uml-use-case.puml`.
- Xóa ảnh cũ `harness-engineering/uml-use-case.png`.
- Render lại ảnh UML mới.
- Cập nhật `CHANGELOG.md` sau mỗi prompt có sửa code/tài liệu/cấu hình.

## Ngoài phạm vi của prompt kế hoạch này

- Chưa tạo project backend.
- Chưa sửa Android UI.
- Chưa sửa Vercel API/web.
- Chưa chạy migration.
- Chưa thay database thật.
- Chưa xóa Firebase.
