# Tài Liệu Phân Tích & Đặc Tả Nghiệp Vụ Ví Điện Tử

Tài liệu giải thích toàn diện và chuyên sâu về các quy tắc tài chính, luồng xử lý giao dịch, cơ chế an toàn số dư và mô hình sổ cái kế toán kép (Double-Entry Ledger) cho **Mini Digital Wallet (mini-digital-wallet)**.

---

## Mục Lục
1. [Tổng Quan Nghiệp Vụ](#1-tổng-quan-nghiệp-vụ)
2. [Mô Hình Trạng Thái Ví (Wallet State Machine)](#2-mô-hình-trạng-thái-ví-wallet-state-machine)
3. [Các Luồng Nghiệp Vụ Cốt Lõi (Core Business Flows)](#3-các-luồng-nghiệp-vụ-cốt-lõi-core-business-flows)
   - [3.1 Mở Ví Mới (Wallet Onboarding)](#31-mở-ví-mới-wallet-onboarding)
   - [3.2 Quản Lý Thẻ & Nguồn Tiền (Payment Methods & PCI-DSS Tokenization)](#32-quản-lý-thẻ--nguồn-tiền-payment-methods--pci-dss-tokenization)
   - [3.3 Nạp Tiền (Top-up)](#33-nạp-tiền-vào-ví-top-up-via-tokenized-card)
   - [3.4 Rút Tiền (Withdrawal)](#34-rút-tiền-khỏi-ví-về-thẻ-withdrawal-to-linked-card)
   - [3.5 Chuyển Tiền P2P Sổ Kép (Double-Entry P2P Transfer)](#35-chuyển-tiền-p2p-sổ-kép-double-entry-p2p-transfer)
   - [3.6 Giao Dịch Hoàn Tiền (Reversals)](#36-giao-dịch-hoàn-tiền-reversals)
   - [3.7 Sao Kê & Đối Soát (Statement & Reconciliation)](#37-sao-kê--đối-soát-statement--reconciliation)
4. [Các Quy Tắc Tài Chính Bắt Buộc (Financial Invariants & Constraints)](#4-các-quy-tắc-tài-chính-bắt-buộc-financial-invariants--constraints)
5. [Phân Tích Các Tình Huống Biên & Xử Lý Rủi Ro (Edge Cases & Safety)](#5-phân-tích-các-tình-huống-biên--xử-lý-rủi-ro-edge-cases--safety)

---

## 1. Tổng Quan Nghiệp Vụ

Trong các hệ thống ngân hàng và fintech, **Core Ledger** đóng vai trò là **Nguồn Chân Lý Tài Chính Duy Nhất (Single Source of Truth)**. 

### Các Khái Niệm Dữ Liệu Tách Biệt:
1. **Số Dư Tức Thời (Materialized Snapshot Projection - Bảng `wallets`)**:
   - `available_balance`: Số dư khả dụng để tiêu, phục vụ tra cứu nhanh $O(1)$ và kiểm tra `available_balance >= amount`.
   - `ledger_balance`: Số dư kế toán đã ghi nhận (hiện cập nhật song song với `available_balance` trong cùng transaction).
2. **Giao Dịch Gốc (Journal Header - Bảng `ledger_transactions`)**:
   - Lưu vết cấp cao của giao dịch: mã tham chiếu duy nhất (`reference_id`), loại giao dịch (`transaction_type`: `TOPUP`, `WITHDRAW`, `P2P_TRANSFER`, `REVERSAL`), số tiền, tiền tệ, mã giao dịch đảo ngược (`reversal_of_transaction_id`).
3. **Dòng Bút Toán Thành Phần (Immutable Lines - Bảng `ledger_entries`)**:
   - Lưu vết chi tiết từng biến động tiền tệ (ví nào, `DEBIT` hay `CREDIT`, bao nhiêu USD cents, dòng số mấy `line_no` với `UNIQUE(transaction_id, line_no)`).
   - Bất biến, append-only, tuyệt đối không được phép `UPDATE` hay `DELETE`.

> **Định lý Bất Biến Kiểm Toán & Quy Ước Số Dư Tự Nhiên (Natural Balance)**:
> - Với ví người dùng (`USER`), số dư tự nhiên là Credit (nghĩa vụ của ví với người dùng):
>   $$\text{Wallet.available\_balance} \equiv \sum \text{Amount}_{\text{CREDIT}} - \sum \text{Amount}_{\text{DEBIT}}$$
> - Bất biến cân bằng toàn hệ thống trên mọi giao dịch:
>   $$\sum \text{Amount}_{\text{DEBIT}} = \sum \text{Amount}_{\text{CREDIT}}$$

---

## 2. Mô Hình Trạng Thái Ví (Wallet State Machine)

Ví người dùng trong hệ thống chuyển dịch qua các trạng thái nghiêm ngặt:

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Mở ví mới (Balance = 0 USD)
    ACTIVE --> FROZEN: Phát hiện gian lận / Yêu cầu khóa
    FROZEN --> ACTIVE: Xác minh thành công / Mở khóa
    ACTIVE --> CLOSED: Tất toán số dư & Đóng ví (Balance = 0 USD)
    FROZEN --> CLOSED: Xử lý vi phạm / Cưỡng chế đóng
    CLOSED --> [*]
```

### Quyền Hạn Theo Trạng Thái:

| Nghiệp vụ | `ACTIVE` (Hoạt động) | `FROZEN` (Đóng băng) | `CLOSED` (Đã đóng) |
| :--- | :---: | :---: | :---: |
| **Xem số dư / Sao kê** | Cho phép | Cho phép | Cho phép |
| **Nạp tiền (Top-up)** | Cho phép | **Từ chối** | **Từ chối** |
| **Rút tiền (Withdraw)** | Cho phép | **Từ chối** | **Từ chối** |
| **Chuyển tiền đi (Transfer Out)** | Cho phép | **Từ chối** | **Từ chối** |
| **Nhận tiền chuyển đến (Transfer In)** | Cho phép | **Từ chối** | **Từ chối** |

> Lưu ý: code hiện tại từ chối mọi giao dịch ghi nợ/ghi có trên ví `FROZEN` hoặc `CLOSED` (kiểm tra trong `LedgerServiceImpl.validateWalletsTransferable`).

---

## 3. Các Luồng Nghiệp Vụ Cốt Lõi (Core Business Flows)

### 3.1 Mở Ví Mới (Wallet Onboarding)
- **Mục đích**: Cấp phát ví thanh toán mới cho người dùng.
- **Quy tắc**:
  - Số dư ban đầu luôn bằng `0 USD`.
  - Tiền tệ: `USD` (minor unit: cents).
  - Trạng thái khởi tạo: `ACTIVE`, loại ví: `USER`.
  - Ràng buộc cơ sở dữ liệu: `CHECK (account_type = 'SYSTEM' OR available_balance >= 0)`.

---

### 3.2 Quản Lý Thẻ & Nguồn Tiền (Payment Methods & PCI-DSS Tokenization)
- **Mục đích**: Cho phép người dùng liên kết thẻ tín dụng/ghi nợ (Visa, Mastercard, Napas) hoặc tài khoản ngân hàng để nạp/rút tiền.
- **Nguyên tắc PCI-DSS**:
  - Ví điện tử **tuyệt đối không lưu số thẻ thật (PAN)** hoặc mã bảo mật (CVV).
  - Khi người dùng nhập thẻ, Mock Payment Gateway xác thực và trả về `payment_token` duy nhất.
  - Hệ thống chỉ lưu `masked_number` (`**** **** **** 4242`), `holder_name`, `provider` và `payment_token`.
- **Vòng đời phương thức thanh toán**: `ACTIVE` $\rightarrow$ `REVOKED`. Người dùng có thể đặt 1 thẻ làm mặc định (`is_default = true`).

---

### 3.3 Nạp Tiền Vào Ví (Top-up via Tokenized Card)
- **Mục đích**: Ghi nhận dòng tiền từ thẻ liên kết vào ví người dùng.
- **Quy trình xử lý** (`POST /api/v1/transfers/topup`):
  1. Kiểm tra ví đích tồn tại.
  2. Kiểm tra thẻ tồn tại, ở trạng thái `ACTIVE` và thuộc quyền sở hữu của chủ ví (khác chủ $\rightarrow$ HTTP 403).
  3. Khóa ví đích và ví hệ thống `SYS_EXTERNAL_SETTLEMENT` theo thứ tự UUID `min -> max` bằng `SELECT ... FOR UPDATE`.
  4. Ghi 1 bản ghi cha `ledger_transactions` (type: `TOPUP`, currency: `USD`).
  5. Ghi 2 bút toán `ledger_entries` (`line_no` 1, 2):
     - DEBIT: `SYS_EXTERNAL_SETTLEMENT` (nguồn đối ứng).
     - CREDIT: Ví người dùng đích (tăng số dư ví).
  6. Cập nhật snapshot `available_balance` / `ledger_balance` ví người dùng trong cùng transaction rồi commit.
- **Chưa có**: gọi Mock Payment Gateway charge thật và `idempotency_records` (Phase 3).

---

### 3.4 Rút Tiền Khỏi Ví Về Thẻ (Withdrawal to Linked Card)
- **Mục đích**: Người dùng rút tiền từ ví về thẻ/tài khoản ngân hàng liên kết.
- **Quy trình xử lý** (`POST /api/v1/transfers/withdraw`):
  1. Kiểm tra ví nguồn tồn tại; kiểm tra thẻ hợp lệ và thuộc về user.
  2. Kiểm tra `amount > 0` và số dư khả dụng: `available_balance >= amount` (dưới lock).
  3. Khóa các ví theo thứ tự UUID `min -> max` bằng `SELECT ... FOR UPDATE`.
  4. Ghi bản ghi cha `ledger_transactions` (type: `WITHDRAW`, currency: `USD`).
  5. Ghi 2 bút toán `ledger_entries`:
     - DEBIT: Ví người dùng (giảm số dư ví).
     - CREDIT: `SYS_EXTERNAL_SETTLEMENT` (đối ứng).
  6. Cập nhật snapshot số dư rồi commit.

---

### 3.5 Chuyển Tiền P2P Sổ Kép (Double-Entry P2P Transfer)
- **Mục đích**: Di chuyển tiền giữa 2 ví nội bộ ($A \rightarrow B$), qua `POST /api/v1/transfers/p2p`.
- **Nguyên lý Kế toán Kép & Xử lý Đồng thời**:
  - Tách biệt Bean Proxy: Bean ngoài `LedgerFacade` đặt `@Retryable` cho các lỗi khóa transient (`CannotAcquireLockException`, `PessimisticLockingFailureException`, `DeadlockLoserDataAccessException`, `ConcurrencyFailureException`); Bean trong `LedgerServiceImpl` thực thi `@Transactional(READ_COMMITTED)`. Không dùng `@Recover` để lỗi nghiệp vụ (`InsufficientFunds`, trùng reference) luôn lan truyền nguyên dạng.
  - Khóa tuần tự: $\min(A, B) \rightarrow \max(A, B)$ bằng `SELECT ... FOR UPDATE`.
  - Ghi bản ghi cha `ledger_transactions` (`transaction_type='P2P_TRANSFER'`).
  - Ghi 2 bút toán con `ledger_entries` (`UNIQUE(transaction_id, line_no)`):
    - DEBIT ví nguồn $A$ (`line_no=1`).
    - CREDIT ví đích $B$ (`line_no=2`).
  - Cập nhật số dư snapshot cho cả 2 ví trong cùng một database transaction rồi commit.
- **Chưa có**: `idempotency_records` (Phase 3) và `outbox_events` (Phase 4).

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Facade as LedgerFacade (@Retryable)
    participant Service as LedgerService (@Transactional)
    participant DB as PostgreSQL

    Client->>Facade: POST /transfers/p2p (Idempotency-Key, A -> B, amount USD)
    Facade->>Service: executeTransfer(cmd)
    Note over Service: 1. Validate amount, self-transfer, referenceId
    Note over Service: 2. Sắp xếp thứ tự khóa: min(A,B) -> max(A,B)
    Service->>DB: Lock min(A, B) bằng SELECT ... FOR UPDATE
    Service->>DB: Lock max(A, B) bằng SELECT ... FOR UPDATE
    Note over Service: 3. Kiểm tra trạng thái ví + ví A có available_balance >= amount?
    Service->>DB: UPDATE A SET available_balance = available_balance - amount
    Service->>DB: UPDATE B SET available_balance = available_balance + amount
    Service->>DB: INSERT INTO ledger_transactions (id, reference_id, transaction_type)
    Service->>DB: INSERT INTO ledger_entries (tx, acc=A, DEBIT, amount, line_no=1)
    Service->>DB: INSERT INTO ledger_entries (tx, acc=B, CREDIT, amount, line_no=2)
    DB-->>Service: Commit Transaction (Atomicity)
    Service-->>Facade: Result
    Facade-->>Client: 201 Created (TransferResponse)
```

---

### 3.6 Giao Dịch Hoàn Tiền (Reversals)
- **Mục đích**: Hoàn trả tiền hoặc đảo bút toán giao dịch sai sót mà không vi phạm tính bất biến, qua `POST /api/v1/transfers/reversals` (`original_transaction_id`, `description`).
- **Quy tắc** (`LedgerServiceImpl.reverseTransfer`):
  - **Tuyệt đối không sửa hay xóa**: Không `UPDATE` hay `DELETE` giao dịch gốc hay dòng `ledger_entries` cũ (giao dịch đảo ghi thêm 1 `UPDATE` duy nhất để gắn `reversal_of_transaction_id` cho chính bản ghi reversal mới trong cùng transaction).
  - Tạo giao dịch mới `ledger_transactions` với `transaction_type = 'REVERSAL'` và `reversal_of_transaction_id = <original_tx_id>`.
  - Sinh 2 dòng `ledger_entries` đảo ngược hoàn toàn dòng tiền (debit ví đích gốc, credit ví nguồn gốc).
  - Từ chối đảo ngược một giao dịch `REVERSAL`, và từ chối đảo ngược lần 2 (HTTP 409).

---

### 3.7 Sao Kê & Đối Soát (Statement & Reconciliation)
- **Mục đích**: Cung cấp lịch sử giao dịch minh bạch cho người dùng và kiểm toán độc lập.
- **Quy tắc**:
  - Sao kê: `GET /api/v1/ledger/wallets/{walletId}/entries` — truy vấn từ `ledger_entries` theo `wallet_id`, sắp xếp `created_at DESC`.
  - Đối soát tự động (`ReconciliationJob`, kiểm tra $\text{available\_balance} = \sum \text{CREDIT} - \sum \text{DEBIT}$): **chưa hiện thực, phạm vi Phase 5**.

---

## 4. Các Quy Tắc Tài Chính Bắt Buộc (Financial Invariants & Constraints)

| # | Quy tắc bất biến | Cơ chế kỹ thuật đảm bảo | Hậu quả nếu vi phạm |
| :-: | :--- | :--- | :--- |
| **1** | **Không âm số dư ví người dùng** | Ràng buộc DB `CHECK (wallet_type = 'SYSTEM' OR available_balance >= 0)` trên bảng `wallets` | Thất thoát tài chính, nợ xấu |
| **2** | **Sổ cái Bất Biến (Append-Only)** | Bảng `ledger_entries` chỉ ghi thêm, hoàn tiền bằng Reversal (`UNIQUE(transaction_id, line_no)`) | Mất dấu vết kiểm toán |
| **3** | **Cân Bằng Sổ Kép (Zero-Sum)** | Bắt buộc $\sum \text{Debit} = \sum \text{Credit}$ trên mỗi transaction, có System Accounts | Tiền tự sinh ra hoặc tự biến mất |
| **4** | **Loại trừ Sai số Số thực (USD Only)** | Lưu số tiền kiểu `BIGINT` (minor units: cents), không hỗ trợ cross-currency | Sai số làm tròn tiền tệ |
| **5** | **Chống trùng giao dịch (tạm thời)** | `UNIQUE(reference_id)` trên `ledger_transactions` + `DataIntegrityViolation` $\rightarrow$ HTTP 409 | Trừ tiền 2 lần khi retry |

---

## 5. Phân Tích Các Tình Huống Biên & Xử Lý Rủi Ro (Edge Cases & Safety)

### 5.1 Xử Lý Tranh Chấp Tiền Tệ (Double-Spending)
- **Kịch bản**: Ví có 10,000 cents ($100). Bấm chuyển 10,000 cents ở 2 thiết bị cùng mili-giây.
- **Giải pháp**: Khóa bi quan `SELECT ... FOR UPDATE`. Giao dịch 1 trừ về 0; giao dịch 2 đọc số dư mới = 0 và bị từ chối với lỗi RFC 7807 `Insufficient Balance`.

### 5.2 Deadlock Khi Chuyển Tiền Ngược Chiều ($A \rightarrow B$ và $B \rightarrow A$)
- **Kịch bản**: 2 giao dịch đồng thời giữa A và B.
- **Giải pháp**:
  - Sắp xếp thứ tự khóa: $\min(A, B) \rightarrow \max(A, B)$ loại bỏ Circular Wait.
  - Tầng ngoài `LedgerFacade` với Spring `@Retryable` (tối đa 3 lần, backoff 100ms $\times$ 2) cho cả họ lỗi khóa transient (`CannotAcquireLockException`, `PessimisticLockingFailureException`, `DeadlockLoserDataAccessException`, `ConcurrencyFailureException`), mỗi lần retry chạy trên transaction mới sạch sẽ.
  - Không dùng `@Recover` để lỗi nghiệp vụ không bị bọc nhầm thành 503 (bài học từ test overdraft: `@Recover(Exception)` từng nuốt `InsufficientFundsException`).

### 5.3 Mạng Chập Chờn & Khách Hàng Bấm Gửi Nhiều Lần
- **Hiện tại (Phase 1–2)**: `reference_id` là `UNIQUE` ở DB. Client gửi kèm `Idempotency-Key` được dùng làm `reference_id`; request trùng $\rightarrow$ HTTP 409 Conflict (kể cả khi 2 thread đua nhau: luồng thua bị `UNIQUE` chặn và cũng nhận 409 thay vì 500).
- **Phase 3 (chưa làm)**: Bảng `idempotency_records` khóa `(actor_id, operation, idempotency_key)` + SHA-256 `request_hash` + `lease_expires_at` để tái dùng key an toàn và thu hồi request treo.

### 5.4 Sự Cố Broker Kafka & At-Least-Once Delivery
- **Chưa hiện thực, phạm vi Phase 4**: Transactional Outbox (`outbox_events` + poller `SKIP LOCKED`) và deduplicate phía consumer (`processed_events`). Codebase hiện tại đã xóa các shell trống của module này để giữ service tối giản; đặc tả đầy đủ xem `PLAN.md`.
