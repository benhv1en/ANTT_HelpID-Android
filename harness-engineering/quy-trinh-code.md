# Quy trình coding

## Trước khi sửa

- Chạy `git status --short` để biết worktree có thay đổi sẵn không.
- Xác định phần tác động: Android, web/API, tài liệu, build config hoặc asset.
- Đọc file gần nhất với thay đổi, không dựa vào đoán.
- Nếu thay đổi đụng dữ liệu y tế, SMS, location, auth, token, QR, NFC hoặc Room, đọc thêm `bao-mat-du-lieu.md`.

## Khi sửa Android

- Giữ style Kotlin hiện có: 4 spaces, Compose function rõ ràng, ít abstraction mới.
- Không đưa business logic phức tạp vào composable nếu có thể tách thành hàm thuần để test.
- Với text UI mới, thêm vào `app/src/main/res/values/strings.xml` và các locale `values-es`, `values-fr`, `values-de`, `values-hi`.
- Với permission mới, cập nhật `AndroidManifest.xml` và kiểm tra runtime permission nếu Android yêu cầu.
- Với dependency mới, ưu tiên `gradle/libs.versions.toml` nếu dependency dùng chung hoặc có khả năng tái sử dụng.
- Với Room schema, tăng version và viết migration.
- Với dữ liệu nhạy cảm local, đi qua `FirebaseRepository` và các helper mã hóa hiện có thay vì tự lưu SharedPreferences thường.

## Khi sửa web/API

- Code React/TSX hiện dùng function component, Tailwind class và lucide icons.
- Route public emergency `/e/:publicKey` là luồng an toàn, không xử lý như trang marketing.
- Không expose secret trong client bundle. Secret chỉ ở `helper-id/api/*`.
- Với API, luôn kiểm tra method, input validation, status code, JSON content type, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.
- Nếu import package trực tiếp trong source, khai báo trong `helper-id/package.json` thay vì dựa vào dependency gián tiếp.
- Nếu thêm route marketing, bảo đảm Vercel rewrite vẫn phục vụ SPA.

## Khi sửa tài liệu

- Markdown viết tiếng Việt.
- Dùng câu ngắn, ưu tiên checklist và lệnh chạy thật.
- Không ghi secret, token mẫu thật hoặc dữ liệu y tế thật.
- Nếu tài liệu mô tả command đã chạy, ghi command chính xác.

## Khi sửa asset

- Không tự thay PNG/JAR nếu task không yêu cầu.
- Launcher icon hiện có kích thước bất thường lớn. Nếu tối ưu asset, phải kiểm tra density, preview app icon và build Android.
- Với web image, tránh phụ thuộc placeholder nếu mục tiêu là trang sản phẩm thật.

## Sau khi sửa

- Chạy command test/build phù hợp trong `quy-trinh-test.md`.
- Chạy `git diff --check` để bắt whitespace lỗi.
- Đọc diff của chính mình trước khi trả lời.
- Nếu không chạy được test/build, nói rõ lý do và command đã thử.
