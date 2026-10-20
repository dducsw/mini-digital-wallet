# Kiến Trúc & Luồng Giao Dịch Core Double-Entry Ledger

Tài liệu thiết kế chi tiết về hệ thống Sổ Cái Kế Toán Kép (Double-Entry Bookkeeping), cơ chế khóa bi quan chống Deadlock và cách bảo vệ tính toàn vẹn dòng tiền trong **Mini Digital Wallet**.

---

## 1. Nguyên Lý Kế Toán Kép (Double-Entry Bookkeeping)

Giả sử bạn chuyển 15 USD cho bạn của bạn:
- Ở thế giới thực, tờ tiền 15 USD rời khỏi tay bạn và chuyển sang tay người bạn. Tổng số tiền trên thế giới không hề thay đổi.
- Trong hệ thống ngân hàng, để mô phỏng sự thật đó, mọi giao dịch biến động số dư đều phải tuân thủ **Nguyên Lý Kế Toán Kép**:
  1. **Tiền không bao giờ tự sinh ra hay tự biến mất**: Nó luôn luôn đi từ một nơi (ví A) đến một nơi khác (ví B).
  2. **Bất biến cân bằng (Balance Invariant)**: Tổng số tiền Ghi nợ (`DEBIT`) luôn luôn bằng tổng số tiền Ghi có (`CREDIT`) trên từng giao dịch:
     $$\sum \text{Amount}_{\text{DEBIT}} = \sum \text{Amount}_{\text{CREDIT}}$$
  3. **Bất biến không âm (Non-negative Invariant)**: Ví của khách hàng (`USER`) không bao giờ được phép mang số dư âm (`available_balance >= 0`). Ví hệ thống (`SYSTEM`) được phép mang số dư âm vì nó đóng vai trò đại diện cho nguồn vốn đối ứng bên ngoài.

```mermaid
flowchart LR
    subgraph Transaction["Giao Dịch Kế Toán Kép (Atomic Transaction)"]
        direction TB
        subgraph SourceAccount["Tài Khoản Nguồn (Ví A)"]
            D["DEBIT (Ghi Nợ) - Cents"]
            D_BAL["Số dư giảm: - Amount"]
        end

        subgraph DestAccount["Tài Khoản Đích (Ví B)"]
            C["CREDIT (Ghi Có) - Cents"]
            C_BAL["Số dư tăng: + Amount"]
        end

        D ---|Số Tiền Bằng Nhau Tuyệt Đối| C
    end
```

---

## 2. Mô Hình Dữ Liệu Sổ Cái (Ledger ERD)

```mermaid
erDiagram
    wallets ||--o{ ledger_entries : "chứa các biến động số dư (1:N)"
    ledger_transactions ||--|{ ledger_entries : "chứa các bút toán con (1:N)"

    wallets {
        uuid id PK "Khóa chính UUIDv4"
        varchar user_id "ID chủ ví"
        varchar account_number UK "STK 12 chữ số duy nhất, đầu 8820"
        varchar wallet_type "USER hoặc SYSTEM"
        varchar currency "ISO 4217 (USD)"
        bigint available_balance "Số dư khả dụng (cents, >= 0)"
        bigint ledger_balance "Số dư kế toán đã chốt"
        varchar status "ACTIVE, FROZEN, CLOSED"
        bigint version "Khóa lạc quan OCC"
        timestamptz created_at "Thời điểm tạo"
        timestamptz updated_at "Cập nhật cuối"
    }

    ledger_transactions {
        uuid id PK "Khóa chính UUIDv4"
        varchar reference_id UK "UNIQUE, chống trùng giao dịch"
        varchar transaction_type "TOPUP, WITHDRAW, P2P_TRANSFER, REVERSAL"
        bigint amount "Số tiền nguyên (cents > 0)"
        varchar currency "USD"
        varchar description "Nội dung chuyển tiền"
        uuid reversal_of_transaction_id FK "Trỏ về tx gốc nếu là REVERSAL"
        timestamptz created_at "Thời điểm tạo"
    }

    ledger_entries {
        uuid id PK "Khóa chính UUIDv4"
        uuid transaction_id FK "Khóa ngoại trỏ về ledger_transactions.id"
        uuid wallet_id FK "Khóa ngoại trỏ về wallets.id"
        varchar entry_type "DEBIT hoặc CREDIT"
        bigint amount "Số tiền nguyên (cents > 0)"
        int line_no "1 = DEBIT nguồn, 2 = CREDIT đích; UNIQUE(transaction_id, line_no)"
        bigint balance_after "Số dư tức thời ngay sau khi ghi dòng này"
        timestamptz created_at "Thời điểm tạo"
    }
```

---

## 3. Cơ Chế Khóa Bi Quan & Triệt Tiêu Deadlock

