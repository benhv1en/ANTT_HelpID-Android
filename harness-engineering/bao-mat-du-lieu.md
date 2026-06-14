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
- Không đổi alias key nếu không có migration dữ liệu.

## Đồng bộ Firebase

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

Biến env cần giữ server-side:

- `FIREBASE_SERVICE_ACCOUNT_KEY`
- `PROFILE_JWT_SECRET`
- `GEMINI_API_KEY`
- `GEMINI_PROXY_TOKEN`

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
