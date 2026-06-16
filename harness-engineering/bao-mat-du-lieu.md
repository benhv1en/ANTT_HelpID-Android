# Bảo mật và dữ liệu nhạy cảm

## Loại dữ liệu nhạy cảm

Repo xử lý:

- Tên người dùng.
- Nhóm máu.
- Địa chỉ.
- Dị ứng.
- Ghi chú y tế.
- Liên hệ khẩn cấp và số điện thoại.
- Vị trí hiện tại trong luồng SOS.
- Firebase uid và ID token.
- Backend access token và refresh token.
- Public profile key và JWT ngắn hạn.

Đối xử các dữ liệu này như PII/health data. Không log, không dùng fixture thật, không đưa vào screenshot công khai.

## Lưu trữ Android

Room cache lưu profile local. Trước khi ghi Room, `FirebaseRepository.mapDomainToLocal()` cố mã hóa:

- name
- bloodGroup
- address
- allergies
- medicalNotes
- emergency contact name/phone

Mã hóa dùng AndroidKeyStore. Dữ liệu có prefix `enc::`.

Rủi ro:

- Nếu AndroidKeyStore key mất hoặc không decrypt được, field mã hóa trả rỗng.
- `SecurePrefs` fallback về SharedPreferences thường nếu EncryptedSharedPreferences lỗi.
- Không backup được key qua reinstall như dữ liệu thường.

Khi sửa:

- Không bỏ prefix `enc::`.
- Không lưu bản rõ vào file mới.

## Lưu trữ biometric

`BiometricPreferenceStore` dùng `EncryptedSharedPreferences` (file `helpid_biometric_prefs`) để lưu trạng thái bật/tắt và timestamp mở khóa gần nhất theo user.

Dữ liệu lưu:

- `biometric_enabled_user_{sha256(userId)}`: boolean — user đã bật hay chưa.
- `last_unlocked_at_epoch_ms_user_{sha256(userId)}`: Long — lần mở khóa gần nhất.

KHÔNG được lưu:

- Vân tay/biometric template.
- Biometric identifier từ hệ thống.
- Access token hoặc refresh token trong store này.
- Email, tên, số điện thoại, dữ liệu y tế.

Quy tắc bắt buộc:

- `BiometricPrompt.AuthenticationResult` không được forward ra ngoài callback; callback chỉ gọi `onSuccess()`.
- Không truyền biometric result, error code, hay userId qua log.
- Khi logout hoặc refresh trả 401/403, phải gọi `biometricStore.clearForUser(userId)` để tránh session biometric của user cũ ảnh hưởng user mới.
- UserId được hash SHA-256 trước khi dùng làm suffix của key để tránh lộ định danh trong prefs key.
- Không đổi alias key nếu không có migration dữ liệu.

## Backend auth/API mới

Backend `backend/HelpId.Api` lưu auth/profile/link trong SQLite qua EF Core code-first.

Quy tắc bắt buộc:

- Password hash bằng PBKDF2 implementation trong backend; không lưu plaintext password.
- Refresh token là opaque random token; database chỉ lưu hash, rotate mỗi lần refresh và revoke cả family khi phát hiện reuse.
- Access JWT không chứa dữ liệu y tế, số điện thoại, địa chỉ, refresh token hoặc public profile key; claim email không cần thiết cho authorization và không nên thêm lại nếu không có lý do rõ.
- Profile/private link query phải filter theo user hiện tại từ JWT `sub` và permission policy.
- Public profile chỉ trả whitelist: `name`, `bloodGroup`, `allergies`, `emergencyContacts`, `address`, `medicalNotes`.
- Dùng EF Core LINQ/parameterized query; không dùng raw SQL ghép chuỗi từ input.
- Audit/log chỉ ghi event type, status/reason code, timestamp và hash IP/user-agent nếu cần; không ghi PII/health data/token.

## Đồng bộ Firebase legacy

Firestore collection chính:

- `users/{uid}`: profile.
- `publicKeys/{publicKey}`: mapping public key sang uid.

App hiện gửi profile bản rõ lên Firestore. Vì vậy Firebase rules và service account phải được kiểm soát ngoài repo. Không commit service account hoặc rules giả định nếu chưa được yêu cầu.

## Public profile link

Public link an toàn hơn uid trực tiếp vì:

- URL dùng public key `HID-*`, không lộ uid.
- Token JWT hết hạn sau 3 giờ.
- API profile whitelist field trả về.
- Route và API noindex/noarchive.

Không làm các việc sau:

- Không đưa uid vào URL public.
- Không kéo toàn bộ document Firestore về client.
- Không tăng thời hạn token tùy tiện.
- Không cache response profile.
- Không expose token trong log/error UI.

## SOS và location

Location chỉ nên lấy khi:

- Người dùng kích hoạt SOS hoặc follow-up đã được schedule từ SOS.
- Permission location đã được cấp.

Khi gửi SMS:

- Không gửi nếu thiếu contact.
- Không gửi nếu profile vẫn là default demo.
- Không crash nếu `SmsManager` lỗi.
- Không log phone/location/message.

WorkManager follow-up phải dừng được bằng unique work name `helpid_sos_follow_up`.

## Web/API secrets

Biến env backend cần giữ server-side:

- `HELPID_AUTH_JWT_SIGNING_KEY`
- `HELPID_PROFILE_JWT_SIGNING_KEY`
- `ConnectionStrings__HelpIdDb`
- `PublicWeb__BaseUrl`

Biến env Vercel cần giữ server-side:

- `FIREBASE_SERVICE_ACCOUNT_KEY`
- `PROFILE_JWT_SECRET`
- `GEMINI_API_KEY`
- `GEMINI_PROXY_TOKEN`
- `HELPID_BACKEND_URL`

Không dùng prefix `VITE_` cho các biến này.

Nếu cần debug API:

- Log loại lỗi và status.
- Không log request body chứa dữ liệu y tế.
- Không log Authorization header.
- Không log JWT token.

## Checklist bảo mật khi review diff

- Có secret mới trong diff không?
- Có log PII/health data/token/location không?
- Có field mới public qua `/api/profile` không?
- Có thay đổi token expiry hoặc public key regex không?
- Có thay đổi permission Android không?
- Có thay đổi lưu trữ local làm mất mã hóa không?
- Có dependency network/analytics mới không?
- Có text pháp lý hoặc privacy claim mới mâu thuẫn với code không?
