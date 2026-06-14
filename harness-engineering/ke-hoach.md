# Kế hoạch thêm Tiếng Việt cho màn hình chọn ngôn ngữ và toàn bộ app Android

Thời điểm lập kế hoạch: 14/06/2026 13:52:15

## Bối cảnh

Người dùng thấy màn hình `Select Language` chưa có Tiếng Việt. Yêu cầu hiện tại chỉ là lập kế hoạch, chưa sửa code.

Hiện trạng đọc từ repo:

- `LanguageManager` chỉ có `en`, `es`, `hi`, `fr`, `de`; chưa có `vi`.
- App có resource locale `values`, `values-es`, `values-fr`, `values-de`, `values-hi`; chưa có `app/src/main/res/values-vi/strings.xml`.
- `LanguageSelectionScreen` lấy danh sách từ `LanguageManager.getAvailableLanguages()` và hiển thị `language.displayName` hard-code, chưa dùng các key `language_*` trong resource.
- `MainActivity.attachBaseContext()` đã gọi `LanguageManager.applySavedLanguage(newBase)`, nên cơ chế đổi locale hiện có thể tái sử dụng.
- `EmergencyNumberResolver` chưa có country code `VN`. Với `Locale("vi")` không có country, nếu không có SIM/network Việt Nam thì logic hiện tại vẫn có thể fallback sai sang `112` hoặc `911`.
- Một số màn hình/helper còn text tiếng Anh hard-code: `EmergencyScreen.kt`, `EditProfileScreen.kt`, `MainActivity.kt`, `ShareUtils.kt`, `PDFExporter.kt`, `NotificationHelper.kt`, `BiometricManager.kt`, `SosFollowUpWorker.kt`, `UserProfile.default()`.

## Mục tiêu

- Thêm lựa chọn `Tiếng Việt` vào màn hình chọn ngôn ngữ.
- Khi chọn `Tiếng Việt` và bấm `APPLY`, toàn bộ UI Android do app sinh ra hiển thị tiếng Việt.
- Nút gọi cấp cứu chính, auto-call sau SOS và mọi text hiển thị số khẩn cấp dùng số cấp cứu y tế Việt Nam `115`.
- Các template do app sinh ra như SOS SMS, fallback share, share text, PDF, notification, biometric prompt và lỗi validation dùng tiếng Việt khi locale là `vi-VN`.
- Không tự dịch dữ liệu người dùng nhập hoặc dữ liệu đã lưu trên Firebase/Room như tên, bệnh nền, dị ứng, địa chỉ, tên liên hệ. Đây là dữ liệu y tế/PII và phải giữ nguyên để tránh làm sai nội dung.
- Không sửa web/API `helper-id` trong phạm vi này, trừ khi sau đó người dùng yêu cầu cả public emergency page `/e/:publicKey` cũng phải có tiếng Việt.

## Quyết định hành vi

- Thêm ngôn ngữ `VIETNAMESE` với code `vi` và locale runtime là `vi-VN`.
- Khi app language là `vi`, số cấp cứu ưu tiên là `115` theo yêu cầu người dùng, kể cả khi máy không detect được country `VN`.
- Với các ngôn ngữ khác, giữ behavior hiện tại: ưu tiên SIM country, network country, locale country, rồi fallback.
- Nếu sau này cần an toàn cho người Việt đi nước ngoài, nên tách riêng "ngôn ngữ app" và "quốc gia/số khẩn cấp"; task hiện tại chưa làm phần đó vì người dùng muốn chọn Tiếng Việt thì dùng số Việt Nam.

## Phạm vi file dự kiến

- `app/src/main/java/com/helpid/app/utils/LanguageManager.kt`
  - Thêm `VIETNAMESE`.
  - Lưu code `vi`.
  - Tạo locale `vi-VN` thay vì chỉ `Locale("vi")`.
  - Cân nhắc đổi enum từ `displayName: String` sang `labelRes: Int` hoặc hàm lấy tên qua resource để màn hình chọn ngôn ngữ không còn hard-code thiếu dấu.
