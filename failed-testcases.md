# Failed Testcases

15/06/2026 01:53:10
- Mục đích/nội dung testcase: Chạy Android debug build bằng môi trường Java mặc định sau khi thêm deep link intent-filter.
- Cách test: Chạy `./gradlew :app:assembleDebug --stacktrace` trong root repo, không override `JAVA_HOME`.
- Expected result: Android debug APK build thành công hoặc Gradle chỉ fail vì lỗi code/manifest cần sửa.
- Actual result: FAIL do môi trường dùng Java `25.0.3`; Kotlin/Gradle ném `java.lang.IllegalArgumentException: 25.0.3` khi parse Java version trước khi build code. Cùng code đã PASS khi chạy lại với `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- Kết luận: FAIL.
