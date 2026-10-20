# Hướng Dẫn Cài Đặt & Khởi Chạy Dự Án (SETUP.md)

Tài liệu hướng dẫn từng bước (step-by-step) dành cho lập trình viên mới (Fresher) để thiết lập môi trường, khởi động cơ sở dữ liệu, build backend Spring Boot và chạy giao diện Web Dashboard trên máy local.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã cài đặt các công cụ sau:

| Công cụ | Phiên bản yêu cầu | Lệnh kiểm tra trên Terminal | Ghi chú dành cho Fresher |
| :--- | :--- | :--- | :--- |
| **Java (JDK)** | Java 21 LTS | `java -version` | Khuyên dùng Eclipse Temurin 21 hoặc Oracle OpenJDK 21 |
| **Maven** | 3.9+ (hoặc dùng wrapper có sẵn) | `mvn -version` | Trong dự án đã có sẵn `./mvnw` và `mvnw.cmd` |
| **Docker & Compose** | Docker v24+, Compose v2+ | `docker compose version` | Bật Docker Desktop trước khi chạy |
| **Node.js & npm** | Node.js 20+ LTS | `node -v` | Cần để chạy giao diện Web React 19 + Vite 8 |
| **Git** | 2.40+ | `git --version` | Dùng để quản lý mã nguồn |

---

## 2. Quy Trình 4 Bước Khởi Chạy Dự Án

### Bước 1: Tạo Khóa Bí Mật Cho JWT (Bắt Buộc)
Hệ thống áp dụng cơ chế Fail-Fast: Nếu bạn quên không truyền khóa bí mật `APP_JWT_SECRET` (tối thiểu 256-bit = 64 ký tự hex), backend sẽ lập tức từ chối khởi động để ngăn ngừa lỗ hổng bảo mật.

Mở Terminal và thiết lập biến môi trường:

- **Trên Windows (PowerShell)**:
  ```powershell
  $env:APP_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
  ```
- **Trên macOS / Linux (Bash / Zsh)**:
  ```bash
  export APP_JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
  ```
*(Ghi chú: Bạn có thể tự sinh khóa ngẫu nhiên bằng lệnh: `openssl rand -hex 32`)*

---

### Bước 2: Khởi Động Cơ Sở Dữ Liệu PostgreSQL Bằng Docker
Hệ thống sử dụng PostgreSQL 16 được đóng gói sẵn trong `docker-compose.yml` (chạy ở cổng `5436` để không bị xung đột với Postgres cổng `5432` mặc định nếu máy bạn đã có sẵn):

```bash
# Khởi động PostgreSQL chạy ngầm
docker compose up -d

# Kiểm tra container đã hoạt động bình thường
docker compose ps
```

---

### Bước 3: Khởi Động Backend Service (Spring Boot 3.3)
Khi ứng dụng khởi động, công cụ **Flyway** sẽ tự động quét thư mục `src/main/resources/db/migration` và áp dụng toàn bộ các migration từ `V1` đến `V11` (tạo bảng, khởi tạo ví hệ thống, tạo chỉ mục...). Bạn không cần phải chạy SQL thủ công bằng tay!

- **Trên Windows (PowerShell / CMD)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Trên macOS / Linux**:
  ```bash
  ./mvnw spring-boot:run
  ```

Kiểm tra ứng dụng đã sẵn sàng:
- Địa chỉ Backend: `http://localhost:8080`
- Kiểm tra Health Check: Mở trình duyệt vào `http://localhost:8080/actuator/health` -> hiện `{"status":"UP"}` là thành công!

---

### Bước 4: Khởi Động Giao Diện Web Dashboard (React 19 + Vite 8)
Mở một cửa sổ Terminal mới và di chuyển vào thư mục `web`:

```bash
cd web

# Cài đặt các thư viện frontend
npm install

# Khởi chạy dev server
npm run dev
```

Mở trình duyệt truy cập: **`http://localhost:5173`**.  
Bạn sẽ thấy giao diện đăng nhập / đăng ký ví điện tử hiển thị với đầy đủ tính năng Nạp tiền, Rút tiền, Chuyển tiền P2P và Sao kê giao dịch!

---

## 3. Hướng Dẫn Chạy Test Kiểm Thử

Dự án được bảo vệ bởi 100+ test cases bao phủ từ Unit Test đến Concurrency Integration Test:

```bash
# 1. Chạy các Unit Test nhanh (không cần Docker):
./mvnw test -Dtest="*ServiceTest,*ControllerTest,DomainOopTest,JwtTokenProviderTest,TransactionPinValidatorTest"

# 2. Chạy toàn bộ test suite (Bao gồm Concurrency Test với Testcontainers PostgreSQL):
# Lưu ý: Cần bật sẵn Docker Desktop trước khi chạy lệnh này
./mvnw test
```

