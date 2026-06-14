# Hướng dẫn agent cho HelpID Android

Tài liệu này là chỉ dẫn vận hành cho agent coding/testing trong repo này. Mọi tài liệu markdown trong repo nên viết bằng tiếng Việt, trừ khi người dùng yêu cầu khác hoặc nội dung bắt buộc phải giữ nguyên văn tiếng Anh.

## Bối cảnh dự án

Repo gồm hai phần:

- `app/`: ứng dụng Android native bằng Kotlin, Jetpack Compose, Room, Firebase Auth/Firestore, WorkManager, ZXing, Google Play Services Location, AndroidX Security Crypto, iTextG.
- `helper-id/`: website React/Vite và serverless API cho Vercel. Website có marketing routes và route khẩn cấp `/e/:publicKey`; API có `/api/mint`, `/api/profile`, `/api/gemini`.

Dự án xử lý dữ liệu nhạy cảm: tên, nhóm máu, địa chỉ, dị ứng, thông tin y tế, số điện thoại liên hệ khẩn cấp, vị trí trong luồng SOS. Luôn ưu tiên bảo mật, quyền riêng tư, khả năng offline và hành vi an toàn trong tình huống khẩn cấp.

## Quy tắc làm việc

- Đọc tài liệu trong `harness-engineering/` trước khi sửa code lớn.
- Khi người dùng yêu cầu lên kế hoạch, phải viết mới toàn bộ kế hoạch vào `harness-engineering/ke-hoach.md`; không viết thêm nối đuôi kế hoạch cũ. Kế hoạch nên nêu bối cảnh, mục tiêu, phạm vi sửa, bước thực hiện và cách kiểm chứng.
- Nếu sau khi đã có kế hoạch người dùng yêu cầu sửa code/tài liệu/cấu hình, phải tự động đọc lại `harness-engineering/ke-hoach.md` trước khi sửa, rồi thực hiện theo kế hoạch hoặc nói rõ điểm cần điều chỉnh nếu bối cảnh đã thay đổi.
- Nếu người dùng chỉ yêu cầu lập kế hoạch, không tự ý sửa code/cấu hình ngoài việc ghi kế hoạch và cập nhật tài liệu vận hành nếu được yêu cầu.
- Sau mỗi prompt yêu cầu sửa đổi code/tài liệu/cấu hình, khi hoàn thành phải cập nhật `CHANGELOG.md` ở cùng cấp với `AGENTS.md`. Entry mới luôn chèn lên đầu file, không append xuống cuối file, theo format ngày giờ bình thường `dd/mm/yyyy hh:mm:ss` trên dòng đầu và dòng tiếp theo là `- nội_dung_sửa_đổi`.
- Không sửa file nhị phân hoặc artifact sinh ra nếu không được yêu cầu: `*.png`, `gradle-wrapper.jar`, `helper-id/package-lock.json` chỉ sửa khi dependency thật sự thay đổi.
- Không commit secret. Các file/env nhạy cảm gồm `app/google-services.json`, `helper-id/.env*`, Firebase service account, JWT secret, Gemini key.
- Không ghi log dữ liệu y tế, số điện thoại, vị trí, Firebase ID token, JWT token hoặc public profile token.
- Khi sửa UI Android, ưu tiên `stringResource` và cập nhật toàn bộ `app/src/main/res/values*/strings.xml` nếu thêm key mới.
- Khi sửa schema Room, tăng version trong `AppDatabase`, thêm migration, không dùng destructive migration cho dữ liệu người dùng.
- Khi sửa SOS, SMS, location, NFC, QR hoặc public profile, phải nghĩ theo failure mode: thiếu quyền, offline, Firebase lỗi, token hết hạn, thiết bị không có NFC, không có SMS, không có location.
- Khi sửa web/API, giữ secret ở serverless API. Không đưa secret vào Vite bundle hoặc biến `VITE_*`.
- Giữ thay đổi hẹp theo yêu cầu. Không refactor rộng, không đổi branding/copy hàng loạt nếu task không yêu cầu.

## Lệnh thường dùng

Android:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Ghi chú: không commit `org.gradle.java.home` với đường dẫn JDK tuyệt đối theo máy cá nhân. Nếu Gradle không tìm được Java hoặc lỗi với Java quá mới, cấu hình Gradle JVM trong Android Studio hoặc set `JAVA_HOME` cục bộ về JDK 17/21.

Web:

```bash
cd helper-id
npm install
npm run build
npm run dev
npx tsc --noEmit
```

Vercel/API cần env:

- `FIREBASE_SERVICE_ACCOUNT_KEY`: JSON service account đã stringify.
- `PROFILE_JWT_SECRET`: secret ký JWT cho profile link.
- `GEMINI_API_KEY`: chỉ dùng server-side cho `/api/gemini`.
- `GEMINI_PROXY_TOKEN`: tùy chọn, bật bảo vệ bearer token cho `/api/gemini`.

## Bản đồ code Android

- `MainActivity.kt`: entry Compose, navigation thủ công bằng state, init Firebase trong `AppNavigation`, bottom bar.
- `ui/EmergencyScreen.kt`: màn hình ID khẩn cấp, online/offline sync, SMS SOS, location, WorkManager follow-up, auto emergency dial, NFC beam cũ, profile rendering.
- `ui/EditProfileScreen.kt`: form chỉnh profile, validation tên/nhóm máu/số điện thoại, chọn contact, lưu Firebase/local.
- `ui/QRScreen.kt`: mint link bảo mật, tạo QR bằng ZXing, NFC beam.
- `data/FirebaseRepository.kt`: Auth ẩn danh, Firestore, cache Room, pending sync, mint emergency link, secure prefs migration.
- `data/local/*`: Room entity, DAO, converters, migration.
- `utils/*`: mã hóa AndroidKeyStore, EncryptedSharedPreferences fallback, PDF, share, notification, language, location, số khẩn cấp.
- `work/SosFollowUpWorker.kt`: SMS cập nhật vị trí định kỳ sau SOS.

## Bản đồ code web/API

- `helper-id/App.tsx`: routing. `/e/:publicKey` tách khỏi marketing site.
- `components/EmergencyProfilePage.tsx`: fetch `/api/profile`, noindex meta, render profile khẩn cấp, deep link Android intent.
- `api/mint.js`: xác thực Firebase ID token, cấp public key dạng `HID-*`, map `publicKeys/{key}` sang uid, ký JWT 3 giờ.
- `api/profile.js`: xác thực JWT, lấy mapping, sanitize whitelist fields trước khi trả profile.
- `api/gemini.js`: proxy Gemini server-side, có giới hạn prompt và timeout.
- `index.html`: Tailwind CDN config, import map, font, favicon.

## Kiểm thử bắt buộc theo loại thay đổi

- Thay đổi Kotlin build/dependency: chạy `./gradlew :app:assembleDebug`.
- Thay đổi logic Android thuần: thêm/chạy unit test ở `app/src/test/...` nếu có thể.
- Thay đổi permission/SOS/location/NFC/QR: chạy build, kiểm tra permission flow trên emulator/device, test offline và thiếu quyền.
- Thay đổi Room: thêm migration test hoặc ít nhất build và kiểm tra logic migration bằng code review kỹ.
- Thay đổi string/resource: chạy build để phát hiện thiếu string ở locale.
- Thay đổi web React/API: chạy `cd helper-id && npm run build`; nếu đổi type phức tạp, chạy thêm `npx tsc --noEmit`.
- Thay đổi Vercel API: kiểm tra method, status code, header `no-store`, input validation, secret handling.

## Rủi ro đã thấy khi đọc repo

- `helper-id/api/gemini.js` import `node-fetch`; lockfile có `node-fetch` do dependency gián tiếp, nhưng `package.json` chưa khai báo trực tiếp. Nếu sửa hoặc dựa vào endpoint này, cân nhắc thêm dependency trực tiếp.
- Nhiều text Android trong `EmergencyScreen.kt` và `EditProfileScreen.kt` còn hard-code tiếng Anh. Khi chạm vào màn hình đó, ưu tiên chuyển sang resource string.
- Một số bản dịch locale không hoàn toàn đồng nhất về dấu/chuỗi so với `values/strings.xml`. Khi thêm key, cập nhật toàn bộ locale.
- App launcher PNG đang rất lớn và trùng kích thước giữa density folders. Không tự tối ưu nếu không được giao, nhưng tránh làm phình thêm asset.
- `gradle.properties` có đường dẫn JDK Windows, có thể làm build Linux/macOS lỗi nếu không override local.

## Tài liệu harness

Các hướng dẫn chi tiết nằm trong `harness-engineering/`:

- `README.md`: mục lục và cách dùng.
- `tong-quan-kien-truc.md`: kiến trúc repo và luồng chính.
- `quy-trinh-code.md`: quy trình sửa code.
- `quy-trinh-test.md`: chiến lược kiểm thử.
- `android.md`: ghi chú Android/Compose/Room/Firebase/SOS.
- `web-api.md`: ghi chú React/Vite/Vercel API.
- `bao-mat-du-lieu.md`: ràng buộc bảo mật và quyền riêng tư.
- `checklist-truoc-khi-tra-loi.md`: checklist trước khi kết thúc task.