### Vấn đề Deadlock là gì?
Khi hai người bạn A và B cùng lúc chuyển tiền cho nhau trên 2 điện thoại khác nhau:
- Luồng 1 (từ ví A sang ví B): Cần khóa ví A trước, rồi khóa ví B sau.
- Luồng 2 (từ ví B sang ví A): Cần khóa ví B trước, rồi khóa ví A sau.

Nếu cả 2 luồng chạy cùng một phần nghìn giây: Luồng 1 giữ A và đợi B; Luồng 2 giữ B và đợi A. Cả hai bên chờ nhau mãi mãi gây **Deadlock** treo cứng cơ sở dữ liệu!

### Giải pháp trong hệ thống:
1. **Sắp xếp thứ tự khóa nhất quán (Deterministic Lock Ordering)**:
   - Không bao giờ khóa theo thứ tự người gửi/người nhận.
   - So sánh chuỗi UUID của 2 ví theo bảng chữ cái:
     `Khóa Thứ Nhất = min(UUID_A, UUID_B), Khóa Thứ Hai = max(UUID_A, UUID_B)`
   - Dù là A chuyển cho B hay B chuyển cho A, cả hai luồng đều bắt buộc phải xin khóa ví có UUID nhỏ hơn trước. Điều kiện chờ đợi vòng tròn bị phá vỡ hoàn toàn!
2. **Khóa độc quyền ghi (Pessimistic Lock)**:
   - Sử dụng câu lệnh SQL: `SELECT ... FOR UPDATE` thông qua `@Lock(LockModeType.PESSIMISTIC_WRITE)` của JPA.
3. **Lớp phục hồi tự động (`@Retryable`)**:
   - `LedgerFacade` tự động thử lại tối đa 3 lần nếu có tranh chấp lock tạm thời giữa các luồng, mỗi lần thử lại chạy trên một transaction sạch sẽ.

```mermaid
flowchart TD
    REQ["Yêu cầu chuyển tiền: Ví A <-> Ví B"] --> FACADE["LedgerFacade (@Retryable)"]
    FACADE --> ORDER{"So sánh UUID(A) và UUID(B)"}
    ORDER -->|UUID(A) &lt; UUID(B)| LOCK_AB["Khóa Ví A trước -> Khóa Ví B sau"]
    ORDER -->|UUID(B) &lt; UUID(A)| LOCK_BA["Khóa Ví B trước -> Khóa Ví A sau"]
    LOCK_AB --> EXEC["Ghi bút toán Debit & Credit"]
    LOCK_BA --> EXEC
    EXEC --> COMMIT["Commit Transaction & Nhả Khóa"]
```

---

## 4. Sequence Diagram: Luồng Chuyển Tiền P2P

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Client
    participant TC as TransferController
    participant LF as LedgerFacade (@Retryable)
    participant LS as LedgerServiceImpl (@Transactional)
    participant WR as WalletRepository
    participant LTR as LedgerTransactionRepository
    participant DB as PostgreSQL

    KhachHang->>TC: POST /api/v1/transfers/p2p (Src, Dst, $15, PIN)
    TC->>LF: executeTransferWithRetry(...)
    LF->>LS: executeTransfer(...)

    Note over LS: 1. Sắp xếp thứ tự khóa: min(src, dst) -> max(src, dst)
    LS->>WR: findByIdForUpdate(firstId)
    WR->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>WR: Đã khóa ví thứ nhất

    LS->>WR: findByIdForUpdate(secondId)
    WR->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>WR: Đã khóa ví thứ hai

    Note over LS: 2. Kiểm tra trạng thái ví ACTIVE và ví nguồn đủ tiền
    alt Không đủ tiền (Insufficient Funds)
        LS-->>TC: throw InsufficientFundsException (HTTP 400)
        TC-->>KhachHang: HTTP 400 ProblemDetail
    else Đủ tiền hợp lệ
        Note over LS: 3. Trừ tiền ví nguồn, cộng tiền ví đích
        LS->>DB: UPDATE wallets SET available_balance = balance - 1500 WHERE id = srcId
        LS->>DB: UPDATE wallets SET available_balance = balance + 1500 WHERE id = dstId

        Note over LS: 4. Ghi 1 dòng Transaction Header và 2 dòng Entries
        LS->>LTR: save(Transaction + Entries)
        LTR->>DB: INSERT INTO ledger_transactions (id, type=P2P_TRANSFER, amount=1500)
        LTR->>DB: INSERT INTO ledger_entries (DEBIT srcId, line_no=1, amount=1500)
        LTR->>DB: INSERT INTO ledger_entries (CREDIT dstId, line_no=2, amount=1500)

        DB-->>LS: Transaction Committed (ACID)
        LS-->>LF: Transfer thành công
        LF-->>TC: TransferResponse DTO
        TC-->>KhachHang: HTTP 201 Created
    end