- `app/src/main/java/com/helpid/app/ui/LanguageSelectionScreen.kt`
  - Hiển thị tên ngôn ngữ qua `stringResource`, thêm `Tiếng Việt`.
  - Kiểm tra recreate/apply locale sau khi chọn `Tiếng Việt`.
- `app/src/main/res/values/strings.xml`
  - Thêm key `language_vietnamese` và các key còn thiếu cho mọi text hard-code sẽ được đưa vào resource.
  - Đổi các string số khẩn cấp sang dạng có tham số, ví dụ nhận `%1$s` để render đúng `115`, `112`, `911`.
- `app/src/main/res/values-vi/strings.xml`
  - Tạo mới đầy đủ các key hiện có và các key mới bằng tiếng Việt.
  - Dịch các nhóm: Emergency, Edit Profile, QR/NFC, Share/Export, Language Selection, bottom nav, SOS countdown/auto-call/fallback, validation, notification, biometric, PDF/share template.
- `app/src/main/res/values-es/strings.xml`, `values-fr/strings.xml`, `values-de/strings.xml`, `values-hi/strings.xml`
  - Bổ sung các key mới để build không thiếu resource.
  - Không cần dịch lại toàn bộ nội dung cũ nếu task chỉ thêm Việt, nhưng key mới phải có fallback hợp lệ.
- `app/src/main/java/com/helpid/app/utils/EmergencyNumberResolver.kt`
  - Thêm mapping `VN -> 115`.
  - Thêm logic ưu tiên `LanguageManager.getSelectedLanguage(context) == VIETNAMESE` để trả `115` theo yêu cầu.
  - Nếu cần test dễ hơn, tách hàm resolve theo country/language thành hàm thuần.
- `app/src/main/java/com/helpid/app/ui/EmergencyScreen.kt`
  - Đưa các text hard-code vào resource: nút gọi cấp cứu, countdown gửi SOS, hủy SOS, auto-call, hủy auto-call, fallback share, stop follow-up, toast cảnh báo profile demo, toast một số contact fail, chooser title.
  - Đổi SMS template sang resource có tham số: tên, nhóm máu, địa chỉ, vị trí, profile link.
  - Đảm bảo `CALL EMERGENCY - $emergencyNumber` chuyển thành tiếng Việt và hiển thị `GỌI CẤP CỨU - 115` khi locale là `vi-VN`.
- `app/src/main/java/com/helpid/app/ui/EditProfileScreen.kt`
  - Đưa validation/supporting text còn hard-code vào resource.
  - Khi lưu profile, set `language` theo `LanguageManager.getSelectedLanguage(context).code` thay vì hard-code `"en"` nếu field này đang được dùng để phản ánh ngôn ngữ hồ sơ.
  - Không tự đổi nội dung y tế người dùng đã nhập.
- `app/src/main/java/com/helpid/app/MainActivity.kt`
  - Đưa init error UI text và thông báo khởi tạo nếu cần hiển thị cho người dùng vào resource.
  - Giữ route/state hiện tại, không đưa Navigation Compose vào task này.
- `app/src/main/java/com/helpid/app/utils/ShareUtils.kt`
  - Dùng resource string cho chooser title, subject email, tiêu đề và label trong share text.
  - Share text sinh ra bằng tiếng Việt khi app đang ở `vi-VN`.
- `app/src/main/java/com/helpid/app/utils/PDFExporter.kt`
  - Dùng `context.getString(...)` cho tiêu đề/section/label/footer.
  - Kiểm tra font iTextG hiện dùng Helvetica có hiển thị dấu tiếng Việt ổn không; nếu lỗi dấu, cần chọn font Unicode phù hợp trong phạm vi sửa PDF.
- `app/src/main/java/com/helpid/app/utils/NotificationHelper.kt`
  - Đưa channel name/description và full-screen test notification text vào resource.
  - Lưu ý Android notification channel name/description đã tạo trước đó có thể không đổi ngay trên thiết bị cũ nếu channel id giữ nguyên; cần test cài mới hoặc đổi channel id nếu thực sự cần cập nhật channel đã tồn tại.
