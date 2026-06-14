# Contract đăng ký/đăng nhập HelpID

Thời điểm chốt contract: 14/06/2026 18:30:18
Cập nhật vận hành sau khi backend/auth mới chạy ổn: 15/06/2026

Tài liệu này chốt contract và runbook vận hành cho tính năng đăng ký/đăng nhập. Contract dựa trên hiện trạng repo:

- Android hiện có Login/Register, token store và repository gọi backend mới; Room `helpid_database` vẫn là cache offline-first.
- Backend mới nằm ở `backend/HelpId.Api`, dùng ASP.NET Core, EF Core SQLite, JWT access token, refresh token rotation/revoke và public profile JWT 3 giờ.
- Web route public `/e/:publicKey` vẫn gọi `/api/profile?key=...&t=...`; serverless `/api/profile` proxy sang backend mới và sanitize whitelist.
- API Vercel vẫn còn `/api/mint` Firebase legacy và `/api/gemini`; chưa xóa Firebase/Firestore trong lượt này.

## Mục tiêu và phạm vi

Mục tiêu:

- Thêm tài khoản user-facing: đăng ký, đăng nhập, refresh token, logout, xem session hiện tại.
- Thêm backend code-first có SQLite migration và API rõ ràng cho Android/web.
- Chốt schema database, phân quyền dữ liệu, token flow, lỗi validation và rule chống SQL injection.
- Giữ an toàn cho tình huống khẩn cấp: nếu API/auth lỗi hoặc offline, Android vẫn đọc được hồ sơ đã cache trong Room.

Ngoài phạm vi của contract/runbook này:

- Chưa gỡ Firebase Auth/Firestore legacy.
- Chưa migrate dữ liệu Firebase cũ sang SQLite backend.
- Chưa thay `/api/mint` legacy bằng backend trong mọi client cũ.
- Chưa triển khai cơ chế reset password/email verification production.

## Backend tech được chốt

Backend mới đặt trong repo tại `backend/HelpId.Api/`.

Stack bắt buộc:

- ASP.NET Core Web API.
- Entity Framework Core theo hướng code-first.
- EF Core SQLite provider.
- EF Core migrations.
- JWT access token.
- Refresh token dạng random opaque token, chỉ lưu hash trong database.
- Password hashing bằng `PasswordHasher<TUser>` của ASP.NET Core Identity hoặc Argon2id nếu prompt triển khai sau chấp nhận thêm dependency bảo mật chuyên dụng.

Lý do chốt:

- Khớp quy trình code-first -> migration -> SQLite database.
- EF Core LINQ parameterize query mặc định, giảm rủi ro SQL injection.
- ASP.NET Core có middleware auth/authorization/policy/test integration tốt.
- Tách backend stateful khỏi Vercel serverless hiện tại, vì Vercel không phù hợp để ghi SQLite file bền vững.

Config/secret backend không commit vào repo:

- `ConnectionStrings__HelpIdDb`: SQLite database path cho môi trường chạy.
- `HELPID_AUTH_JWT_SIGNING_KEY`: secret ký access token, tối thiểu 32 byte, được resolve qua `AuthJwt:SigningKeyEnvironmentVariable`.
- `HELPID_PROFILE_JWT_SIGNING_KEY`: secret ký public profile JWT 3 giờ, tối thiểu 32 byte, được resolve qua `ProfileJwt:SigningKeyEnvironmentVariable`.
- `AuthJwt__Issuer`, `AuthJwt__Audience`: override issuer/audience nếu cần.
- `PublicWeb__BaseUrl`: ví dụ `https://helper-id.vercel.app`.

## Database schema

SQLite không có phân quyền user/role ở mức `GRANT/REVOKE` như PostgreSQL/MySQL. Phân quyền dữ liệu được enforce ở API layer, authorization policy và ownership check. Database vẫn phải có foreign key, unique index, check/max length qua EF configuration và migration để backend không chỉ dựa vào frontend.

Quy ước chung:

- `TEXT` cho UUID/ULID, email, token hash, timestamp ISO-8601 UTC nếu không dùng integer epoch.
- `INTEGER` cho boolean trong SQLite (`0/1`) và sort order.
- Tất cả timestamp dùng UTC.
- Bật foreign key SQLite trong connection.
- Không lưu plaintext password, access token, refresh token, public profile JWT.

### `Users`

