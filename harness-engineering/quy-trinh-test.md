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
- `helper-id` cần `npm install` trước khi build nếu chưa có `node_modules`.
- API Vercel cần env thật để chạy request end-to-end.

## Khi nào phải thêm test

- Thêm test khi sửa validation form, số điện thoại, sanitize dữ liệu, mapping profile, Room converters, public profile sanitize, JWT/public key validation.
- Thêm test hoặc ít nhất test thủ công có ghi lại khi sửa SOS, WorkManager follow-up, permission, location, QR/NFC.
- Với bug fix, test nên chứng minh bug cũ fail hoặc kiểm tra trực tiếp hành vi mới.

## Gợi ý test Android

Unit test phù hợp:

- `EmergencyNumberResolver`: country ISO sang số khẩn cấp.
- Hàm sanitize/validation trong `EditProfileScreen` nếu tách ra khỏi composable.
- `Converters`: list string và list contact round-trip.
- Mapping local/domain trong repository nếu tách helper public/internal testable.

Instrumentation/manual test phù hợp:

- Mở app lần đầu với mạng có/không có.
- Lưu profile, kill app, mở lại để kiểm tra Room cache.
- Tắt mạng, sửa profile, bật mạng lại để kiểm tra pending sync.
- SOS thiếu quyền: phải không gửi SMS và không crash.
- SOS đủ quyền test mode hoặc device test: message có name, blood, address, location nếu có.
- WorkManager follow-up có thể start/stop và không gửi nếu thiếu quyền SMS.
- QR mint fail offline: hiển thị lỗi và retry.
- Thiết bị không NFC: UI hiển thị unavailable.
- Đổi ngôn ngữ: app recreate và string thay đổi.

## Gợi ý test web/API

Build/type:

- `npm run build` bắt lỗi TSX, route và bundling.
- `npx tsc --noEmit` bắt type nếu build không đủ nghiêm.

API manual:

- `/api/mint` chỉ nhận POST.
- `/api/mint` trả 401 nếu thiếu bearer token Firebase.
- `/api/mint` trả 400 nếu public key format sai.
- `/api/profile` chỉ nhận GET.
- `/api/profile` trả 400 nếu thiếu key/token.
- `/api/profile` trả 401 nếu JWT sai/hết hạn hoặc không khớp key.
- `/api/profile` không trả field ngoài whitelist.
- `/api/gemini` trả 413 nếu prompt quá dài và 401 nếu `GEMINI_PROXY_TOKEN` bật nhưng bearer sai.

## Checklist release thủ công

- Android debug build thành công.
- Web build thành công nếu đụng `helper-id`.
- Không có secret trong diff.
- Không có dữ liệu y tế thật trong log/test fixture.
- Public emergency page vẫn noindex.
- Strings mới có đủ locale hoặc có quyết định rõ ràng về fallback.
