# Tổng quan kiến trúc

## Thành phần chính

Repo có hai ứng dụng liên quan với nhau:

- Ứng dụng Android native trong `app/`.
- Website và API Vercel trong `helper-id/`.

Android là bề mặt chính của sản phẩm. Website hỗ trợ marketing và public emergency profile link được tạo từ app.

## Android app

Stack chính:

- Kotlin.
- Jetpack Compose Material3.
- Firebase Auth ẩn danh và Firestore.
- Room cho cache local.
- AndroidX Security Crypto và AndroidKeyStore cho dữ liệu nhạy cảm.
- WorkManager cho SMS follow-up sau SOS.
- Google Play Services Location.
- ZXing cho QR code.
- iTextG cho PDF export.

Luồng khởi động:

1. `MainActivity.attachBaseContext` áp dụng ngôn ngữ đã lưu.
2. `MainActivity.onCreate` gọi `AppNavigation`.
3. `AppNavigation` tạo `FirebaseRepository(context)` và gọi `initializeUser()` trong timeout 10 giây.
4. Nếu init thành công, điều hướng thủ công bằng state qua các route: `emergency`, `qr`, `edit`, `language`.

Luồng dữ liệu profile:

1. `FirebaseRepository.initializeUser()` đăng nhập Firebase anonymous nếu cần.
2. Nếu Firestore chưa có profile, tạo `UserProfile.default(userId)`.
3. `EmergencyScreen` đọc cache Room trước, sau đó fetch remote.
4. `EditProfileScreen` lưu vào Room trước, rồi sync Firestore; nếu remote lỗi thì cache pending profile trong secure prefs.
5. Khi online trở lại, `syncPendingProfile()` đẩy pending profile.

Luồng public emergency link:

1. Android gọi `FirebaseRepository.mintEmergencyLink()`.
2. Repository lấy Firebase ID token rồi POST tới `https://helper-id.vercel.app/api/mint`.
3. `/api/mint` xác thực ID token, tạo hoặc dùng lại public key `HID-*`, map public key sang uid, ký JWT 3 giờ.
4. App dùng URL trả về để tạo QR hoặc NFC beam.
5. Web route `/e/:publicKey?t=...` gọi `/api/profile`.
6. `/api/profile` xác thực JWT, lấy profile từ Firestore và chỉ trả các field whitelist.

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

API:

- `POST /api/mint`: cần Firebase ID token.
- `GET /api/profile?key=...&t=...`: cần public key và JWT.
- `POST /api/gemini`: server-only Gemini proxy, tùy chọn bearer token riêng.

## File nhị phân và artifact

- `gradle/wrapper/gradle-wrapper.jar`: wrapper jar.
- `Gemini_Generated_Image_h8qgpch8qgpch8qg.png`: ảnh lớn.
- `app/src/main/res/mipmap-*/ic_launcher*.png`: launcher icons, hiện rất lớn.
- `helper-id/package-lock.json`: lockfile npm, chỉ sửa khi dependency thay đổi.
- `.git/`: không đọc/sửa như source trừ khi cần thao tác git.