Mục đích: tài khoản đăng nhập.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key, UUID/ULID |
| `Email` | TEXT | no | Email hiển thị, max 254 |
| `NormalizedEmail` | TEXT | no | Upper/lower normalized để unique |
| `PasswordHash` | TEXT | no | Hash từ password hasher |
| `DisplayName` | TEXT | yes | Max 80 |
| `PhoneNumber` | TEXT | yes | E.164 nếu dùng, max 16 |
| `IsEmailVerified` | INTEGER | no | Default `0` |
| `FailedLoginCount` | INTEGER | no | Default `0` |
| `LockoutUntilUtc` | TEXT | yes | Khóa tạm thời nếu brute force |
| `SecurityStamp` | TEXT | no | Đổi khi reset password/revoke toàn bộ phiên |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |
| `LastLoginAtUtc` | TEXT | yes | UTC |
| `DeletedAtUtc` | TEXT | yes | Soft delete nếu cần |

Indexes/constraints:

- Primary key `PK_Users(Id)`.
- Unique index `UX_Users_NormalizedEmail`.
- Index `IX_Users_DeletedAtUtc`.

### `Roles`

Mục đích: role backend.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `Name` | TEXT | no | Ví dụ `User`, `Admin` |
| `NormalizedName` | TEXT | no | Unique |
| `CreatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Unique index `UX_Roles_NormalizedName`.

Seed mặc định:

- `User`: role cho tài khoản app.
- `Admin`: chỉ dùng cho endpoint vận hành nếu sau này có, không tự động có quyền đọc hồ sơ y tế plaintext hàng loạt.

### `Permissions`

Mục đích: permission policy rõ ràng, phục vụ authorization test.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `Code` | TEXT | no | Ví dụ `profile:read:self` |
| `Description` | TEXT | yes | Mô tả ngắn |

Indexes/constraints:

- Unique index `UX_Permissions_Code`.

Permission seed tối thiểu:

- `profile:read:self`
- `profile:write:self`
- `emergency_link:mint:self`
- `auth:session:self`
- `admin:metadata:read`

### `UserRoles`

Mục đích: gán role cho user.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `UserId` | TEXT | no | FK `Users.Id` |
| `RoleId` | TEXT | no | FK `Roles.Id` |
| `CreatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Composite primary key `(UserId, RoleId)`.
- Index `IX_UserRoles_RoleId`.
- Cascade delete khi user bị xóa mềm không tự xóa; nếu hard delete dev/test thì cascade được chấp nhận.

### `RolePermissions`

Mục đích: map role -> permission.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `RoleId` | TEXT | no | FK `Roles.Id` |
| `PermissionId` | TEXT | no | FK `Permissions.Id` |
| `CreatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Composite primary key `(RoleId, PermissionId)`.
- Index `IX_RolePermissions_PermissionId`.

### `RefreshTokens`

Mục đích: quản lý phiên dài hạn và revoke được.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `UserId` | TEXT | no | FK `Users.Id` |
| `TokenHash` | TEXT | no | Hash của refresh token, unique |
| `TokenFamilyId` | TEXT | no | Dùng phát hiện reuse/rotation |
| `CreatedAtUtc` | TEXT | no | UTC |
| `ExpiresAtUtc` | TEXT | no | UTC |
| `RevokedAtUtc` | TEXT | yes | UTC |
| `ReplacedByTokenId` | TEXT | yes | FK self-reference |
| `DeviceName` | TEXT | yes | Max 120, không tin tuyệt đối |
| `UserAgentHash` | TEXT | yes | Hash, không lưu raw user-agent dài |
| `CreatedByIpHash` | TEXT | yes | Hash IP nếu cần audit, không lưu raw IP |

Indexes/constraints:

- Unique index `UX_RefreshTokens_TokenHash`.
- Index `IX_RefreshTokens_UserId`.
- Index `IX_RefreshTokens_TokenFamilyId`.
- Index `IX_RefreshTokens_ExpiresAtUtc`.

Quy tắc:

- Refresh token plaintext chỉ trả về client một lần.
- Rotation mỗi lần refresh: token cũ bị revoke, token mới được tạo.
- Nếu phát hiện reuse token đã bị thay thế, revoke toàn bộ `TokenFamilyId`.

### `UserProfiles`

Mục đích: hồ sơ y tế chính của user trên backend mới.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `UserId` | TEXT | no | Primary key, FK `Users.Id` |
| `FullName` | TEXT | no | Max 80, default `''` |
| `BloodGroup` | TEXT | no | Enum `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, hoặc `''` khi chưa khai báo |
| `Address` | TEXT | no | Max 120, default `''` để khớp Android hiện tại |
| `Language` | TEXT | no | Max 8, default `en` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |
| `LastUpdatedUtc` | TEXT | no | UTC, tương đương `lastUpdated` domain |

Indexes/constraints:

