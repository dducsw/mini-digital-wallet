# Mini Digital Wallet — API Specification

Tài liệu đặc tả toàn bộ REST API giao dịch tiền tệ cho **Mini Digital Wallet (mini-digital-wallet)**.

---

## 1. Quy ước Chung (General Conventions)

- **Base URL**: `http://localhost:8080/api/v1`
- **Content-Type**: `application/json`
- **Đơn vị Tiền tệ**: **USD** (minor unit = cents, $1.00 = 100 cents, số nguyên `Long`).
  - Ví dụ: `$50.00` $\rightarrow$ `5000`, `$1,000.00` $\rightarrow$ `100000`.
- **Idempotency Header**: Các API ghi nhận tiền (`topup`, `withdraw`, `transfers`, `reversal`) yêu cầu:
  - Header `Idempotency-Key`: Chuỗi định danh duy nhất do client sinh (UUIDv4).
  - Hệ thống lưu trữ theo scope: `(actor_id, operation, idempotency_key)` và hash SHA-256 của request body.
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
    "payment_token": "tok_mock_visa_4242",
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
- **Mô tả**: Mở ví thanh toán mới cho người dùng.
- **Request Body**:
  ```json
  {
    "owner_id": "USER_987654",
    "currency": "USD"
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
- **Endpoint**: `POST /api/v1/wallets/{id}/topup`
- **Headers**: `Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d`
- **Mô tả**: Nạp tiền từ thẻ liên kết vào ví. Gọi Mock Payment Gateway charge token $\rightarrow$ Ghi bút toán sổ kép: DEBIT `SYS_EXTERNAL_SETTLEMENT`, CREDIT ví người dùng.
- **Request Body**:
  ```json
  {
    "reference_id": "TOPUP_20260910_001",
    "payment_method_id": "PM_01HX98Z001",
    "amount": 50000,
    "description": "Nạp $50 từ thẻ Visa *4242"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z999",
    "reference_id": "TOPUP_20260910_001",
    "wallet_id": "WAL_01HX98Z123",
    "payment_method_id": "PM_01HX98Z001",
    "type": "DEPOSIT",
    "amount": 50000,
    "currency": "USD",
    "new_available_balance": 75000,
    "created_at": "2026-09-10T12:20:00Z"
  }
  ```

---

### 2.5 Rút Tiền Khỏi Ví Về Thẻ / Ngân Hàng (Withdraw Money to Linked Card)
- **Endpoint**: `POST /api/v1/wallets/{id}/withdraw`
- **Headers**: `Idempotency-Key: 1c2deb4d-4b7d-4bad-9bdd-3b0d7b3dcb7e`
- **Mô tả**: Rút tiền từ ví về thẻ liên kết. Kiểm tra `available_balance >= amount` bằng `SELECT FOR UPDATE`. Ghi bút toán: DEBIT ví người dùng, CREDIT `SYS_EXTERNAL_SETTLEMENT` và kích hoạt Mock Gateway payout.
- **Request Body**:
  ```json
  {
    "reference_id": "WIT_20260910_002",
    "payment_method_id": "PM_01HX98Z001",
    "amount": 20000,
    "description": "Rút $20 về thẻ Visa *4242"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z888",
    "reference_id": "WIT_20260910_002",
    "wallet_id": "WAL_01HX98Z123",
    "payment_method_id": "PM_01HX98Z001",
    "type": "WITHDRAWAL",
    "amount": 20000,
    "currency": "USD",
    "new_available_balance": 55000,
    "created_at": "2026-09-10T12:25:00Z"
  }
  ```
- **Response (400 Bad Request)**: Nếu số dư khả dụng không đủ.

---

### 2.5 Chuyển Tiền P2P Ví $\rightarrow$ Ví (Double-Entry Transfer)
- **Endpoint**: `POST /api/v1/transfers`
- **Headers**: `Idempotency-Key: 7a8deb4d-5b7d-4bad-9bdd-4b0d7b3dcb8f`
- **Mô tả**: Chuyển tiền giữa 2 ví:
  - Khóa theo thứ tự `min(source, target) -> max(source, target)` chống deadlock.
  - Bọc Spring `@Retryable` ngoài ranh giới transaction xử lý lỗi `40P01`.
  - Ghi bản ghi cha `ledger_transactions` và 2 dòng `ledger_entries` (DEBIT nguồn, CREDIT đích) trong 1 database transaction duy nhất.
- **Request Body**:
  ```json
  {
    "reference_id": "P2P_20260910_777",
    "source_wallet_id": "WAL_01HX98Z123",
    "target_wallet_id": "WAL_01HX99B456",
    "amount": 15000,
    "description": "Chuyển tiền ăn trưa"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z777",
    "reference_id": "P2P_20260910_777",
    "source_wallet_id": "WAL_01HX98Z123",
    "target_wallet_id": "WAL_01HX99B456",
    "amount": 15000,
    "currency": "USD",
    "status": "POSTED",
    "executed_at": "2026-09-10T12:30:00Z"
  }
  ```

---

### 2.6 Giao Dịch Hoàn Tiền (Reversal Transaction)
- **Endpoint**: `POST /api/v1/transfers/{transaction_id}/reversal`
- **Headers**: `Idempotency-Key: 3f4deb4d-6b7d-4bad-9bdd-5b0d7b3dcb90`
- **Mô tả**: Tạo bút toán hoàn tiền append-only đảo ngược giao dịch gốc. Không sửa đổi dữ liệu cũ trong quá khứ.
- **Request Body**:
  ```json
  {
    "reference_id": "REV_20260910_001",
    "reason": "Hoàn tiền do chuyển nhầm"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "transaction_id": "TX_01HX98Z666",
    "reference_id": "REV_20260910_001",
    "reversal_of_transaction_id": "TX_01HX98Z777",
    "type": "REVERSAL",
    "status": "POSTED",
    "executed_at": "2026-09-10T12:35:00Z"
  }
  ```

---

### 2.7 Sao Kê & Lịch Sử Giao Dịch (Wallet Statement)
- **Endpoint**: `GET /api/v1/wallets/{id}/entries?page=0&size=20`
- **Mô tả**: Tra cứu lịch sử các dòng bút toán sổ cái (`ledger_entries`) của ví theo thứ tự thời gian mới nhất.
- **Response (200 OK)**:
  ```json
  {
    "wallet_id": "WAL_01HX98Z123",
    "page": 0,
    "size": 20,
    "total_elements": 3,
    "items": [
      {
        "id": "ENT_003",
        "transaction_id": "TX_01HX98Z777",
        "entry_type": "DEBIT",
        "amount": 15000,
        "line_no": 1,
        "created_at": "2026-09-10T12:30:00Z"
      },
      {
        "id": "ENT_002",
        "transaction_id": "TX_01HX98Z888",
        "entry_type": "DEBIT",
        "amount": 20000,
        "line_no": 1,
        "created_at": "2026-09-10T12:25:00Z"
      },
      {
        "id": "ENT_001",
        "transaction_id": "TX_01HX98Z999",
        "entry_type": "CREDIT",
        "amount": 50000,
        "line_no": 2,
        "created_at": "2026-09-10T12:20:00Z"
      }
    ]
  }
  ```

---

## 3. Đặc Tả gRPC Contract (`ledger.proto`)

Cổng gRPC nội bộ phục vụ các service khác (Payment Gateway, Fraud Service) gọi đồng bộ với độ trễ < 5ms:

```protobuf
syntax = "proto3";

