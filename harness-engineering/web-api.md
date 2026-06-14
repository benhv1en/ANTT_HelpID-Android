# Ghi chú web và API

## Cấu hình

Project web nằm trong `helper-id/`.

Stack:

- React `19.2.4`.
- Vite `6.2.0` trong `package.json`, lockfile đang resolve Vite `6.4.1`.
- React Router `6.30.1` trong `package.json`, lockfile đang resolve `6.30.3`.
- Lucide React.
- Firebase Admin cho `/api/mint` legacy.
- `node-fetch` khai báo trực tiếp cho serverless runtime cần fetch package.
- jsonwebtoken cho legacy JWT.

Scripts:

```bash
npm run dev
npm run build
npm run preview
npx tsc --noEmit
```

TypeScript cấu hình `allowJs: true`, `noEmit: true`, `moduleResolution: bundler`.

## Routing

`App.tsx` có hai lớp:

- `/e/:publicKey`: render `EmergencyProfilePage`, không dùng layout marketing.
- `/*`: render `MarketingSite`, có navbar, footer, sticky CTA.

Marketing routes:

- `/`
- `/product`
- `/about`
- `/terms-of-service`
- `/privacy-and-cookies`
- `/mission`

Vercel rewrite mọi route không phải `/api/*` về `index.html`.

## Public emergency page

`EmergencyProfilePage`:

- Lấy `publicKey` từ URL param.
- Lấy token từ query `t`.
- Set meta robots `noindex,nofollow,noarchive`.
- Fetch `/api/profile?key=...&t=...`.
- Render name, blood group, allergies, contacts, address, medical notes.
- Có deep link Android intent `intent://e/:key#Intent;scheme=helpid;package=com.helpid.app;end`.

Khi sửa trang này:

- Không thêm analytics/tracking mặc định.
- Không cache dữ liệu profile.
- Không render field chưa được whitelist ở API.
- Kiểm tra trạng thái loading, expired token, missing token.

## API profile proxy mới

`api/profile.js` hiện là proxy server-side sang backend ASP.NET Core.

Env bắt buộc:

- `HELPID_BACKEND_URL`: base URL backend, ví dụ `https://api.example.com` hoặc `http://127.0.0.1:5080` khi test local serverless.

Luồng:

1. Chỉ nhận `GET`.
2. Validate `key` theo `^HID-[A-Z0-9_-]{8,64}$` và token `t` có độ dài hợp lý.
3. Gọi `${HELPID_BACKEND_URL}/api/v1/public/profile?key=...&t=...`.
4. Nếu backend lỗi mạng/timeout, trả `503` generic.
5. Nếu backend trả lỗi, map message an toàn, không log token.
6. Nếu backend thành công, sanitize lại whitelist trước khi trả web.

Whitelist response:

- `name`
- `bloodGroup`
- `allergies`
- `emergencyContacts[].name`
- `emergencyContacts[].phone`
- `address`
- `medicalNotes`

Không trả `userId`, email, language, role, token, audit, metadata nội bộ.

## API mint legacy

`api/mint.js` vẫn là luồng Firebase legacy:

- Chỉ nhận POST.
- Yêu cầu `Authorization: Bearer <Firebase ID token>`.
- Dùng Firebase Admin để verify ID token.
- Public key format: `HID-[A-Z0-9_-]{8,64}`.
- Nếu client gửi publicKey cũ, kiểm tra key thuộc cùng uid.
- Map `publicKeys/{publicKey}` sang `uid` trong Firestore.
- JWT payload gồm `{ k: publicKey }`, HS256, expires `3h` bằng `PROFILE_JWT_SECRET` legacy.
- URL trả về dựa trên forwarded host/proto đã sanitize.

Luồng Android auth mới nên dùng backend `POST /api/v1/emergency-links/mint`. Không xóa `/api/mint` cho tới khi có kế hoạch gỡ Firebase/migration riêng.

## API Gemini

`api/gemini.js`:

- Chỉ nhận POST.
- Body `{ prompt: string }`.
- Giới hạn prompt 4000 ký tự.
- Timeout upstream 15 giây.
- Nếu `GEMINI_PROXY_TOKEN` tồn tại, yêu cầu bearer token khớp.
- Log lỗi bằng scope và tên lỗi, không log prompt hoặc token.

## Header và noindex

`vercel.json` set `X-Robots-Tag: noindex, nofollow, noarchive` cho:

- `/e/(.*)`
- `/api/(.*)`

API cũng set:

- `Content-Type: application/json`
- `Cache-Control: no-store`
- `Pragma: no-cache`
- `Expires: 0`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`

Giữ các header này khi sửa API.

## Env Vercel/API

- `HELPID_BACKEND_URL`: bắt buộc cho `/api/profile` proxy backend mới.
- `FIREBASE_SERVICE_ACCOUNT_KEY`: bắt buộc cho `/api/mint` legacy.
- `PROFILE_JWT_SECRET`: bắt buộc cho `/api/mint` legacy.
- `GEMINI_API_KEY`: bắt buộc cho `/api/gemini`.
- `GEMINI_PROXY_TOKEN`: tùy chọn, bật bearer token cho `/api/gemini`.

Không đưa các env này vào `VITE_*` hoặc client bundle.

## Thiết kế web

Website hiện dùng:

- Tailwind CDN trong `index.html`.
- Nhiều ảnh placeholder `picsum.photos`.
- Một video ngoài từ Pexels.
- Font Inter từ Google Fonts.

Khi làm sản phẩm nghiêm túc, thay placeholder bằng asset thật. Không dùng public emergency page như bề mặt marketing.
