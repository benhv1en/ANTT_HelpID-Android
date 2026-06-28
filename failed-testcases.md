# Failed Testcases

17/06/2026 11:10:00
- Mục đích/nội dung testcase: PROMPT 55 TC-04 — SOS button trên EmergencyScreen (emulator-5554 API 37).
- Cách test: Scroll xuống EmergencyScreen → tap "SEND SOS" button tại (540,2206).
- Expected result: SOS intent/SMS trigger không crash, không bị ảnh hưởng bởi certificate pinning.
- Actual result: FAIL — crash `java.lang.IllegalArgumentException: Can only use lower 16 bits for requestCode` trong `ActivityCompat.requestPermissions` → `ManagedActivityResultLauncher.launch`. Crash xảy ra khi SOS cố gắng request SMS/location permissions. Đây là pre-existing bug với Compose ActivityResult API (request code quá lớn), KHÔNG liên quan certificate pinning (không có SSL/HTTPS call nào trước crash).
- Kết luận: FAIL (pre-existing bug, ngoài scope NHÓM 10 — Certificate Pinning).

15/06/2026 01:53:10
- Mục đích/nội dung testcase: Chạy Android debug build bằng môi trường Java mặc định sau khi thêm deep link intent-filter.
- Cách test: Chạy `./gradlew :app:assembleDebug --stacktrace` trong root repo, không override `JAVA_HOME`.
- Expected result: Android debug APK build thành công hoặc Gradle chỉ fail vì lỗi code/manifest cần sửa.
- Actual result: FAIL do môi trường dùng Java `25.0.3`; Kotlin/Gradle ném `java.lang.IllegalArgumentException: 25.0.3` khi parse Java version trước khi build code. Cùng code đã PASS khi chạy lại với `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- Kết luận: FAIL.
