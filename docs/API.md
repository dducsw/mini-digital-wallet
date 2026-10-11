# Mini Digital Wallet — API Specification

Tài liệu đặc tả toàn bộ REST API giao dịch tiền tệ cho **Mini Digital Wallet (mini-digital-wallet)**.

---

## 1. Quy ước Chung (General Conventions)

- **Base URL**: `http://localhost:8080/api/v1`
- **Content-Type**: `application/json`
- **Đơn vị Tiền tệ**: **USD** (minor unit = cents, $1.00 = 100 cents, số nguyên `Long`).
  - Ví dụ: `$50.00` $\rightarrow$ `5000`, `$1,000.00` $\rightarrow$ `100000`.
- **Idempotency Header (Phase 3, đã hiện thực)**: Các API ghi nhận tiền (`topup`, `withdraw`, `p2p`, `reversals`) nhận thêm:
  - Header `Idempotency-Key` (không bắt buộc): scope `(actor_id, operation, idempotency_key)` + SHA-256 `request_hash` + lease 30s.
  - Trùng key + khác payload → HTTP 422; trùng khi đang xử lý → HTTP 409; retry sau thành công → replay JSON cũ, không sinh ledger mới.
- **Chuẩn Định Dạng Lỗi (RFC 7807 Problem Details)**:
  ```json
  {
    "type": "https://minidigitalwallet.com/errors/insufficient-balance",
    "title": "Insufficient Balance",
    "status": 400,
    "detail": "Ví WAL_01HX98Z123 không đủ số dư. Khả dụng: 5000 cents, yêu cầu: 10000 cents",
    "instance": "/api/v1/transfers"
  }
  ```

---

## 2. Danh Sách REST APIs

### 2.0 Xác Thực & Người Dùng (Authentication & User)

#### Đăng ký tài khoản (Register)
- **Endpoint**: `POST /api/v1/auth/register`
- **Mô tả**: Đăng ký người dùng mới, tự động băm mật khẩu bằng BCrypt, tự động khởi tạo ví USD mặc định, cấp phát JWT Access Token.
- **Request Body**:
  ```json
  {
    "username": "alice",
    "email": "alice@example.com",
    "password": "Password123!",
    "full_name": "Alice Wonderland",
    "currency": "USD"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "alice",
    "email": "alice@example.com",
    "defaultWalletId": "5f425555-2f70-4d56-bc5e-828049fe93c6"
  }
  ```

#### Đăng nhập (Login)
- **Endpoint**: `POST /api/v1/auth/login`
- **Mô tả**: Kiểm tra username/password, trả về JWT Access Token nếu hợp lệ.
- **Request Body**:
  ```json
  {
    "username": "alice",
    "password": "Password123!"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "alice",
    "email": "alice@example.com",
    "defaultWalletId": "5f425555-2f70-4d56-bc5e-828049fe93c6"
  }
  ```