```

---

## 5. Sequence Diagram: Luồng Nạp Tiền (Top-Up) & Rút Tiền (Withdrawal)

Giao dịch Nạp và Rút tiền đều sử dụng ví hệ thống `SYS_EXTERNAL_SETTLEMENT` (`00000000-0000-0000-0000-000000000001`) làm tài khoản đối ứng:

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Client
    participant TC as TransferController
    participant LS as LedgerServiceImpl (@Transactional)
    participant DB as PostgreSQL

    rect rgb(240, 248, 255)
    Note over KhachHang, DB: LUỒNG NẠP TIỀN (TOP-UP)
    KhachHang->>TC: POST /transfers/topup ($500 từ thẻ Visa)
    TC->>LS: executeTransfer(SYS_EXTERNAL_SETTLEMENT -> ViKhachHang)
    LS->>DB: INSERT ledger_entries (DEBIT SYS_SETTLEMENT, $500, line_no=1)
    LS->>DB: INSERT ledger_entries (CREDIT ViKhachHang, $500, line_no=2)
    LS->>DB: UPDATE wallets SET balance = balance + $500
    DB-->>TC: Commit OK
    TC-->>KhachHang: HTTP 201 Created (Số dư tăng lên)
    end

    rect rgb(255, 245, 245)
    Note over KhachHang, DB: LUỒNG RÚT TIỀN (WITHDRAWAL)
    KhachHang->>TC: POST /transfers/withdraw ($200 về thẻ Visa)
    TC->>LS: executeTransfer(ViKhachHang -> SYS_EXTERNAL_SETTLEMENT)
    LS->>DB: INSERT ledger_entries (DEBIT ViKhachHang, $200, line_no=1)
    LS->>DB: INSERT ledger_entries (CREDIT SYS_SETTLEMENT, $200, line_no=2)
    LS->>DB: UPDATE wallets SET balance = balance - $200
    DB-->>TC: Commit OK
    TC-->>KhachHang: HTTP 201 Created (Số dư giảm đi)
    end
```

---

## 6. Sequence Diagram: Luồng Hoàn Tiền (Reversal)

Giao dịch cũ hoàn toàn giữ nguyên tính bất biến. Hệ thống tạo một giao dịch mới trỏ về giao dịch cũ và đảo ngược bút toán:

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Client
    participant TC as TransferController
    participant LS as LedgerServiceImpl (@Transactional)
    participant DB as PostgreSQL

    KhachHang->>TC: POST /transfers/reversals (originalTxId: TX123)
    TC->>LS: reverseTransfer(TX123)
    Note over LS: 1. Đọc giao dịch gốc và kiểm tra quyền sở hữu<br/>2. Kiểm tra chưa từng bị hoàn tiền trước đây
    Note over LS: 3. Đảo ngược dòng tiền: DEBIT người nhận cũ, CREDIT người gửi cũ
    LS->>DB: INSERT INTO ledger_transactions (type=REVERSAL, reversal_of=TX123)
    LS->>DB: INSERT INTO ledger_entries (DEBIT ViNhanCu, line_no=1)
    LS->>DB: INSERT INTO ledger_entries (CREDIT ViGuiCu, line_no=2)
    DB-->>TC: Commit thành công (Chỉ mục UNIQUE ngăn chặn hoàn tiền 2 lần)
    TC-->>KhachHang: HTTP 201 Created (Hoàn tiền hoàn tất)
```

---

## 7. Vì Sao `@Retryable` Phải Tách Riêng Khỏi `@Transactional`?

Câu hỏi kinh điển dành cho fresher: *"Tại sao không viết cả `@Retryable` và `@Transactional` lên cùng một hàm trong `LedgerServiceImpl`?"*.

- **Bản chất Spring Proxy**: Khi một transaction trong Spring gặp lỗi Deadlock ở tầng DB, Spring sẽ đánh dấu transaction đó là **Rollback-Only** (đã hỏng, không thể commit nữa).
- Nếu `@Retryable` nằm chung trên cùng một bean: Lần thử lại (retry) sẽ chạy tiếp trên **chính transaction đã bị đánh dấu hỏng đó** $\rightarrow$ Nhận ngay ngoại lệ `TransactionRollbackException` và retry hoàn toàn vô ích.
- Khi tách ra 2 Bean riêng:
  - `LedgerFacade` nằm ngoài, mang `@Retryable`.
  - `LedgerServiceImpl` nằm trong, mang `@Transactional`.
  - Khi `LedgerServiceImpl` báo lỗi deadlock, transaction cũ bị rollback và hủy bỏ. `LedgerFacade` bắt được lỗi và gọi lại hàm, Spring sẽ mở một transaction hoàn toàn mới sạch sẽ.