- `app/src/main/java/com/helpid/app/utils/BiometricManager.kt`
  - Đưa title/subtitle/nút hủy/lỗi xác thực vào resource.
- `app/src/main/java/com/helpid/app/work/SosFollowUpWorker.kt`
  - Đưa message follow-up vị trí sang resource hoặc helper format theo locale.
- `app/src/main/java/com/helpid/app/data/UserProfile.kt`
  - Cân nhắc Việt hóa default/demo profile khi locale là `vi-VN`, nhưng không nên đổi dữ liệu profile thật đã lưu.
  - Nếu default profile được tạo trước khi chọn ngôn ngữ, có thể để dữ liệu demo tiếng Anh và chỉ đảm bảo UI/template tiếng Việt; nếu muốn demo cũng tiếng Việt, cần thiết kế default theo locale tại thời điểm tạo user.

## Các bước thực hiện khi được phép code

1. Đọc lại `harness-engineering/ke-hoach.md`, `android.md`, `bao-mat-du-lieu.md`, `quy-trinh-code.md` trước khi sửa.
2. Chạy `git status --short` để tách thay đổi có sẵn của người dùng khỏi thay đổi mới.
3. Thêm Tiếng Việt vào `LanguageManager` và màn hình chọn ngôn ngữ.
4. Thêm `values-vi/strings.xml` đầy đủ, đồng thời thêm key mới vào mọi locale hiện có.
5. Refactor các text hard-code người dùng nhìn thấy sang resource, ưu tiên theo luồng:
   - Language selection.
   - Emergency screen và SOS.
   - Edit profile.
   - QR/NFC.
   - Share/PDF/notification/biometric/follow-up.
6. Sửa `EmergencyNumberResolver` để Tiếng Việt dùng `115` và các label/nút gọi cấp cứu nhận số động.
7. Kiểm tra không thêm log chứa dữ liệu y tế, số điện thoại, vị trí hoặc token.
8. Đọc diff toàn bộ phần Android để chắc chắn không đổi schema Room, không đổi secret, không chạm asset nhị phân.

## Kiểm chứng dự kiến

Chạy các lệnh:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
git diff --check
```

Nếu chỉ sửa resource/UI nhiều và môi trường cho phép, chạy thêm:

```bash
./gradlew :app:lintDebug
```

Kiểm tra thủ công trên emulator/device:

- Mở `Select Language`, thấy option `Tiếng Việt`.
- Chọn `Tiếng Việt`, bấm apply, app quay về màn hình ID và các label chính là tiếng Việt.
- Bottom nav, QR, Edit Profile, toast/notification, share/PDF template không còn tiếng Anh do app sinh ra.
- Nút gọi cấp cứu hiển thị `115` và dial intent là `tel:115`.
- Auto-call sau SOS dùng `115`.
- SOS countdown/cancel/fallback text là tiếng Việt.
- Offline mode/online sync vẫn hiển thị đúng.
- Không gửi SOS thật trong test thủ công nếu không có môi trường test an toàn.

## Rủi ro và điểm cần chú ý

- Số cấp cứu là luồng an toàn cao. Việc buộc Tiếng Việt dùng `115` đúng theo yêu cầu hiện tại, nhưng có thể không phù hợp nếu người dùng đang ở nước ngoài.
- Android notification channel đã tạo không tự đổi tên trên thiết bị đã cài app trước đó.
- PDF tiếng Việt có dấu có thể cần font Unicode; nếu Helvetica lỗi glyph thì cần thêm xử lý font nhưng không thêm asset/font lớn nếu chưa cần.
- Các dữ liệu profile đã lưu bằng tiếng Anh sẽ vẫn là tiếng Anh vì đó là dữ liệu người dùng/Firebase, không phải UI string.
- Nếu web public profile cũng phải tiếng Việt, cần task riêng cho `helper-id/components/EmergencyProfilePage.tsx` và API/profile rendering.

## Ngoài phạm vi trong bước kế hoạch này

- Không sửa code Kotlin/resource ngay trong prompt này.
- Không sửa web/API.
- Không đổi Room schema hoặc migration.
- Không thêm dependency.
- Không sửa asset PNG/JAR/package-lock.
