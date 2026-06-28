# Script thuyết trình cho `demo-bao-mat-2-phut.mp4`

Khớp đúng timestamp với hành vi thật trong video (đã xác nhận bằng cách trích frame thật, không suy đoán). Đọc đúng nhịp theo mốc giây — mỗi đoạn được tính vừa đủ thời gian để nói trong khung đó.

---

**0:00 – 0:06 | Tiêu đề**
> "Đây là demo 3 tính năng bảo mật của HelpID: Certificate Pinning, Biometric, và FLAG_SECURE."

**0:06 – 0:34 | Certificate Pinning — trường hợp thất bại**
> "Đầu tiên là Certificate Pinning. Em sẽ đổi địa chỉ server trong app sang một server giả, dùng chứng chỉ tự ký — chứng chỉ này không khớp với SPKI pin đã khai báo trong `network_security_config.xml`. Khi em đăng nhập…"
*(t≈0:25–0:30: lỗi "Không có kết nối. Kiểm tra mạng và thử lại." xuất hiện)*
> "…kết nối bị từ chối ngay. Log của app ghi rõ: SSL handshake failed, possible MITM or cert change — đúng như cơ chế pinning được thiết kế để chặn."

**0:34 – 0:62 | Certificate Pinning — trường hợp thành công**
> "Giờ em đổi địa chỉ server lại về server thật, chứng chỉ khớp đúng SPKI đã pin."
*(t≈0:40–0:50: nhập lại email/mật khẩu)*
> "Đăng nhập lại…"
*(t≈0:60: đăng nhập thành công, vào màn hình hồ sơ khẩn cấp)*
> "…và lần này thành công. Điều này chứng minh pinning không chặn nhầm kết nối hợp lệ, chỉ chặn đúng kết nối có chứng chỉ sai."

**0:62 – 0:83 | Biometric — vào màn hình bật tính năng**
> "Tiếp theo là Biometric. Em vào màn hình Chỉnh sửa hồ sơ, kéo xuống tới mục Mở khóa bằng vân tay."

**0:83 | Biometric — bật toggle, hộp thoại vân tay hệ thống xuất hiện**
*(t≈0:83: chạm vào toggle, hộp thoại "Truy cập HelpID — Xác thực để xem thông tin khẩn cấp" hiện ra)*
> "Khi em bật toggle này, Android yêu cầu xác thực vân tay ngay — đây là hộp thoại BiometricPrompt thật của hệ thống, không phải dựng cảnh."

**0:83 – 0:92 | Giữ màn hình xác nhận đã bật**
*(sau khi chạm vân tay: "Đã bật mở khóa bằng vân tay")*
> "Xác thực xong, tính năng được bật."

**0:92 – 0:97 | Quay lại Emergency, tắt hẳn app**
> "Em quay lại màn hình chính, rồi tắt hẳn ứng dụng — không chỉ thu nhỏ, mà kill toàn bộ tiến trình."

**0:97 – 0:104 | Mở lại app — màn hình khóa kích hoạt**
*(t≈0:97–0:102: app khởi động lại từ đầu)*
> "Khi mở app lại từ đầu…"
*(t≈0:104: màn hình "Mở khóa HelpID" + hộp thoại vân tay hệ thống hiện ra)*
> "…màn hình khóa biometric tự động kích hoạt, bắt buộc xác thực vân tay trước khi vào được hồ sơ khẩn cấp."

**0:104 – 0:108 | Mở khóa thành công**
> "Xác thực vân tay, mở khóa, vào lại hồ sơ khẩn cấp như bình thường."

**0:108 – 0:120 | FLAG_SECURE**
> "Cuối cùng là FLAG_SECURE. Ngay lúc màn hình hồ sơ khẩn cấp đang mở, em chạy trực tiếp lệnh `adb shell screencap` để thử chụp màn hình từ bên ngoài."
*(caption hiện kết quả lệnh thật: file ảnh kéo về 0 byte)*
> "Kết quả: file ảnh kéo về có kích thước 0 byte — chứng tỏ FLAG_SECURE chặn hoàn toàn việc chụp/quay màn hình trong toàn bộ ứng dụng, ngay cả khi lệnh chụp được gọi trực tiếp qua adb, không qua giao diện app."

---

## Ghi chú khi trình bày

- Các mốc giây trên lấy từ video đã render sẵn (`demo-bao-mat-2-phut.mp4`, đúng 120.000s), đã xác nhận bằng cách trích frame thật ở từng mốc — không phải ước lượng.
- Caption chữ trắng nền đen đã có sẵn trong video, người nói chỉ cần khớp nhịp, không cần đọc lại y nguyên caption.
- Nếu bị hỏi tại sao quay được hộp thoại vân tay (bình thường `adb screenrecord` sẽ bị đen): trả lời là quay bằng capture cấp host (X11 window capture), tương đương nguyên lý "quay lại màn hình bằng máy quay ngoài" — không phải tắt FLAG_SECURE hay tính năng bảo mật nào của app.
