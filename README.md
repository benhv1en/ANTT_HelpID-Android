# HelpID

HelpID là ứng dụng hồ sơ y tế khẩn cấp: người dùng chuẩn bị trước thông tin y tế quan trọng (nhóm máu, dị ứng, liên hệ khẩn cấp...) để người ứng cứu có thể truy cập nhanh qua QR/NFC khi cần.

Repo gồm 3 phần độc lập:

- `app/` — Ứng dụng Android (Kotlin, Jetpack Compose, Room, Firebase, WorkManager)
- `backend/HelpId.Api/` — Backend API (ASP.NET Core, EF Core, SQLite, JWT) + `backend/HelpId.Api.Tests/`
- `helper-id/` — Trang web (React 19 + Vite 6) + API serverless trên Vercel

File này hướng dẫn từng bước cài đặt và chạy cả 3 phần trên máy local.

## Yêu cầu hệ thống

| Thành phần | Yêu cầu |
|---|---|
| Backend | .NET 8 SDK |
| Android | JDK 17 hoặc 21 (JDK 25 sẽ lỗi build), Android Studio hoặc Android SDK command-line tools, 1 máy ảo/thiết bị Android (API 24+) |
| Web | Node.js 18+ và npm |

Kiểm tra nhanh:

```bash
dotnet --version     # cần có SDK 8.x
node --version
npm --version
echo $JAVA_HOME       # phải trỏ tới JDK 17 hoặc 21 khi build Android
```

## 1. Cài đặt & chạy Backend (`backend/HelpId.Api`)

### 1.1. Cài EF Core CLI tool (chỉ cần làm 1 lần)

```bash
dotnet tool install --global dotnet-ef
```

### 1.2. Tạo file secrets `.env.local`

```bash
cp backend/.env.local.example backend/.env.local
```

Mở `backend/.env.local` và điền giá trị thật cho:

- `HELPID_AUTH_JWT_SIGNING_KEY` — chuỗi ngẫu nhiên tối thiểu 32 byte
- `HELPID_PROFILE_JWT_SIGNING_KEY` — chuỗi ngẫu nhiên tối thiểu 32 byte (khác key trên)
- `PublicWeb__BaseUrl` — URL của frontend web (mặc định `http://localhost:5173`)

File `.env.local` **không được commit** (đã có trong `.gitignore`).

### 1.3. Tạo dev HTTPS certificate (chỉ cần làm 1 lần trên máy)

```bash
dotnet dev-certs https --trust
```

### 1.4. Chạy backend

```bash
./run-backend.sh
```

Script này sẽ tự động: nạp `.env.local`, áp dụng EF Core migration vào SQLite (`backend/HelpId.Api/App_Data/helpid-dev.db`), rồi chạy backend ở:

- HTTP: `http://localhost:5080`
- HTTPS: `https://localhost:7080`

Kiểm tra backend đã chạy:

```bash
curl -k https://localhost:7080/health
```

Nếu không dùng script, có thể chạy thủ công:

```bash
cd backend/HelpId.Api
dotnet build
dotnet run --urls "http://0.0.0.0:5080;https://0.0.0.0:7080"
```

### 1.5. Chạy test backend

```bash
cd backend/HelpId.Api.Tests
dotnet test
```

## 2. Cài đặt & chạy Android app (`app/`)

### 2.1. Cấu hình Firebase (bắt buộc)

App cần file `app/google-services.json` (không được commit vào repo vì chứa key riêng của từng project Firebase). Tự tạo project Firebase của bạn tại [Firebase Console](https://console.firebase.google.com/), thêm Android app với package name `com.helpid.app`, rồi tải file `google-services.json` về và đặt vào `app/google-services.json`.

### 2.2. Build app

JDK build phải là 17 hoặc 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug
```

(Thay đường dẫn `JAVA_HOME` cho đúng máy của bạn nếu không dùng Linux/JDK 17.)

### 2.3. Kết nối app với backend local

Nếu chạy Android Emulator và backend ở bước 1, forward cổng HTTPS để emulator gọi được `127.0.0.1:7080`:

```bash
adb reverse tcp:7080 tcp:7080
```

### 2.4. Cài app lên thiết bị/emulator

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:installDebug
```

### 2.5. Chạy test / lint Android

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:lintDebug
# Cần emulator/thiết bị đang chạy:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:connectedDebugAndroidTest
```

## 3. Cài đặt & chạy Web (`helper-id/`)

```bash
cd helper-id
npm install      # chỉ cần lần đầu
npm run dev      # chạy dev server (mặc định http://localhost:5173)
```

Build production:

```bash
npm run build
npx tsc --noEmit   # type-check không build
```

Chạy test web:

```bash
npm run test
```

**Lưu ý bảo mật:** mọi secret (API key, service account...) chỉ được đặt trong API serverless (`helper-id/api/`), tuyệt đối không đặt secret vào biến môi trường `VITE_*` (sẽ bị bundle lộ ra client).

## Kiến trúc tóm tắt

- Android có 2 hệ thống auth song song: Firebase anonymous auth (legacy, dùng cho Firestore sync và public emergency link) và Backend JWT auth (mới, gọi trực tiếp ASP.NET Core API — access token 15 phút, refresh token 30 ngày xoay vòng).
- Dữ liệu trên Android theo mô hình offline-first: đọc từ Room (SQLite local) trước, sau đó đồng bộ với remote (Firestore hoặc backend API); các trường nhạy cảm (tên, địa chỉ, dị ứng, ghi chú y tế, liên hệ khẩn cấp) được mã hoá AES-GCM trước khi lưu vào Room.
- Backend luôn lấy `userId` từ JWT claim `sub`, không bao giờ tin `userId` gửi từ body/query của client.
- Giao tiếp Android ↔ backend qua HTTPS được bảo vệ bằng Certificate Pinning (SPKI pin trong `app/src/main/res/xml/network_security_config.xml`); ở debug build, Android tự tắt pin-checking để thuận tiện phát triển local — pin chỉ thật sự được enforce ở release build.
