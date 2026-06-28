# Thiết kế bảo vệ màn hình — FLAG_SECURE và Lifecycle Mask

Thời điểm lập tài liệu: 17/06/2026 02:43:40

## 1. Threat model

### Vector A — Chụp màn hình và screen recording qua MediaProjection API

Android cho phép bất kỳ app nào có quyền `MEDIA_PROJECTION` chụp toàn bộ nội dung màn hình theo thời gian thực. App độc hại cùng thiết bị có thể dùng `MediaProjectionManager.createScreenCaptureIntent()` để capture buffer của SurfaceFlinger, bao gồm toàn bộ nội dung đang hiển thị trên màn hình.

Dữ liệu bị lộ nếu khai thác thành công:
- `EmergencyScreen`: họ tên, nhóm máu, địa chỉ, dị ứng, bệnh nền, danh sách liên hệ khẩn cấp kèm số điện thoại.
- `EditProfileScreen`: toàn bộ dữ liệu profile đang được chỉnh sửa.
- `QRScreen`: QR code mã hóa URL liên kết tới hồ sơ khẩn cấp công khai — scan được là truy cập được hồ sơ.

### Vector B — Ảnh thumbnail Recent Apps (nút Overview)

Khi user nhấn nút Overview, Android chụp snapshot màn hình hiện tại và lưu làm thumbnail. Thumbnail này hiển thị trong màn hình Recent Apps mà **không cần xác thực, không cần mở app**. Bất kỳ ai cầm điện thoại đều thấy toàn bộ dữ liệu y tế trong thumbnail nếu user vừa dùng EmergencyScreen, EditProfileScreen, hoặc QRScreen.

---

## 2. Giải pháp

### Giải pháp A — `FLAG_SECURE` (chặn MediaProjection capture)

Set `WindowManager.LayoutParams.FLAG_SECURE` trong `MainActivity.onCreate()` ngay sau `super.onCreate(savedInstanceState)` và trước `setContent {}`.

Cơ chế: flag này đánh dấu surface của window với bit `SECURE`. SurfaceFlinger từ chối cung cấp buffer của window này cho bất kỳ `VirtualDisplay` nào (bao gồm `MediaProjection`). Kết quả: ảnh chụp màn hình và screen recording chỉ ra màn hình đen cho window của HelpID.

Flag áp dụng **toàn app, không theo điều kiện** — vì app luôn có khả năng chứa dữ liệu y tế trong cache và trên màn hình.

### Giải pháp B — `SecureScreenWrapper` composable (chặn Recent Apps thumbnail)

Tạo composable `SecureScreenWrapper(content: @Composable () -> Unit)` dùng `LifecycleEventObserver` để theo dõi `Lifecycle.Event`:
- Khi nhận `ON_PAUSE` → set `isBackground = true` → phủ overlay fullscreen màu `MaterialTheme.colorScheme.background` lên trên content.
- Khi nhận `ON_RESUME` → set `isBackground = false` → overlay biến mất, content hiển thị bình thường.

Cơ chế: Android chụp thumbnail Recent Apps trong sự kiện `onPause()`. Khi overlay đã được phủ trước khi hệ thống chụp, thumbnail chỉ thấy màu nền trống — không có dữ liệu y tế.

Wrapper này **chỉ bọc các màn hình hiển thị dữ liệu y tế** — không bọc toàn bộ navigation.

---

## 3. Màn hình cần bọc SecureScreenWrapper

| Màn hình | Lý do cần bọc |
|---|---|
| `EmergencyScreen` | Hiển thị tên, nhóm máu, dị ứng, bệnh nền, liên hệ khẩn cấp |
| `EditProfileScreen` | Hiển thị và cho phép sửa toàn bộ hồ sơ y tế |
| `QRScreen` | Hiển thị QR code liên kết tới hồ sơ khẩn cấp công khai |

**KHÔNG bọc:**

| Màn hình | Lý do không cần bọc |
|---|---|
| `LoginScreen` | Chỉ có field email/password, không có dữ liệu y tế |
| `RegisterScreen` | Chỉ có field đăng ký, không có dữ liệu y tế |
| `BiometricLockScreen` (trong `MainActivity`) | Chỉ hiện icon khóa và nút unlock, không có dữ liệu y tế |
| `AdminScreen` | Hiện metadata user (email, role), không có dữ liệu y tế |
| `LanguageSelectionScreen` | Không có dữ liệu nhạy cảm |

---

## 4. Edge case phải xử lý

**Overlay không được delay hay animate:** Tuyệt đối không dùng `AnimatedVisibility` hay fade transition cho overlay. Nếu có animation, sẽ có khoảng thời gian ngắn lộ dữ liệu trước khi overlay che kịp. Overlay phải xuất hiện tức thì khi `ON_PAUSE`.

**Overlay phải invisible hoàn toàn khi `ON_RESUME`:** Sau khi user quay lại app, overlay phải biến mất ngay — không để che dữ liệu khi user đang dùng.

**FLAG_SECURE không theo điều kiện:** Không set/unset flag theo auth state hay màn hình hiện tại. Luôn set trong `onCreate()` và giữ nguyên suốt lifecycle của Activity.

**Không ảnh hưởng luồng SOS:** `EmergencyScreen` có nút SOS gửi SMS và gọi số khẩn cấp. `SecureScreenWrapper` chỉ bọc UI, không can thiệp vào logic — các action SOS vẫn hoạt động bình thường khi user đang nhìn màn hình (`isBackground == false`).

**Không ảnh hưởng offline cache (`LocalCacheOnly`):** Trong `MainActivity`, state `LocalCacheOnly` render `EmergencyScreen` với banner "session expired / offline". `EmergencyScreen` vẫn được bọc `SecureScreenWrapper` trong trường hợp này vì dữ liệu y tế từ Room cache vẫn hiển thị.

**Xung đột với `FLAG_SHOW_WHEN_LOCKED`:** `MainActivity.applyLockScreenFlagsIfNeeded()` set `FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON` cho luồng test. `FLAG_SECURE` không xung đột với hai flag này — chúng kiểm soát khác nhau: SHOW_WHEN_LOCKED quyết định window có hiện trên lock screen không, FLAG_SECURE quyết định window có bị capture không.

---

## 5. Không làm

- **Không tắt Recent Apps:** Xâm phạm UX nghiêm trọng, user không thể chuyển app.
- **Không dùng `setRecentsScreenshotEnabled(false)`:** API này không ổn định trên nhiều OEM (Samsung, Xiaomi, OPPO có behavior khác nhau) và không đáng tin cậy cho bảo mật.
- **Không xóa Room data khi ON_PAUSE:** Overlay che dữ liệu trên màn hình là đủ; không xóa cache.
- **Không thay đổi UML use-case:** Tính năng này không thêm actor hay luồng user mới — chỉ thêm bảo vệ cho màn hình hiện có.

---

## 6. Kế hoạch implement (tham khảo prompts 49–51)

| Bước | Nội dung |
|---|---|
| Prompt 49 | Thêm `FLAG_SECURE` vào `MainActivity.onCreate()` + tạo `SecureScreenWrapper.kt` |
| Prompt 50 | Bọc `EmergencyScreen`, `EditProfileScreen`, `QRScreen` bằng `SecureScreenWrapper` |
| Prompt 51 | Test thủ công 10 case + hardening + ghi kết quả `passed-testcases.md` |