- Primary key `PK_UserProfiles(UserId)`.
- FK `UserProfiles.UserId -> Users.Id`.

### `ProfileAllergies`

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `UserId` | TEXT | no | FK `Users.Id` |
| `Value` | TEXT | no | Max 120 |
| `SortOrder` | INTEGER | no | Default `0` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Index `IX_ProfileAllergies_UserId_SortOrder`.
- Không unique theo `Value` để không phá dữ liệu user; frontend có thể tự gợi ý bỏ trùng.

### `MedicalNotes`

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `UserId` | TEXT | no | FK `Users.Id` |
| `Value` | TEXT | no | Max 500 |
| `SortOrder` | INTEGER | no | Default `0` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Index `IX_MedicalNotes_UserId_SortOrder`.

### `EmergencyContacts`

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `UserId` | TEXT | no | FK `Users.Id` |
| `Name` | TEXT | no | Max 80 |
| `Phone` | TEXT | no | E.164, max 16 |
| `Relationship` | TEXT | yes | Max 80, chưa bắt Android dùng |
| `SortOrder` | INTEGER | no | Default `0` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |

Indexes/constraints:

- Index `IX_EmergencyContacts_UserId_SortOrder`.
- Không unique phone toàn hệ thống vì nhiều user có thể cùng contact.

### `PublicProfileLinks`

Mục đích: thay mapping Firestore `publicKeys/{publicKey}` trong backend mới.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `PublicKey` | TEXT | no | Primary key, format `HID-*` |
| `UserId` | TEXT | no | FK `Users.Id` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `UpdatedAtUtc` | TEXT | no | UTC |
| `LastMintedAtUtc` | TEXT | yes | UTC |
| `RevokedAtUtc` | TEXT | yes | UTC, link bị revoke thì public profile trả 404/410 |

Indexes/constraints:

- Primary key `PK_PublicProfileLinks(PublicKey)`.
- Index `IX_PublicProfileLinks_UserId`.
- Check/validation format ở API và service: `^HID-[A-Z0-9_-]{8,64}$`.

### `AuditEvents`

Mục đích: audit bảo mật tối thiểu, không ghi dữ liệu y tế/PII/token.

| Cột | Type | Null | Ghi chú |
| --- | --- | --- | --- |
| `Id` | TEXT | no | Primary key |
| `UserId` | TEXT | yes | FK `Users.Id`, nullable cho login fail |
| `EventType` | TEXT | no | Ví dụ `auth.login.failed` |
| `ReasonCode` | TEXT | yes | Ví dụ `invalid_credentials` |
| `Success` | INTEGER | no | `0/1` |
| `CreatedAtUtc` | TEXT | no | UTC |
| `IpHash` | TEXT | yes | Hash, không raw IP |
| `UserAgentHash` | TEXT | yes | Hash |

Indexes/constraints:

- Index `IX_AuditEvents_UserId_CreatedAtUtc`.
- Index `IX_AuditEvents_EventType_CreatedAtUtc`.

Không được ghi vào audit:

- Password, password hash.
- Access token, refresh token, public profile JWT.
- Tên, địa chỉ, dị ứng, ghi chú y tế, số điện thoại, vị trí.

## Phân quyền dữ liệu

Actor và quyền:

- Anonymous web visitor: chỉ xem marketing routes.
- Người dùng Android chưa đăng nhập: có thể xem hồ sơ local đã cache nếu app policy cho phép emergency local mode; không được gọi API private.
- User đã đăng nhập: chỉ đọc/sửa profile, contact, allergy, note, public link của chính mình.
- Admin: chỉ dùng cho metadata/ops endpoint nếu có; không mặc định được đọc hồ sơ y tế plaintext hàng loạt trong v1.
- Người phản hồi/cứu hộ: chỉ đọc public profile qua `publicKey + public JWT` còn hạn và chỉ nhận field whitelist.
- Vercel API proxy: chỉ là proxy server-side cho public profile nếu giữ URL cũ; không giữ secret trong Vite bundle.

Quy tắc bắt buộc:

- Backend không tin `userId` trong body/query của client cho endpoint private.
- User id thực thi lấy từ claim `sub` trong access JWT đã verify.
- Mọi query profile/private data phải filter theo `UserId == currentUserId`.
- Cross-user access trả `404` nếu nên che sự tồn tại tài nguyên, hoặc `403` nếu tài nguyên đã được xác định từ token chính user nhưng thiếu permission.
- Public profile không trả `UserId`, email, role, token, audit, metadata nội bộ.
- Public profile chỉ trả: `name`, `bloodGroup`, `allergies`, `emergencyContacts`, `address`, `medicalNotes`.
- Logout không xóa Room cache trên Android nếu chưa có xác nhận rõ ràng, để không phá tình huống khẩn cấp offline.

