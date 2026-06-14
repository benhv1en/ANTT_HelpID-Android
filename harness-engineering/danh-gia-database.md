# Đánh giá database của HelpID

Thời điểm đánh giá: 14/06/2026 15:20:09

## Kết luận ngắn

Database hiện tại đã là lựa chọn tối ưu nhất cho kiến trúc và yêu cầu hiện tại của HelpID. Không nên đổi database ở thời điểm này.

Cụ thể, project đang dùng hai lớp database đúng với hai bài toán khác nhau:

- Local Android: Room trên SQLite, database name `helpid_database`, version `2`, table chính `user_profile`.
- Cloud/backend: Cloud Firestore, chủ yếu dùng collections `users/{uid}` và `publicKeys/{publicKey}`.

Đây không phải là khẳng định rằng Room hoặc Firestore luôn là lựa chọn tốt nhất cho mọi sản phẩm. Đây là kết luận theo ngữ cảnh repo hiện tại: app Android xử lý hồ sơ y tế khẩn cấp, cần đọc offline nhanh, ghi local trước, đồng bộ cloud sau, dùng Firebase Auth ẩn danh, Vercel serverless API và public emergency link có JWT ngắn hạn.

## Database đang dùng trong repo

### Android local database

Các file xác nhận:

- `app/build.gradle.kts`: khai báo Room runtime, Room KTX và KSP compiler.
- `app/src/main/java/com/helpid/app/data/local/AppDatabase.kt`: khai báo `@Database(entities = [LocalUserProfile::class], version = 2)`, tạo Room database tên `helpid_database` và migration `1 -> 2`.
- `app/src/main/java/com/helpid/app/data/local/LocalUserProfile.kt`: entity `user_profile`, lưu hồ sơ, dị ứng, medical notes, emergency contacts, language và `lastUpdated`.
- `app/src/main/java/com/helpid/app/data/local/UserProfileDao.kt`: DAO có `getUserProfile`, `insertUserProfile`, `deleteUserProfile`.
- `app/src/main/java/com/helpid/app/data/FirebaseRepository.kt`: đọc Room trước, fetch Firestore sau, và luôn lưu local trước khi sync remote.

Vai trò của Room trong app:

- Là cache local offline-first cho hồ sơ khẩn cấp.
- Cho phép app vẫn hiển thị hồ sơ khi Firebase hoặc mạng lỗi.
- Lưu dữ liệu có cấu trúc: profile, list dị ứng, list medical notes, list emergency contacts.
- Có migration bảo toàn dữ liệu người dùng.
- Kết hợp mã hóa field nhạy cảm qua `SensitiveDataCipher` trước khi ghi Room.

### Cloud database

Các file xác nhận:

- `app/src/main/java/com/helpid/app/data/FirebaseRepository.kt`: dùng `FirebaseFirestore.getInstance()`, đọc/ghi `users/{uid}`, lấy Firebase ID token rồi gọi `/api/mint`.
- `helper-id/package.json`: backend Vercel dùng `firebase-admin`.
- `helper-id/api/mint.js`: xác thực Firebase ID token, tạo hoặc tái dùng public key `HID-*`, ghi mapping `publicKeys/{publicKey}` sang `uid`, ký JWT 3 giờ.
- `helper-id/api/profile.js`: xác thực JWT, đọc `publicKeys/{key}`, đọc `users/{uid}`, trả profile đã whitelist.

Vai trò của Firestore:

- Là nguồn sync cloud cho hồ sơ người dùng.
- Tích hợp trực tiếp với Firebase Auth ẩn danh đang dùng trong Android.
- Cho phép Vercel serverless API dùng Firebase Admin để xác thực token và đọc profile.
- Dữ liệu hiện là document-oriented: một hồ sơ người dùng và một mapping public key, chưa có join phức tạp.

## Vì sao Room/SQLite là tối ưu cho local Android

Room phù hợp nhất vì app cần lưu dữ liệu có cấu trúc, riêng tư, đọc được ngay cả khi offline. Android documentation phân loại structured data nên dùng database, và Room là lớp persistence trên SQLite có compile-time verification, annotation giảm boilerplate và migration path rõ ràng. Android documentation cũng khuyến nghị dùng Room thay vì gọi SQLite API trực tiếp.

So với các lựa chọn khác:

- SharedPreferences/DataStore không phù hợp làm database hồ sơ vì chúng thiên về key-value/proto nhỏ, không tốt cho entity có list liên hệ, migration schema và truy vấn có cấu trúc.
- Raw SQLite làm được nhưng tăng boilerplate, dễ lỗi migration/query hơn, không tận dụng pattern Jetpack hiện có.
- Realm/ObjectBox không cần thiết vì dữ liệu hiện nhỏ, schema đơn giản, không cần object database hoặc sync engine riêng; thêm dependency/vendor lock-in không tạo lợi ích tương xứng.
- Firestore offline cache không thay thế Room vì app đang cần cache local có kiểm soát, có mã hóa field nhạy cảm ở tầng app, và vẫn đọc được theo luồng repository hiện tại ngay cả khi remote lỗi.
- SQLCipher có thể là hướng tăng cường bảo mật nếu sau này bắt buộc mã hóa toàn bộ file database, nhưng hiện tại chưa đủ lý do để thay Room. App đã mã hóa field nhạy cảm trước khi ghi Room; đổi sang SQLCipher sẽ thêm native dependency và bài toán quản lý key mà chưa giải quyết thêm use-case hiện tại.

Vì vậy, giữ Room/SQLite cho local là lựa chọn đúng nhất hiện tại.

## Vì sao Firestore là tối ưu cho cloud/backend

