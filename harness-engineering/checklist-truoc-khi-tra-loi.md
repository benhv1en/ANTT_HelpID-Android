# Checklist trước khi trả lời

## Kiểm tra phạm vi

- Đã xử lý đúng yêu cầu mới nhất của người dùng.
- Không sửa file ngoài phạm vi.
- Không revert thay đổi có sẵn của người dùng.
- Không chạm file nhị phân/artifact nếu không cần.

## Kiểm tra code

- Đã đọc diff của mình.
- Không có secret, token, số điện thoại thật, vị trí thật hoặc dữ liệu y tế thật.
- String UI Android mới đã vào resource và các locale liên quan.
- Schema Room thay đổi đã có migration.
- API web vẫn validate input và set header an toàn.
- Public emergency route vẫn noindex.

## Kiểm tra lệnh

Chạy command phù hợp:

- Android: `./gradlew :app:assembleDebug`.
- Android unit: `./gradlew :app:testDebugUnitTest`.
- Android lint nếu sửa resource/manifest/UI nhiều: `./gradlew :app:lintDebug`.
- Web: `cd helper-id && npm run build`.
- Type check web nếu sửa TypeScript: `cd helper-id && npx tsc --noEmit`.
- Whitespace: `git diff --check`.

Nếu command không chạy được, ghi rõ:

- Command đã chạy.
- Lỗi chính.
- Có phải do môi trường, dependency, secret, emulator hay sandbox không.

## Kiểm tra trả lời cuối

- Trả lời ngắn gọn bằng tiếng Việt nếu người dùng đang dùng tiếng Việt.
- Nêu file đã tạo/sửa.
- Nêu test/build đã chạy hoặc chưa chạy.
- Nếu có follow-up thực sự hữu ích, gợi ý cụ thể, không viết lan man.