## Token flow

### Access token

- JWT ký bằng secret từ `HELPID_AUTH_JWT_SIGNING_KEY`.
- Lifetime: 15 phút.
- Claims tối thiểu: `sub`, `jti`, `iat`, `exp`, `iss`, `aud`, `roles`, `security_stamp`.
- Không đưa dữ liệu y tế, số điện thoại, địa chỉ, refresh token hoặc public key vào access token.
- Android lưu access token trong `AuthTokenStore` dùng `SecurePrefs.create(...)`.

### Refresh token

- Random opaque token ít nhất 32 bytes entropy, encode base64url.
- Lifetime mặc định: 30 ngày.
- Database chỉ lưu `TokenHash`, không lưu plaintext.
- Mỗi lần refresh phải rotate token.
- Refresh token cũ bị revoke và trỏ `ReplacedByTokenId`.
- Reuse token cũ sau rotation phải revoke cả `TokenFamilyId` và trả `401`.
- Android refresh một lần khi API private trả `401` do access token hết hạn, sau đó retry request đúng một lần.

### Public profile token

- JWT riêng ký bằng secret từ `HELPID_PROFILE_JWT_SIGNING_KEY`, không dùng chung access token secret.
- Lifetime: 3 giờ, giữ tương thích với Vercel API hiện tại.
- Payload tối thiểu: `{ "k": "<publicKey>", "typ": "public_profile" }`.
- Public profile endpoint verify token, verify `payload.k == key`, verify link chưa revoke.
- QR/NFC/public URL chứa token ngắn hạn; nếu hết hạn, Android cần mint lại link mới.

### Startup Android

1. Đọc token store.
2. Nếu access token còn hạn, vào app và có thể gọi `/auth/me` hoặc `/profile`.
3. Nếu access token hết hạn nhưng có refresh token, gọi `/auth/refresh`.
4. Nếu refresh thành công, lưu token pair mới và vào app.
5. Nếu refresh fail/offline, chuyển sang trạng thái cần đăng nhập cho API remote, nhưng EmergencyScreen vẫn được phép đọc Room cache nếu có hồ sơ local.
6. Không log token hoặc email/password trong bất kỳ nhánh nào.

## API conventions

Base path backend mới: `/api/v1`.

Headers chung:

- Request JSON: `Content-Type: application/json`.
- Client nên gửi `Accept: application/json`.
- Endpoint private: `Authorization: Bearer <accessToken>`.
- Response nhạy cảm: `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.

Error envelope:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed.",
    "traceId": "00-opaque-trace-id",
    "fields": {
      "email": ["email.invalid"]
    }
  }
}
```

Không trả raw SQL exception, stack trace, secret, token hoặc dữ liệu y tế trong error.

## API auth

### `POST /api/v1/auth/register`

