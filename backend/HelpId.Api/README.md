# Backend HelpId.Api

Skeleton backend cho tính năng đăng ký/đăng nhập HelpID.

## Stack

- ASP.NET Core Web API, target `net8.0`.
- Entity Framework Core.
- SQLite qua `Microsoft.EntityFrameworkCore.Sqlite`.
- Cấu hình mẫu trong `appsettings.json`, không chứa secret.

## Cấu trúc

- `Program.cs`: cấu hình DI, `HelpIdDbContext`, endpoint `/health` và route auth `/api/v1/auth/*`.
- `Data/HelpIdDbContext.cs`: DbContext code-first, cấu hình entity/index/foreign key cho auth, profile, public link và audit.
- `Data/Entities/`: entity code-first cho `Users`, `RefreshTokens`, `UserProfiles`, `ProfileAllergies`, `MedicalNotes`, `EmergencyContacts`, `PublicProfileLinks`, `AuditEvents`, `Roles`, `Permissions`, `UserRoles`, `RolePermissions`.
- `Auth/`: DTO, validation, PBKDF2 password hashing, JWT access token HS256, refresh token hashing và endpoint auth.
- `App_Data/`: vị trí mặc định cho SQLite dev database. Không commit file `.db`, `.db-wal`, `.db-shm`.
- `Properties/launchSettings.json`: profile chạy local tại `http://127.0.0.1:5080`.

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

## Cấu hình auth

Auth API cần secret ký JWT qua biến môi trường, không lưu trong `appsettings.json`:

```bash
export HELPID_AUTH_JWT_SIGNING_KEY="thay-bang-chuoi-random-it-nhat-32-byte"
```

Các endpoint hiện có:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

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
ConnectionStrings__HelpIdDb="Data Source=/var/lib/helpid/helpid.db"
```

## Ghi chú bảo mật

- Không commit secret JWT, token, service account hoặc database thật có dữ liệu người dùng.
- Không log password, access token, refresh token, public profile JWT, số điện thoại, vị trí hoặc dữ liệu y tế.
- Auth API không trả raw exception/SQL error; lỗi validation/auth dùng status code và problem response generic.
