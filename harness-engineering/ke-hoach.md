# Kế hoạch sửa lỗi: Màn hình EditProfile bị treo ở "SAVING..."

Thời điểm lập kế hoạch: 16/06/2026 16:30:00

## Mô tả lỗi

Sau khi đăng nhập thành công qua backend JWT, user nhập thông tin vào form hồ sơ rồi nhấn Save. Màn hình bị treo mãi ở trạng thái "SAVING..." và không bao giờ hoàn thành.

## Nguyên nhân gốc rễ

`EditProfileScreen.kt` (dòng 89) khởi tạo `FirebaseRepository` thay vì dùng backend profile API:

```kotlin
val repository = remember { FirebaseRepository(context) }
```

Khi lưu hồ sơ (dòng 533), nó gọi `repository.updateUserProfile()` → bên trong gọi:
```kotlin
firestore.collection("users").document(userId).set(updatedProfile.toMap()).await()
```

User đăng nhập bằng backend JWT, không phải Firebase Auth. Firestore `await()` có thể treo vô hạn khi:
- Firebase không có session hợp lệ
- Security rules từ chối nhưng client đợi timeout rất lâu (không có timeout mặc định trên Firestore Android client)

Ngoài ra, `onSaveSuccess()` và `onBackClick()` được gọi từ `withContext(Dispatchers.IO)` thay vì main thread — có thể gây vấn đề với navigation callbacks.

## Kế hoạch sửa

### Phần 1 — Tạo HelpIdApiProfileRepository (Android data layer)
- File mới: `app/src/main/java/com/helpid/app/data/HelpIdApiProfileRepository.kt`
- Dùng `AuthTokenStore` để lấy access token
- `getProfile()`: GET /api/v1/profile → parse JSON → map sang `UserProfile` → lưu Room
- `updateProfile()`: PUT /api/v1/profile → parse response → lưu Room; nếu lỗi network → lưu Room + set pending flag
- Nếu access token hết hạn → gọi `HelpIdApiAuthRepository.refresh()` → retry 1 lần

### Phần 2 — Sửa EditProfileScreen
- Thay `FirebaseRepository` bằng `HelpIdApiProfileRepository` cho load/save
- Bọc toàn bộ save coroutine trong try/catch để `isSaving.value = false` luôn được gọi kể cả khi exception
- Gọi `onSaveSuccess()` và `onBackClick()` trên main thread (withContext(Dispatchers.Main))
- Hiển thị snackbar/error text khi save thất bại thay vì chỉ im lặng

### Phần 3 — Test
- Unit test cho `HelpIdApiProfileRepository` (mock HTTP)
- Manual test: load profile, sửa, save → backend nhận đúng, Room cập nhật, màn hình quay về
- Manual test: save khi offline → Room lưu, pending flag set, không treo

## Không thay đổi
- Backend `PUT /api/v1/profile` — đã hoạt động đúng
- `FirebaseRepository` — giữ nguyên cho QR/emergency link flow
- Room schema — không đổi
