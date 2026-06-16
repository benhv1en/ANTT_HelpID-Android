---
name: feedback-code-style
description: How the user wants tasks approached — terse code, Vietnamese docs, strict AGENTS.md rules
metadata:
  type: feedback
---

Luôn tuân thủ AGENTS.md trước khi sửa code. Đọc lại `harness-engineering/ke-hoach.md` trước mỗi lần sửa lớn.

Không viết comment giải thích WHAT — chỉ viết khi WHY không hiển nhiên. Không docstring dài. Không thêm code thừa ngoài scope task.

Mọi thời gian ghi vào changelog/testcase phải lấy bằng `date '+%d/%m/%Y %H:%M:%S'`.

CHANGELOG.md: chèn entry mới lên đầu, không append cuối.

Test log: passed vào `passed-testcases.md`, failed vào `failed-testcases.md`. Ghi đủ mục đích/cách test/expected/actual/kết luận. Không ghi PII, token, số điện thoại, dữ liệu y tế.

String resources: luôn cập nhật đủ 6 locale (values/, values-de/, values-es/, values-fr/, values-hi/, values-vi/) khi thêm key.

**Why:** Quy tắc trong AGENTS.md là bắt buộc, không phải gợi ý.
**How to apply:** Kiểm tra AGENTS.md checklist ở đầu mỗi task trước khi viết code.
