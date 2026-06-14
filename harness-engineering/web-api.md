# Ghi chú web và API

## Cấu hình

Project web nằm trong `helper-id/`.

Stack:

- React `19.2.4`.
- Vite `6.2.0` trong `package.json`, lockfile đang resolve Vite `6.4.1`.
- React Router `6.30.1` trong `package.json`, lockfile đang resolve `6.30.3`.
- Lucide React.
- Firebase Admin.
- jsonwebtoken.

Scripts:

```bash
npm run dev
npm run build
npm run preview
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

## API mint

`api/mint.js`:

- Chỉ nhận POST.
- Yêu cầu `Authorization: Bearer <Firebase ID token>`.
- Dùng Firebase Admin để verify ID token.
- Public key format: `HID-[A-Z0-9_-]{8,64}`.
- Nếu client gửi publicKey cũ, kiểm tra key thuộc cùng uid.
- Map `publicKeys/{publicKey}` sang `uid`.
- JWT payload gồm `{ k: publicKey }`, HS256, expires `3h`.
- URL trả về dựa trên forwarded host/proto đã sanitize.

Không thay đổi expiry hoặc field token mà không cập nhật Android và tài liệu.

## API profile

`api/profile.js`:

- Chỉ nhận GET.
- Yêu cầu `key` và `t`.
- Verify JWT bằng `PROFILE_JWT_SECRET`.
- Payload phải khớp key.
- Lấy uid qua `publicKeys/{key}`.
- Lấy profile ở `users/{uid}`.
- Trả whitelist:
  - `name`
  - `bloodGroup`
  - `allergies`
  - `emergencyContacts`
  - `address`
  - `medicalNotes`

Nếu thêm field public, phải trả lời được câu hỏi: field đó có an toàn để người quét QR xem không?

## API Gemini

`api/gemini.js`:

- Chỉ nhận POST.
- Body `{ prompt: string }`.
- Giới hạn prompt 4000 ký tự.
- Timeout upstream 15 giây.
- Nếu `GEMINI_PROXY_TOKEN` tồn tại, yêu cầu bearer token khớp.

Lưu ý: source import `node-fetch`; lockfile có `node-fetch` gián tiếp, nhưng `package.json` chưa khai báo trực tiếp. Nếu endpoint này là phần được bảo trì chính thức, nên thêm dependency trực tiếp.

## Header và noindex

`vercel.json` set `X-Robots-Tag: noindex, nofollow, noarchive` cho:

- `/e/(.*)`
- `/api/(.*)`

API cũng set:

- `Content-Type: application/json`
- `Cache-Control: no-store`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`

Giữ các header này khi sửa API.

## Thiết kế web

Website hiện dùng:

- Tailwind CDN trong `index.html`.
- Nhiều ảnh placeholder `picsum.photos`.
- Một video ngoài từ Pexels.
- Font Inter từ Google Fonts.

Khi làm sản phẩm nghiêm túc, thay placeholder bằng asset thật. Không dùng public emergency page như bề mặt marketing.