Request:

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple",
  "displayName": "Nguyen Van A",
  "deviceName": "Pixel 8"
}
```

Validation:

- `email`: required, max 254, format email, normalized unique.
- `password`: required, min 12, max 128, không trim âm thầm.
- `displayName`: optional, max 80.
- `deviceName`: optional, max 120.

Success `201 Created`:

```json
{
  "tokenType": "Bearer",
  "accessToken": "opaque.jwt.access",
  "accessTokenExpiresAtUtc": "2026-06-14T10:15:00Z",
  "refreshToken": "opaque-refresh-token",
  "refreshTokenExpiresAtUtc": "2026-07-14T10:00:00Z",
  "user": {
    "id": "usr_...",
    "email": "user@example.com",
    "displayName": "Nguyen Van A",
    "roles": ["User"]
  }
}
```

Status codes:

- `201`: tạo tài khoản thành công.
- `400`: JSON sai cú pháp hoặc content type không hợp lệ.
- `409`: email đã tồn tại.
- `422`: validation fail.
- `429`: rate limit.
- `500`: lỗi nội bộ, không lộ chi tiết.

Side effects:

- Tạo `Users`.
- Tạo `UserProfiles` rỗng/default.
- Gán role `User`.
- Tạo refresh token hash.
- Ghi audit event không chứa PII nhạy cảm.

### `POST /api/v1/auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple",
  "deviceName": "Pixel 8"
}
```

Success `200 OK`: cùng shape với register.

Status codes:

- `200`: đăng nhập thành công.
- `400`: JSON sai.
- `401`: email/password sai, dùng message chung `Invalid email or password.`
- `423`: tài khoản đang lockout.
- `422`: validation fail.
- `429`: rate limit.
- `500`: lỗi nội bộ.

Quy tắc:

- Không tiết lộ email có tồn tại hay không trong lỗi `401`.
- Tăng `FailedLoginCount` và set `LockoutUntilUtc` theo policy brute-force.
- Reset failed count khi login thành công.

### `POST /api/v1/auth/refresh`

Request:

```json
{
  "refreshToken": "opaque-refresh-token",
  "deviceName": "Pixel 8"
}
```

Success `200 OK`: token pair mới cùng shape với login/register.

Status codes:

- `200`: refresh thành công.
- `400`: JSON sai.
- `401`: refresh token invalid/expired/revoked/reuse.
- `422`: thiếu refresh token hoặc token quá dài.
- `500`: lỗi nội bộ.

### `POST /api/v1/auth/logout`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Success:

- `204 No Content`: token đã revoke hoặc vốn đã không còn active. Endpoint nên idempotent với token thuộc user/session hợp lệ.

Status codes:

- `204`: logout/revoke thành công.
- `400`: JSON sai.
- `401`: token không hợp lệ theo policy backend.
- `422`: thiếu refresh token.
- `500`: lỗi nội bộ.

Android sau `204`:

- Xóa access/refresh token trong `AuthTokenStore`.
- Không tự xóa Room profile nếu chưa có xác nhận riêng.

### `GET /api/v1/auth/me`

Headers: `Authorization: Bearer <accessToken>`.

Success `200 OK`:

```json
{
  "user": {
    "id": "usr_...",
    "email": "user@example.com",
    "displayName": "Nguyen Van A",
    "roles": ["User"]
  }
}
```

Status codes:

- `200`: token hợp lệ.
- `401`: thiếu/invalid/expired access token.
- `403`: token hợp lệ nhưng thiếu permission cần thiết.

## API profile

### `GET /api/v1/profile`

Headers: `Authorization: Bearer <accessToken>`.

Success `200 OK`:

```json
{
  "profile": {
    "userId": "usr_...",
    "name": "Nguyen Van A",
    "bloodGroup": "O+",
    "address": "",
    "allergies": ["Penicillin"],
    "medicalNotes": ["Diabetes"],
    "emergencyContacts": [
      {
        "id": "cnt_...",
        "name": "Father",
        "phone": "+84901234567",
        "relationship": "Father"
      }
    ],
    "language": "vi",
    "lastUpdated": 1781431200000
  }
}
```

Status codes:

- `200`: lấy profile thành công.
- `401`: thiếu/invalid/expired access token.
- `403`: thiếu permission.
- `404`: user/profile không tồn tại hoặc bị xóa.

### `PUT /api/v1/profile`

Headers: `Authorization: Bearer <accessToken>`.

Request:

```json
{
  "name": "Nguyen Van A",
  "bloodGroup": "O+",
  "address": "District 1",
  "allergies": ["Penicillin"],
  "medicalNotes": ["Diabetes"],
  "emergencyContacts": [
    {
      "name": "Father",
      "phone": "+84901234567",
      "relationship": "Father"
    }
  ],
  "language": "vi"
}
```

Validation:

- `name`: required khi muốn SOS thật, max 80; API có thể cho rỗng để user hoàn thiện sau nhưng Android SOS phải chặn profile incomplete.
- `bloodGroup`: required khi muốn SOS thật; nếu gửi không rỗng phải thuộc enum.
- `address`: max 120.
- `allergies`: max 20 items, mỗi item max 120.
- `medicalNotes`: max 50 items, mỗi item max 500.
- `emergencyContacts`: max 10 items; nếu item có name/phone thì cả hai phải hợp lệ.
- `phone`: E.164, max 16.
- `language`: `en`, `es`, `hi`, `fr`, `de`, `vi`.

Success `200 OK`: trả profile mới cùng shape với `GET /profile`.

Status codes:

- `200`: lưu thành công.
- `400`: JSON sai.
- `401`: thiếu/invalid/expired access token.
- `403`: thiếu permission.
- `422`: validation fail.
- `500`: lỗi nội bộ.

## API emergency link và public profile

### `POST /api/v1/emergency-links/mint`

Headers: `Authorization: Bearer <accessToken>`.

Request:

```json
{
  "publicKey": "HID-ABCDEFGH"
}
```

`publicKey` optional. Nếu Android gửi key đã cache, backend chỉ tái dùng nếu key thuộc chính user. Nếu không gửi, backend cấp key mới.

Success `200 OK`:

```json
{
  "publicKey": "HID-ABCDEFGH",
  "expiresInSeconds": 10800,
  "token": "opaque.public.jwt",
  "url": "https://helper-id.vercel.app/e/HID-ABCDEFGH?t=opaque.public.jwt"
}
```

Status codes:

- `200`: mint thành công.
- `400`: JSON sai hoặc public key format sai.
- `401`: thiếu/invalid/expired access token.
- `403`: thiếu permission.
- `409`: public key đã thuộc user khác.
- `422`: request không hợp lệ.
- `503`: không cấp được key sau retry collision.

### `GET /api/v1/public/profile?key=...&t=...`

Không dùng access token user. Dùng public key và public profile JWT.

Success `200 OK`:

```json
{
  "key": "HID-ABCDEFGH",
  "profile": {
    "name": "Nguyen Van A",
    "bloodGroup": "O+",
    "allergies": ["Penicillin"],
    "emergencyContacts": [
      {
        "name": "Father",
        "phone": "+84901234567"
      }
    ],
    "address": "District 1",
    "medicalNotes": ["Diabetes"]
  }
}
```

Status codes:

- `200`: trả public profile whitelist.
- `400`: thiếu `key`/`t`, key sai format, token quá dài.
- `401`: token invalid/expired hoặc không khớp key.
- `404`: public key không tồn tại, link đã revoke, profile không tồn tại.
- `410`: có thể dùng cho link đã revoke nếu muốn phân biệt rõ.
- `500`: lỗi nội bộ.

Headers bắt buộc:

- `Cache-Control: no-store`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`
- Nếu qua Vercel, giữ `X-Robots-Tag: noindex, nofollow, noarchive`.