---

## 4. Kịch Bản Test Nhanh Luồng Nghiệp Vụ (cURL Cheat Sheet)

Để giúp Fresher hiểu rõ luồng đi của dữ liệu, dưới đây là kịch bản gọi API thực tế:

### Bước 4.1: Đăng ký tài khoản khách hàng Alice
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "Password123!",
    "fullName": "Alice Wonderland",
    "currency": "USD"
  }'
```
*Server sẽ trả về `accessToken`, `userId` và `defaultWalletId` của Alice.*

Lưu mã token vào biến:
```bash
TOKEN="<dán-mã-accessToken-nhận-được-ở-trên-vào-đây>"
```

### Bước 4.2: Liên kết thẻ ngân hàng Visa
```bash
curl -X POST http://localhost:8080/api/v1/payment-methods \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "method_type": "DEBIT_CARD",
    "provider": "VISA",
    "card_number": "4242424242424242",
    "holder_name": "ALICE WONDERLAND",
    "is_default": true
  }'
```
*Ghi lại mã `id` của thẻ (ví dụ: `CARD_ID`).*

### Bước 4.3: Nạp 500 USD (50.000 cents) vào ví
```bash
curl -X POST http://localhost:8080/api/v1/transfers/topup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: TOPUP-DEMO-001" \
  -d '{
    "wallet_id": "<WALLET_ID_CỦA_ALICE>",
    "payment_method_id": "<CARD_ID_Ở_BƯỚC_4.2>",
    "amount": 50000,
    "description": "Nạp tiền demo đầu ngày"
  }'
```

### Bước 4.4: Xem số dư tức thì
```bash
curl -X GET http://localhost:8080/api/v1/wallets/<WALLET_ID_CỦA_ALICE>/balance \
  -H "Authorization: Bearer $TOKEN"
```
*Kết quả: `{"availableBalance": 50000, "ledgerBalance": 50000, "currency": "USD"}`.*

### Bước 4.5: Cài đặt mã PIN giao dịch 6 số
```bash
curl -X POST http://localhost:8080/api/v1/auth/pin \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"pin": "123456"}'
```

### Bước 4.6: Chuyển tiền P2P sang số tài khoản 12 số của Bob
```bash
curl -X POST http://localhost:8080/api/v1/transfers/p2p \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Transaction-Pin: 123456" \
  -H "Idempotency-Key: P2P-DEMO-001" \
  -d '{
    "source_wallet_id": "<WALLET_ID_CỦA_ALICE>",
    "destination_account_number": "882012345678",
    "amount": 15000,
    "description": "Chuyển tiền ăn trưa cho Bob"
  }'
```

### Bước 4.7: Xem sao kê giao dịch
```bash
curl -X GET "http://localhost:8080/api/v1/ledger/wallets/<WALLET_ID_CỦA_ALICE>/statement?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 5. Xử Lý Các Sự Cố Thường Gặp (Troubleshooting)

Khi mới bắt đầu, bạn có thể gặp một số lỗi phổ biến sau:

| Tình huống lỗi | Nguyên nhân | Cách khắc phục nhanh |
| :--- | :--- | :--- |
| **Lỗi: Port 5436 already in use** | Port 5436 đã bị một chương trình khác trên máy chiếm dụng | Mở `docker-compose.yml`, sửa `"5436:5432"` thành `"5437:5432"` và sửa thêm trong `application.properties` thành `localhost:5437` |
| **Backend crash: Missing JWT secret** | Bạn chưa khai báo biến môi trường `APP_JWT_SECRET` | Chạy lệnh thiết lập biến môi trường như ở Bước 1 rồi chạy lại backend |
| **Lỗi HTTP 401 Unauthorized khi gọi API** | Header Authorization thiếu Bearer token hoặc token đã hết hạn (sau 15 phút) | Đăng nhập lại hoặc gọi API refresh để lấy token mới |
| **Lỗi HTTP 429 Too Many Requests** | Đăng nhập sai mật khẩu quá 5 lần hoặc gõ sai PIN quá 5 lần | Đợi 15 phút để hệ thống tự mở khóa hoặc restart backend (bộ đếm lưu in-memory) |
| **Lỗi Java version incompatible** | Biến `JAVA_HOME` đang trỏ về phiên bản Java cũ (Java 8, 11 hoặc 17) | Cài đặt JDK 21 và trỏ lại đường dẫn biến môi trường `JAVA_HOME` về thư mục JDK 21 |
| **Testcontainers báo lỗi Docker environment** | Docker Desktop chưa được bật hoặc service Docker bị tắt | Mở phần mềm Docker Desktop trên máy và đợi đến khi biểu tượng hiện màu xanh lá |