package com.minidigitalwallet.v1;

option java_multiple_files = true;
option java_package = "com.minidigitalwallet.v1";
option java_outer_classname = "LedgerProto";

service WalletGrpcService {
  // Tra cứu ví tức thời
  rpc GetWallet(GetWalletRequest) returns (WalletResponse);

  // Nạp tiền vào ví
  rpc Topup(TopupRequest) returns (TransactionResponse);

  // Rút tiền khỏi ví
  rpc Withdraw(WithdrawRequest) returns (TransactionResponse);

  // Chuyển khoản Double-Entry P2P
  rpc Transfer(TransferRequest) returns (TransactionResponse);
}

message GetWalletRequest {
  string wallet_id = 1;
}

message WalletResponse {
  string id = 1;
  string owner_id = 2;
  int64 available_balance = 3;
  int64 ledger_balance = 4;
  string currency = 5;
  string account_status = 6;
  int32 version = 7;
  int64 created_at = 8;
  int64 updated_at = 9;
}

message TopupRequest {
  string reference_id = 1;
  string wallet_id = 2;
  int64 amount = 3;
  string description = 4;
  string payment_method_id = 5;
}

message WithdrawRequest {
  string reference_id = 1;
  string wallet_id = 2;
  int64 amount = 3;
  string description = 4;
  string payment_method_id = 5;
}

message TransferRequest {
  string reference_id = 1;
  string source_wallet_id = 2;
  string target_wallet_id = 3;
  int64 amount = 4;
  string currency = 5;
  string description = 6;
}

message TransactionResponse {
  bool success = 1;
  string message = 2;
  string transaction_id = 3;
  string reference_id = 4;
  int64 executed_at = 5;
}
```