## API health

### `GET /health`

Success `200 OK`:

```json
{
  "status": "ok"
}
```

Không trả database path, secret, version dependency chi tiết hoặc dữ liệu user.

## Validation error codes

Backend trả `422` với `error.code = "VALIDATION_ERROR"` cho lỗi semantic. `fields` dùng code ổn định để Android map ra string resource sau này.

| Field | Code | Khi nào |
| --- | --- | --- |
| `email` | `email.required` | Thiếu email |
| `email` | `email.invalid` | Sai format |
| `email` | `email.too_long` | Hơn 254 ký tự |
| `password` | `password.required` | Thiếu password |
| `password` | `password.too_short` | Ít hơn 12 ký tự |
| `password` | `password.too_long` | Hơn 128 ký tự |
| `displayName` | `display_name.too_long` | Hơn 80 ký tự |
| `name` | `name.required` | Required theo API action |
| `name` | `name.too_long` | Hơn 80 ký tự |
| `bloodGroup` | `blood_group.invalid` | Không thuộc enum |
| `address` | `address.too_long` | Hơn 120 ký tự |
| `allergies` | `allergies.too_many` | Hơn 20 item |
| `allergies[i]` | `allergy.too_long` | Hơn 120 ký tự |
| `medicalNotes` | `medical_notes.too_many` | Hơn 50 item |
| `medicalNotes[i]` | `medical_note.too_long` | Hơn 500 ký tự |
| `emergencyContacts` | `contacts.too_many` | Hơn 10 item |
| `emergencyContacts[i].name` | `contact_name.required` | Có phone nhưng thiếu name |
| `emergencyContacts[i].name` | `contact_name.too_long` | Hơn 80 ký tự |
| `emergencyContacts[i].phone` | `contact_phone.required` | Có name nhưng thiếu phone |
| `emergencyContacts[i].phone` | `contact_phone.invalid` | Không phải E.164 |
| `language` | `language.unsupported` | Không thuộc locale hỗ trợ |
| `publicKey` | `public_key.invalid` | Không khớp `HID-*` |
| `refreshToken` | `refresh_token.required` | Thiếu token |
| `refreshToken` | `refresh_token.invalid` | Token quá dài/sai shape |

Các lỗi auth không dùng field validation:

- `INVALID_CREDENTIALS`
- `ACCOUNT_LOCKED`
- `ACCESS_TOKEN_EXPIRED`
- `REFRESH_TOKEN_INVALID`
- `REFRESH_TOKEN_REUSED`
- `FORBIDDEN`
- `PUBLIC_PROFILE_TOKEN_INVALID`
- `PUBLIC_PROFILE_TOKEN_EXPIRED`

## Quy tắc chống SQL injection

Bắt buộc:

