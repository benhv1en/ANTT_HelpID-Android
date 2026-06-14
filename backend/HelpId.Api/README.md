# Backend HelpId.Api

Backend ASP.NET Core cho đăng ký/đăng nhập, profile private và public emergency link của HelpID.

## Stack

- ASP.NET Core Web API, target `net8.0`.
- Entity Framework Core code-first.
- SQLite qua `Microsoft.EntityFrameworkCore.Sqlite`.
- JWT access token HS256.
- Refresh token opaque, lưu hash và rotate/revoke được.
- Public profile JWT riêng cho link khẩn cấp.
- Cấu hình mẫu trong `appsettings.json`, không chứa secret.

## Cấu trúc

- `Program.cs`: cấu hình DI, `HelpIdDbContext`, security headers, exception handler, `/health` và route groups.
- `Data/HelpIdDbContext.cs`: DbContext code-first, cấu hình entity/index/foreign key cho auth, profile, public link, audit và RBAC.
- `Data/Entities/`: entity code-first cho `Users`, `RefreshTokens`, `UserProfiles`, `ProfileAllergies`, `MedicalNotes`, `EmergencyContacts`, `PublicProfileLinks`, `AuditEvents`, `Roles`, `Permissions`, `UserRoles`, `RolePermissions`.
- `Auth/`: DTO, validation, PBKDF2 password hashing, JWT access token HS256, refresh token hashing/rotation/revoke và endpoint auth.
- `Profiles/`: private profile API, profile validation, public profile JWT và public profile whitelist.
- `EmergencyLinks/`: mint/tái dùng public key, verify ownership và tạo URL `/e/:publicKey?t=...`.
- `Security/`: current user context, role/permission policies.
- `App_Data/`: vị trí mặc định cho SQLite dev database. Không commit file `.db`, `.db-wal`, `.db-shm`.
- `Properties/launchSettings.json`: profile chạy local tại `http://127.0.0.1:5080`.

## Biến môi trường

Bắt buộc khi chạy auth/public profile thực tế:

```bash
export HELPID_AUTH_JWT_SIGNING_KEY="thay-bang-chuoi-random-toi-thieu-32-byte"
export HELPID_PROFILE_JWT_SIGNING_KEY="thay-bang-chuoi-random-khac-toi-thieu-32-byte"
```

Tùy chọn override:

```bash
export ConnectionStrings__HelpIdDb="Data Source=backend/HelpId.Api/App_Data/helpid-dev.db"
export AuthJwt__Issuer="HelpId.Api"
export AuthJwt__Audience="HelpId.Android"
export PublicWeb__BaseUrl="https://helper-id.vercel.app"
```

Không commit secret vào `appsettings*.json`, shell history dùng chung, log hoặc testcase.

## Lệnh chạy

Từ root repo:

```bash
dotnet build backend/HelpId.Api/HelpId.Api.csproj
dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj
dotnet run --project backend/HelpId.Api/HelpId.Api.csproj
```

Kiểm tra health:

```bash
curl http://127.0.0.1:5080/health
```

## Cấu hình database

Mặc định dev:

```json
{
  "ConnectionStrings": {
    "HelpIdDb": "Data Source=App_Data/helpid-dev.db"
  }
}
```

Production/dev local có thể override bằng biến môi trường:

```bash
export ConnectionStrings__HelpIdDb="Data Source=/var/lib/helpid/helpid.db"
```

## Migration

Liệt kê migration:

```bash
dotnet ef migrations list --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Tạo migration sau khi đổi entity/schema:

```bash
dotnet ef migrations add TenMigration --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Apply vào database dev/test:

```bash
dotnet ef database update --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Không dùng destructive migration cho dữ liệu thật. Nếu cần migrate Firestore sang SQLite backend, lập kế hoạch riêng trước.

## Endpoint chính

Auth:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Profile private:

- `GET /api/v1/profile`
- `PUT /api/v1/profile`

Emergency link/public profile:

- `POST /api/v1/emergency-links/mint`
- `GET /api/v1/public/profile?key=...&t=...`

Health:

- `GET /health`

## Ghi chú bảo mật

- Không commit secret JWT, token, service account hoặc database thật có dữ liệu người dùng.
- Không log password, access token, refresh token, public profile JWT, số điện thoại, vị trí hoặc dữ liệu y tế.
- Auth API không trả raw exception/SQL error; lỗi validation/auth dùng status code và problem response generic.
- Dùng EF Core LINQ/parameterized query, không ghép chuỗi SQL từ input người dùng.
- Refresh token plaintext chỉ trả về client một lần; database chỉ lưu hash.
- Access token không chứa dữ liệu y tế, số điện thoại, địa chỉ hoặc public key.
- Public profile chỉ trả whitelist field và luôn dùng `Cache-Control: no-store`.
