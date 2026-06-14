# Quy trình kiểm thử

## Ma trận command

Android build cơ bản:

```bash
./gradlew :app:assembleDebug
```

Android unit test:

```bash
./gradlew :app:testDebugUnitTest
```

Android instrumentation test, cần emulator hoặc device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Android lint:

```bash
./gradlew :app:lintDebug
```

Backend build:

```bash
dotnet build backend/HelpId.Api/HelpId.Api.csproj
```

Backend test:

```bash
dotnet test backend/HelpId.Api.Tests/HelpId.Api.Tests.csproj
```

Backend migration list:

```bash
dotnet ef migrations list --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Backend database update trên database dev/test:

```bash
dotnet ef database update --project backend/HelpId.Api/HelpId.Api.csproj --startup-project backend/HelpId.Api/HelpId.Api.csproj
```

Web build:

```bash
cd helper-id
npm run build
```

Web type check:

```bash
cd helper-id
npx tsc --noEmit
```

Kiểm tra whitespace:

```bash
git diff --check
```

## Lưu ý môi trường

- Không commit `org.gradle.java.home` với đường dẫn JDK tuyệt đối theo máy cá nhân. Nếu Gradle lỗi vì JDK, cấu hình Gradle JVM trong Android Studio hoặc set `JAVA_HOME` cục bộ về JDK 17/21.
- Android Firebase build thường cần `app/google-services.json`. File này bị ignore và không nên commit.
- Backend local cần `HELPID_AUTH_JWT_SIGNING_KEY` và `HELPID_PROFILE_JWT_SIGNING_KEY`, mỗi secret tối thiểu 32 byte.
- Backend dev dùng SQLite `App_Data/helpid-dev.db` mặc định; override bằng `ConnectionStrings__HelpIdDb` nếu cần database test riêng.
- `helper-id` cần `npm install` trước khi build nếu chưa có `node_modules`.
- Vercel `/api/profile` cần `HELPID_BACKEND_URL`; `/api/mint` legacy cần Firebase env; `/api/gemini` cần Gemini env thật để chạy end-to-end.

## Khi nào phải thêm test

- Thêm test khi sửa validation form, số điện thoại, sanitize dữ liệu, mapping profile, Room converters, public profile sanitize, JWT/public key validation.
- Thêm test khi sửa auth backend: password policy, duplicate email, lockout, refresh rotation/reuse revoke, logout revoke, role/permission/ownership, SQL injection payload.
- Thêm test hoặc ít nhất test thủ công có ghi lại khi sửa SOS, WorkManager follow-up, permission, location, QR/NFC.
- Với bug fix, test nên chứng minh bug cũ fail hoặc kiểm tra trực tiếp hành vi mới.

## Gợi ý test Android

Unit test phù hợp:

- `EmergencyNumberResolver`: country ISO sang số khẩn cấp.
- Hàm sanitize/validation trong `EditProfileScreen` nếu tách ra khỏi composable.
- `Converters`: list string và list contact round-trip.
- Mapping local/domain trong repository nếu tách helper public/internal testable.
- Auth token store và API repository nếu có mock HTTP/local fake.

Instrumentation/manual test phù hợp:

- Register/login/logout bằng backend local/staging.
- Mở app lần đầu với mạng có/không có.
- Token hết hạn: refresh một lần rồi retry request private.
- Lưu profile, kill app, mở lại để kiểm tra Room cache.
- Tắt mạng, sửa profile, bật mạng lại để kiểm tra pending sync hoặc lỗi remote được xử lý an toàn.
- SOS thiếu quyền: phải không gửi SMS và không crash.
- SOS đủ quyền test mode hoặc device test: message có name, blood, address, location nếu có.
- WorkManager follow-up có thể start/stop và không gửi nếu thiếu quyền SMS.
- QR mint fail offline: hiển thị lỗi và retry.
- Thiết bị không NFC: UI hiển thị unavailable.
- Đổi ngôn ngữ: app recreate và string thay đổi.

## Gợi ý test backend

- `dotnet test` phải bao phủ register/login/refresh/logout/me.
- Password dưới min policy trả `422`.
- Duplicate email trả `409`, login sai không tiết lộ email tồn tại.
- Refresh token rotate; token cũ reuse phải revoke token family và trả `401`.
- Logout revoke refresh token và không cần log token plaintext.
- Profile private chỉ dùng user từ JWT `sub`, không tin `userId` body/query.
- Cross-user profile/link access trả `403` hoặc `404` theo policy.
- Public profile chỉ trả whitelist field, không trả `userId`, email, role, token, language.
- SQL injection payload không bypass auth, không phá schema, không tạo raw SQL unsafe.
- Response nhạy cảm có `Cache-Control: no-store` và không lộ raw exception SQL.

## Gợi ý test web/API

Build/type:

- `npm run build` bắt lỗi TSX, route và bundling.
- `npx tsc --noEmit` bắt type nếu build không đủ nghiêm.

API manual:

- `/api/profile` chỉ nhận GET.
- `/api/profile` trả 400 nếu thiếu key/token hoặc public key sai format.
- `/api/profile` proxy sang `HELPID_BACKEND_URL` và trả 503 nếu backend unavailable.
- `/api/profile` không trả field ngoài whitelist.
- `/api/mint` legacy chỉ nhận POST và trả 401 nếu thiếu bearer token Firebase.
- `/api/gemini` trả 413 nếu prompt quá dài và 401 nếu `GEMINI_PROXY_TOKEN` bật nhưng bearer sai.

## Checklist release thủ công

- Android debug build thành công nếu đụng Android.
- Backend build/test/migration check thành công nếu đụng backend.
- Web build/type check thành công nếu đụng `helper-id`.
- Không có secret trong diff.
- Không có dữ liệu y tế thật trong log/test fixture.
- Public emergency page vẫn noindex/no-store.
- Strings mới có đủ locale hoặc có quyết định rõ ràng về fallback.
- Testcase đã chạy được ghi vào `passed-testcases.md` hoặc `failed-testcases.md`.