#### Lấy thông tin user hiện tại (Current User)
- **Endpoint**: `GET /api/v1/auth/me`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response (200 OK)**:
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "alice",
    "email": "alice@example.com",
    "fullName": "Alice Wonderland",
    "status": "ACTIVE",
    "createdAt": "2026-10-06T12:00:00Z"
  }
  ```

### 2.1 Quản Lý Phương Thức Thanh Toán / Thẻ Liên Kết (Payment Methods)

#### Liên kết thẻ mới (Link Card / Bank)
- **Endpoint**: `POST /api/v1/payment-methods`
- **Mô tả**: Liên kết thẻ hoặc tài khoản ngân hàng mới. Giả lập Payment Gateway tokenization, chỉ lưu masked number và token.
- **Request Body**:
  ```json
  {
    "owner_id": "USER_987654",
    "method_type": "DEBIT_CARD",
    "provider": "VISA",
    "card_number": "4242424242424242",
    "holder_name": "NGUYEN VAN A",
    "is_default": true
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "id": "PM_01HX98Z001",
    "owner_id": "USER_987654",
    "method_type": "DEBIT_CARD",
    "provider": "VISA",
    "masked_number": "**** **** **** 4242",
    "holder_name": "NGUYEN VAN A",
    "payment_token": "tok_mock_visa_4242_a1b2c3d4",
    "is_default": true,
    "status": "ACTIVE",
    "created_at": "2026-09-10T12:00:00Z"
  }
  ```

#### Danh sách phương thức thanh toán của người dùng
- **Endpoint**: `GET /api/v1/payment-methods?owner_id=USER_987654`
- **Response (200 OK)**:
  ```json
  [
    {
      "id": "PM_01HX98Z001",
      "method_type": "DEBIT_CARD",
      "provider": "VISA",
      "masked_number": "**** **** **** 4242",
      "holder_name": "NGUYEN VAN A",
      "is_default": true,
      "status": "ACTIVE"
    }
  ]
  ```

#### Hủy liên kết phương thức thanh toán
- **Endpoint**: `DELETE /api/v1/payment-methods/{id}`
- **Response (204 No Content)**

---

### 2.2 Tạo Ví Mới (Create Wallet)
- **Endpoint**: `POST /api/v1/wallets`
- **Mô tả**: Mở ví thanh toán mới cho người dùng (`currency` và `wallet_type` không bắt buộc, mặc định `USD` / `USER`).
- **Request Body**:
  ```json
  {
    "owner_id": "USER_987654",
    "currency": "USD",
    "wallet_type": "USER"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "id": "WAL_01HX98Z123",
    "owner_id": "USER_987654",
    "account_type": "USER",
    "account_status": "ACTIVE",
    "available_balance": 0,
    "ledger_balance": 0,
    "currency": "USD",
    "version": 1,
    "created_at": "2026-09-10T12:00:00Z"
  }
  ```

---

### 2.3 Tra Cứu Thông Tin & Số Dư Ví (Get Wallet)
- **Endpoint**: `GET /api/v1/wallets/{id}`
- **Mô tả**: Lấy thông tin ví và số dư khả dụng tức thời.
- **Response (200 OK)**:
  ```json
  {
    "id": "WAL_01HX98Z123",
    "owner_id": "USER_987654",
    "account_type": "USER",
    "account_status": "ACTIVE",
    "available_balance": 25000,
    "ledger_balance": 25000,
    "currency": "USD",
    "version": 4,
    "created_at": "2026-09-10T12:00:00Z",
    "updated_at": "2026-09-10T12:15:30Z"
  }
  ```
- **Response (404 Not Found)**: Nếu không tìm thấy Wallet ID.

---

### 2.4 Nạp Tiền Vào Ví Qua Thẻ (Top-up Money from Linked Card)
- **Endpoint**: `POST /api/v1/transfers/topup`
- **Headers**: `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` (không bắt buộc; dùng làm `reference_id`)
- **Mô tả**: Nạp tiền từ thẻ liên kết vào ví. Ghi bút toán sổ kép: DEBIT `SYS_EXTERNAL_SETTLEMENT` (`00000000-0000-0000-0000-000000000001`), CREDIT ví người dùng.
- **Request Body**:
  ```json
  {
    "wallet_id": "5f425555-2f70-4d56-bc5e-828049fe93c6",
    "payment_method_id": "c3f6a2b1-aaaa-bbbb-cccc-ddeeff001122",
    "amount": 50000,
    "description": "Nạp $500 từ thẻ Visa *4242"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z999",
    "reference_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "transaction_type": "TOPUP",
    "amount": 50000,
    "currency": "USD",
    "description": "Nạp $500 từ thẻ Visa *4242",
    "created_at": "2026-09-10T12:20:00Z"
  }
  ```

---

### 2.5 Rút Tiền Khỏi Ví Về Thẻ / Ngân Hàng (Withdraw Money to Linked Card)
- **Endpoint**: `POST /api/v1/transfers/withdraw`
- **Headers**: `Idempotency-Key: 1c2deb4d-4b7d-4bad-9bdd-3b0d7b3dcb7e` (không bắt buộc)
- **Mô tả**: Rút tiền từ ví về thẻ liên kết. Kiểm tra `available_balance >= amount` dưới `SELECT ... FOR UPDATE`. Ghi bút toán: DEBIT ví người dùng, CREDIT `SYS_EXTERNAL_SETTLEMENT`.
- **Request Body**:
  ```json
  {
    "wallet_id": "5f425555-2f70-4d56-bc5e-828049fe93c6",
    "payment_method_id": "c3f6a2b1-aaaa-bbbb-cccc-ddeeff001122",
    "amount": 20000,
    "description": "Rút $200 về thẻ Visa *4242"
  }
  ```
- **Response (201 Created)**: cùng cấu trúc `TransferResponse` với `"transaction_type": "WITHDRAW"`.
- **Response (400 Bad Request)**: Nếu số dư khả dụng không đủ (`insufficient-funds`); **403** nếu thẻ không thuộc về chủ ví.

---

### 2.6 Chuyển Tiền P2P Ví $\rightarrow$ Ví (Double-Entry Transfer)
- **Endpoint**: `POST /api/v1/transfers/p2p`
- **Headers**: `Idempotency-Key: 7a8deb4d-5b7d-4bad-9bdd-4b0d7b3dcb8f` (không bắt buộc)
- **Mô tả**: Chuyển tiền giữa 2 ví:
  - Khóa theo thứ tự `min(source, target) -> max(source, target)` chống deadlock.
  - Bọc Spring `@Retryable` (tầng `LedgerFacade`, ngoài ranh giới `@Transactional`) cho các lỗi khóa transient (`CannotAcquireLockException`, `PessimisticLockingFailureException`, `DeadlockLoserDataAccessException`, `ConcurrencyFailureException`).
  - Ghi 1 bản ghi `ledger_transactions` và 2 dòng `ledger_entries` (DEBIT nguồn `line_no=1`, CREDIT đích `line_no=2`, `UNIQUE(transaction_id, line_no)`) trong 1 database transaction duy nhất.
- **Request Body**:
  ```json
  {
    "source_wallet_id": "5f425555-2f70-4d56-bc5e-828049fe93c6",
    "destination_wallet_id": "7f425555-2f70-4d56-bc5e-828049fe93c7",
    "amount": 15000,
    "description": "Chuyển tiền ăn trưa"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z777",
    "reference_id": "7a8deb4d-5b7d-4bad-9bdd-4b0d7b3dcb8f",
    "transaction_type": "P2P_TRANSFER",
    "amount": 15000,
    "currency": "USD",
    "description": "Chuyển tiền ăn trưa",
    "created_at": "2026-09-10T12:30:00Z"
  }
  ```

---

### 2.7 Giao Dịch Hoàn Tiền (Reversal Transaction)
- **Endpoint**: `POST /api/v1/transfers/reversals`
- **Headers**: `Idempotency-Key: 3f4deb4d-6b7d-4bad-9bdd-5b0d7b3dcb90` (không bắt buộc)
- **Mô tả**: Tạo bút toán hoàn tiền append-only đảo ngược giao dịch gốc (debit ví đích gốc, credit lại ví nguồn gốc). Không sửa đổi dữ liệu cũ. Từ chối đảo ngược một giao dịch `REVERSAL`, và từ chối đảo ngược lần 2 (HTTP 409).
- **Request Body**:
  ```json
  {
    "original_transaction_id": "TX_01HX98Z777",
    "description": "Hoàn tiền do chuyển nhầm"
  }
  ```
- **Response (201 Created)**: cùng cấu trúc `TransferResponse` với `"transaction_type": "REVERSAL"`.

---

### 2.8 Tra Cứu Giao Dịch & Sao Kê Ví (Ledger Reads)
- **Chi tiết giao dịch**: `GET /api/v1/ledger/transactions/{id}`
- **Tra cứu theo reference**: `GET /api/v1/ledger/transactions/reference/{referenceId}`
- **Sao kê ví**: `GET /api/v1/ledger/wallets/{walletId}/entries` — danh sách `ledger_entries` mới nhất trước, mỗi dòng gồm `wallet_id`, `entry_type`, `amount`, `line_no`, `balance_after`, `created_at`.
- **Số dư tức thời O(1)**: `GET /api/v1/wallets/{id}` hoặc `GET /api/v1/wallets/{id}/balance`.

---

## 3. Ngoài Phạm Vi Hiện Tại (Chưa Hiện Thực)

- **gRPC nội bộ** (`ledger.proto`, port 9090): đã loại khỏi codebase để giữ service tối giản; nếu cần sẽ bổ sung lại ở phase sau.
- **Scoped Idempotency** `(actor_id, operation, key)` + lease: đã hiện thực ở Phase 3 (mục 1).
- **Transactional Outbox / Kafka**: phạm vi Phase 4.
- **Reconciliation job định kỳ**: phạm vi Phase 5.
