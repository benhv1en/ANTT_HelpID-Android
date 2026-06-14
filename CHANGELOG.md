14/06/2026 14:16:49
- Thêm Tiếng Việt cho Android app: bổ sung lựa chọn `Tiếng Việt`, tạo `values-vi/strings.xml`, chuyển các text UI/SOS/share/PDF/notification/biometric sang resource, dùng số cấp cứu Việt Nam `115` khi chọn `vi-VN`, thêm test cho `EmergencyNumberResolver` và kiểm chứng bằng unit test/build/lint.

14/06/2026 13:52:15
- Viết mới `harness-engineering/ke-hoach.md` với kế hoạch thêm Tiếng Việt vào màn hình chọn ngôn ngữ và Việt hóa toàn bộ app Android khi chọn `vi-VN`, bao gồm quy tắc dùng số cấp cứu y tế Việt Nam `115`.

14/06/2026 12:52:46
- Viết mới `harness-engineering/ke-hoach.md` với kế hoạch xử lý lỗi thiếu `app/google-services.json`, giữ nguyên nguyên tắc không commit secret Firebase và nêu hai hướng xử lý: dùng file Firebase thật local hoặc thiết kế chế độ debug không Firebase nếu được yêu cầu.

14/06/2026 05:11:14
- Sửa format `CHANGELOG.md` và quy tắc trong `AGENTS.md` để dùng ngày giờ bình thường dạng `dd/mm/yyyy hh:mm:ss`, không dùng dấu cộng trong timestamp.

14/06/2026 05:07:50
- Ghi kế hoạch sửa lỗi Gradle Java home vào `harness-engineering/ke-hoach.md`; cập nhật `AGENTS.md` để kế hoạch luôn được viết mới vào `harness-engineering/ke-hoach.md` và phải đọc lại kế hoạch trước khi sửa; xóa `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr` khỏi `gradle.properties`; xóa fallback `JAVA_HOME` hard-code trong `gradlew.bat`; cập nhật ghi chú JDK trong `AGENTS.md` và `harness-engineering/quy-trinh-test.md` để dùng Gradle JVM hoặc `JAVA_HOME` cục bộ JDK 17/21; kiểm chứng build bằng JDK 17 đã qua lỗi Java home và dừng ở lỗi thiếu `app/google-services.json`; tạo `CHANGELOG.md` và bổ sung quy tắc cập nhật changelog lên đầu file sau mỗi prompt có sửa đổi.