Firestore phù hợp nhất vì dữ liệu của HelpID hiện là hồ sơ dạng document, ít quan hệ, cần sync mobile, cần tích hợp Firebase Auth và cần serverless API đọc bằng Firebase Admin. Tài liệu Firebase mô tả Firestore là database NoSQL có mô hình collection/document, hỗ trợ sync dữ liệu và có security rules/IAM cho client/server. Tài liệu so sánh chính thức của Firebase cũng đánh dấu Firestore là lựa chọn ưu tiên hơn Realtime Database trong các mục như data model dạng collections/documents, query, transaction, reliability, scalability và security.

So với các lựa chọn khác:

- Firebase Realtime Database mạnh cho presence hoặc state-sync latency cực thấp, nhưng HelpID không có yêu cầu presence; dữ liệu hồ sơ và public key mapping hợp với document model của Firestore hơn.
- PostgreSQL/Supabase/Neon/Cloud SQL/Firebase SQL Connect hợp với quan hệ phức tạp, reporting, audit SQL và transaction nhiều bảng. Repo hiện chưa có join/report/admin workflow như vậy; chuyển sang SQL sẽ buộc thêm backend layer, migration vận hành, connection pooling trong serverless, auth mapping và chi phí bảo trì cao hơn.
- MongoDB Atlas tương đồng document database nhưng không tích hợp Firebase Auth/Admin SDK trơn tru bằng Firestore trong kiến trúc hiện tại.
- DynamoDB có thể scale rất lớn nhưng tăng độ phức tạp về access pattern, IAM/AWS vận hành và không ăn khớp tự nhiên với Firebase Auth đang dùng.
- Self-hosted database không phù hợp cho app khẩn cấp ở giai đoạn này vì tăng rủi ro vận hành, backup, uptime, bảo mật và incident response.

Vì vậy, giữ Firestore cho cloud là lựa chọn tối ưu nhất hiện tại.

## Điểm chưa hoàn hảo nhưng không phải lý do đổi database

- `pending_profile` hiện lưu trong secure prefs, không nằm trong Room. Nếu sau này pending sync có nhiều record, retry state phức tạp hoặc cần audit local, nên chuyển pending queue vào Room thay vì đổi database.
- `exportSchema = false` trong `AppDatabase` làm Room không xuất schema để review/test migration. Nếu muốn nghiêm túc hơn với dữ liệu người dùng, nên bật export schema và thêm migration test; đây là cải tiến quy trình, không phải lý do đổi database.
- Field-level encryption hiện phụ thuộc `SensitiveDataCipher`; nếu key lỗi thì field mã hóa trả rỗng. Nên có test/telemetry an toàn cho lỗi decrypt nhưng tuyệt đối không log dữ liệu y tế.
- Firestore đang chứa dữ liệu y tế/PII. Nếu sản phẩm đi vào môi trường y tế pháp lý nghiêm ngặt, cần đánh giá compliance, region, retention, audit log, backup/restore, DPA/BAA và quy trình xóa dữ liệu. Nếu các yêu cầu này vượt Firestore/Firebase hiện tại, lúc đó mới mở lại quyết định database.

## Khi nào mới nên cân nhắc đổi database

Chỉ nên lập kế hoạch migration nếu xuất hiện một hoặc nhiều điều kiện sau:

- Cần truy vấn quan hệ phức tạp: tổ chức, bác sĩ, caregiver, thiết bị, nhiều hồ sơ, phân quyền nhiều tầng, audit log dạng SQL.
- Cần reporting/analytics giao dịch phức tạp trên dữ liệu operational, không chỉ hồ sơ document.
- Cần compliance chính thức với yêu cầu lưu trữ y tế nghiêm ngặt mà Firebase/Firestore setup hiện tại không đáp ứng được.
- Cần multi-region/backup/restore/RPO/RTO hoặc data residency vượt khả năng cấu hình hiện tại.
- Cần end-to-end encryption hoặc server không được nhìn thấy plaintext profile; khi đó vấn đề chính là mô hình mã hóa và key management, không chỉ là đổi database.

Nếu các điều kiện này xảy ra, ứng viên đáng cân nhắc nhất cho cloud sẽ là PostgreSQL managed qua một backend riêng, không phải thay toàn bộ bằng Realtime Database hoặc MongoDB. Lý do: các yêu cầu nêu trên thường là relational, audit-heavy và transaction-heavy. Tuy nhiên đó là quyết định tương lai; hiện tại migration sang PostgreSQL sẽ là over-engineering.

## Khuyến nghị hiện tại

- Không đổi database.
- Giữ Room/SQLite local + Firestore cloud.
- Ưu tiên cải thiện quanh database hiện có:
  - Chuyển pending sync phức tạp vào Room nếu luồng offline mở rộng.
  - Bật Room schema export và thêm migration test khi schema tiếp tục thay đổi.
  - Rà soát Firestore Security Rules, IAM của service account, backup/restore và retention policy.
  - Không log dữ liệu y tế, số điện thoại, vị trí, Firebase token, JWT hoặc public profile token.
  - Khi thêm field hồ sơ mới, cập nhật đồng bộ `UserProfile`, Room entity/converters, Firestore mapping và `/api/profile` whitelist nếu field được phép public.

## Nguồn chính thức đã đối chiếu

- Android data storage overview: https://developer.android.com/training/data-storage
- Android Room documentation: https://developer.android.com/training/data-storage/room
- Cloud Firestore documentation: https://firebase.google.com/docs/firestore
- Firestore offline persistence: https://firebase.google.com/docs/firestore/manage-data/enable-offline
- Firebase comparison, Firestore vs Realtime Database: https://firebase.google.com/docs/database/rtdb-vs-firestore