- Dùng EF Core LINQ cho CRUD chính.
- Cấm ghép chuỗi SQL từ input user.
- Cấm `FromSqlRaw`, `ExecuteSqlRaw` với input user chưa parameterize.
- Nếu bắt buộc raw SQL, dùng API parameterized như `FromSqlInterpolated` hoặc tham số rõ ràng.
- Không cho client gửi tên bảng, tên cột hoặc raw sort expression. Nếu cần sort/filter, dùng allowlist enum server-side.
- Validation DTO bằng whitelist, max length, regex/enum rõ ràng.
- Database có unique index/foreign key/check hợp lý để chống race condition và dữ liệu sai.
- Không trả raw exception SQL ra client.
- Test injection với input như `' OR 1=1 --`, `x'); DROP TABLE Users; --`, email có quote, public key sai format.
- Không log request body chứa password, token, dữ liệu y tế, số điện thoại, vị trí.
- SQLite database file production phải nằm ngoài web root, file permission hạn chế theo user chạy backend.

Không được coi các việc sau là đủ chống SQL injection:

- Chỉ sanitize frontend.
- Chỉ escape quote thủ công.
- Chỉ dùng regex public key nhưng vẫn raw SQL cho email/password.
- Chỉ dựa vào SQLite để tự chặn injection.

## Android gọi API như thế nào

Hiện Android đã có lớp auth/token/backend API theo contract này:

- `AuthTokenStore`: dùng `SecurePrefs.create(context, "auth_tokens")`, lưu access token, refresh token, user id, expiry UTC.
- `HelpIdApiClient` hoặc tương đương: có base URL cấu hình được. Dev emulator dùng URL riêng; production dùng HTTPS. Do manifest đang `usesCleartextTraffic="false"`, nếu dev cần HTTP phải có cấu hình debug riêng, không bật cleartext toàn app production.
- Có thể bắt đầu bằng `HttpURLConnection` để khớp `FirebaseRepository.mintEmergencyLink()` hiện tại; chỉ thêm Retrofit/OkHttp nếu prompt triển khai sau chấp nhận dependency.
- Mọi request JSON set `Content-Type` và `Accept`.
- Private request attach `Authorization: Bearer <accessToken>`.
- Khi response `401` từ private API:
  1. Nếu chưa retry, gọi `/auth/refresh`.
  2. Nếu refresh thành công, lưu token pair mới và retry request một lần.
  3. Nếu refresh fail, clear token và chuyển auth state về login cho remote API.
  4. EmergencyScreen vẫn được đọc Room cache nếu có.
- Khi `422`, Android map `fields` sang string resource; không hiển thị raw exception dài.
- Khi `409` register duplicate email, hiển thị lỗi email đã được dùng.
- Khi offline/backend timeout, không crash; profile đã cache vẫn hiện, QR/NFC/SOS fallback link không render URL hỏng.
- Không log email/password/token/profile body/phone/location.

Mapping Android hiện tại sang backend mới:

- `initializeUser()` hiện anonymous Firebase -> auth startup mới.
- `getCachedUserProfile()` giữ Room read offline-first.
- `getUserProfile()` remote trong luồng mới gọi `GET /api/v1/profile`.
- `updateUserProfile()` local-first rồi remote `PUT /api/v1/profile`.
- `mintEmergencyLink()` trong luồng mới gọi `POST /api/v1/emergency-links/mint`.
- `public_profile_key` có thể tiếp tục cache trong secure prefs, nhưng backend phải verify ownership khi tái dùng.

## Web public profile gọi API như thế nào

Web public profile hiện gọi:

```text
GET /api/profile?key=<publicKey>&t=<publicProfileJwt>
```

Contract chuyển đổi an toàn:

- Ưu tiên giữ URL browser hiện tại để ít sửa web: `helper-id/api/profile.js` trở thành serverless proxy gọi backend mới `GET /api/v1/public/profile?key=...&t=...`.
- Vite bundle không chứa backend secret.
- Proxy phải giữ `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.
- `vercel.json` tiếp tục set `X-Robots-Tag: noindex, nofollow, noarchive` cho `/e/*` và `/api/*`.
- Response shape giữ tương thích với `EmergencyProfilePage`: `{ "key": "...", "profile": { ... } }`.
- Nếu gọi backend trực tiếp từ browser, backend phải cấu hình CORS allowlist chỉ cho origin public web chính thức và vẫn không cache.

Web error mapping:

- `400`: link không hợp lệ.
- `401`: link hết hạn hoặc token sai, hiển thị thông báo cần tạo lại QR/link.
- `404`/`410`: link không còn tồn tại hoặc đã bị thu hồi.
- `500`: lỗi tạm thời, không lộ chi tiết backend.

## Cách chạy backend local

Từ root repo, đặt secret dev không commit vào file:

```bash
export HELPID_AUTH_JWT_SIGNING_KEY="dev-auth-signing-key-32-byte-minimum-change-me"
export HELPID_PROFILE_JWT_SIGNING_KEY="dev-profile-signing-key-32-byte-minimum-change-me"
export ConnectionStrings__HelpIdDb="Data Source=backend/HelpId.Api/App_Data/helpid-dev.db"
export PublicWeb__BaseUrl="http://localhost:5173"
dotnet run --project backend/HelpId.Api/HelpId.Api.csproj
```

Health check:

```bash
curl http://127.0.0.1:5080/health
```

Không đưa secret mẫu thật, token hoặc database có dữ liệu user vào commit/log/testcase.

## Quy trình migration backend

1. Sửa entity/configuration trong `backend/HelpId.Api/Data`.
2. Tạo migration:

```bash
dotnet ef migrations add TenMigration --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

3. Kiểm tra migration diff: table, index, foreign key, default value, cascade delete.
4. Cập nhật database dev/test:

```bash
dotnet ef database update --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

5. Chạy `dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj`.
6. Không dùng destructive migration cho dữ liệu thật. Nếu cần migrate Firebase sang SQLite, lập kế hoạch riêng gồm mapping user/profile/public key, rollback và kiểm thử dữ liệu.

## Trạng thái Firebase legacy

Backend mới đã thay thế auth user-facing và public profile backend cho luồng mới, nhưng Firebase Auth/Firestore chưa được gỡ hoàn toàn:

- Android vẫn còn code `FirebaseRepository` cho legacy profile sync/cache/pending flow.
- Vercel `/api/mint` vẫn dùng Firebase Admin và Firestore mapping legacy.
- Dữ liệu user cũ trong Firestore chưa có migration sang SQLite backend.

Vì vậy không xóa Firebase dependency, service account env, Firestore collection hoặc legacy API nếu chưa có plan riêng về gỡ/migration dữ liệu.

## Use-case mới

Android app:

- Đăng ký tài khoản bằng email/password.
- Đăng nhập bằng email/password.
- Khôi phục session khi mở app.
- Refresh access token khi hết hạn.
- Logout và revoke refresh token.
- Xem trạng thái cần đăng nhập cho API remote.
- Vẫn xem hồ sơ khẩn cấp local cache khi offline/token hết hạn nếu đã có cache.
- Đồng bộ hồ sơ với backend mới bằng access token.
- Mint QR/NFC/SOS fallback public link bằng backend mới.
- Hiển thị lỗi validation/auth thân thiện, không lộ raw backend error.

Backend HelpID API:

- Tạo account và profile mặc định.
- Xác thực login và lockout khi brute force.
- Cấp access token và refresh token.
- Rotate refresh token.
- Revoke token khi logout.
- Trả user hiện tại qua `/auth/me`.
- Enforce role/permission và ownership.
- Lưu/cập nhật profile y tế của chính user.
- Mint/tái dùng public key `HID-*`.
- Ký public profile JWT 3 giờ.
- Trả public emergency profile whitelist.
- Ghi audit event bảo mật không chứa dữ liệu nhạy cảm.
- Từ chối input validation sai và input SQL injection.

Website Helper ID:

- Mở public emergency profile từ QR/NFC link.
- Gọi proxy/backend public profile bằng `publicKey + t`.
- Hiển thị lỗi link thiếu token, hết hạn, bị revoke hoặc invalid.
- Mở deep link về Android app.

Vercel Serverless API:

- Giữ Gemini proxy server-side.
- Có thể làm proxy `/api/profile` sang backend mới để giữ contract web public profile.
- Có thể tạm giữ `/api/mint` cũ cho Firebase trong giai đoạn chuyển đổi, nhưng Android auth mới phải dùng `/api/v1/emergency-links/mint`.

## Kiểm chứng khi triển khai các prompt sau

Backend:

- Build/test backend.
- Migration tạo đủ table/index/foreign key.
- Test register/login/refresh/logout/me.
- Test duplicate email, invalid password, lockout.
- Test cross-user profile/link access.
- Test public profile whitelist.
- Test SQL injection không bypass auth và không phá schema.

Android:

- Build app.
- Test register/login/logout/refresh.
- Test app restart.
- Test token hết hạn.
- Test offline vẫn xem cache local.
- Test QR/NFC/SOS fallback khi mint fail không render URL hỏng.

Web/API:

- Build web nếu sửa `helper-id`.
- Test `/e/:publicKey` với token hợp lệ/hết hạn/thiếu.
- Test header no-store/noindex còn nguyên.
- Test không có secret trong Vite bundle.
