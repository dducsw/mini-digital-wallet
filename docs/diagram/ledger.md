# Kiến Trúc & Luồng Tương Tác Core Double-Entry Ledger (Phase 2)

Tài liệu thiết kế chi tiết hệ thống sổ cái kế toán kép (Double-Entry Bookkeeping), cơ chế khóa chống deadlock và luồng giao dịch tiền tệ an toàn trong ngân hàng số.

---

## 1. Nguyên Lý Kế Toán Kép & Bất Biến Số Dư (Invariants)

Mọi giao dịch biến động số dư tài chính bắt buộc tuân thủ nguyên lý kế toán kép:
- **Không bao giờ tạo hoặc phá hủy tiền từ hư vô**: Tiền chỉ chuyển dịch giữa các tài khoản/ví.
- **Bất biến cân bằng (Balance Invariant)**: Tổng số tiền Ghi nợ (DEBIT) luôn bằng tổng số tiền Ghi có (CREDIT) trên từng mã giao dịch.
  $$\sum \text{Amount}_{\text{DEBIT}} = \sum \text{Amount}_{\text{CREDIT}}$$
- **Bất biến không âm (Non-negative Invariant)**: Ví người dùng (`USER`) không bao giờ được âm số dư khả dụng (`available_balance >= 0`). Ví hệ thống (`SYSTEM`) được phép âm tạm thời để phản ánh nguồn cấp vốn (Clearing/Settlement).

```mermaid
flowchart LR
    subgraph Transaction["Giao dịch Kế toán kép (Atomic Transaction)"]
        direction TB
        subgraph SourceAccount["Tài khoản Nguồn (Source Wallet)"]
            D["DEBIT (Ghi nợ) - Cents"]
            D_BAL["Giảm Số dư (- Amount)"]
        end

        subgraph DestAccount["Tài khoản Đích (Destination Wallet)"]
            C["CREDIT (Ghi có) - Cents"]
            C_BAL["Tăng Số dư (+ Amount)"]
        end

        D ---|Equal Amount| C
    end
```

---

## 2. Mô Hình Dữ Liệu Sổ Cái (Ledger ERD)

```mermaid
erDiagram
    wallets ||--o{ ledger_entries : "has balance movements (1:N)"
    ledger_transactions ||--|{ ledger_entries : "contains entries (1:N)"

    wallets {
        uuid id PK "gen_random_uuid()"
        varchar user_id "Owner ID"
        varchar wallet_type "USER or SYSTEM"
        varchar currency "ISO 4217 (USD)"
        bigint available_balance "Spendable cents (>= 0)"
        bigint ledger_balance "Settled ledger cents"
        varchar status "ACTIVE, FROZEN, CLOSED"
        bigint version "Optimistic lock version"
        timestamptz created_at "Created timestamp"
        timestamptz updated_at "Updated timestamp"
    }

    ledger_transactions {
        uuid id PK "gen_random_uuid()"
        varchar reference_id UK "UNIQUE, chống trùng giao dịch"
        varchar transaction_type "TOPUP, WITHDRAW, P2P_TRANSFER, REVERSAL"
        bigint amount "Positive minor units (cents)"
        varchar currency "USD"
        varchar description "Ghi chú"
        uuid reversal_of_transaction_id FK "Trỏ về tx gốc nếu là REVERSAL"
        timestamptz created_at "Created timestamp"
    }

    ledger_entries {
        uuid id PK "gen_random_uuid()"
        uuid transaction_id FK "References ledger_transactions.id"
        uuid wallet_id FK "References wallets.id"
        varchar entry_type "DEBIT or CREDIT"
        bigint amount "Positive minor units (cents)"
        int line_no "1 = DEBIT nguồn, 2 = CREDIT đích; UNIQUE(transaction_id, line_no)"
        bigint balance_after "Snapshot balance post-entry"
        timestamptz created_at "Created timestamp"
    }
```

---

## 3. Kiến Trúc Concurrency & Cơ Chế Khóa Chống Deadlock

Khi hai người dùng chuyển tiền chéo nhau đồng thời:
- Luồng 1: Ví A chuyển sang Ví B.
- Luồng 2: Ví B chuyển sang Ví A.

Nếu khóa theo thứ tự request (`A -> B` và `B -> A`), cơ sở dữ liệu sẽ gặp Deadlock (`ORA-00060 / PG Deadlock detected`).

### Giải pháp kỹ thuật:
1. **Deterministic Lock Ordering**: Luôn sắp xếp thứ tự khóa bi-directional theo thứ tự tự nhiên của khóa chính UUID (`UUID.compareTo`):
   $$\text{First Lock} = \min(\text{srcId}, \text{dstId}), \quad \text{Second Lock} = \max(\text{srcId}, \text{dstId})$$
2. **Pessimistic Lock (`PESSIMISTIC_WRITE`)**: Sử dụng lệnh SQL `SELECT ... FOR UPDATE` đảm bảo độc quyền ghi.
3. **Resilience Retry Layer (`@Retryable`)**: `LedgerFacade` tự động retry tối đa 3 lần (backoff 100ms $\times$ 2, mỗi lần là transaction mới) cho cả họ lỗi khóa transient: `CannotAcquireLockException`, `PessimisticLockingFailureException`, `DeadlockLoserDataAccessException`, `ConcurrencyFailureException`. Cố tình **không dùng `@Recover`** để lỗi nghiệp vụ (`InsufficientFunds`, trùng `reference_id`) luôn lan truyền nguyên dạng thay vì bị bọc nhầm thành 503.

```mermaid
flowchart TD
    REQ["Transfer Request (Src, Dst, Amount)"] --> FACADE["LedgerFacade (@Retryable)"]
    FACADE --> ORDER{"Compare UUIDs (srcId, dstId)"}
    ORDER -->|srcId &lt; dstId| LOCK_AB["Lock srcId FIRST -> Lock dstId SECOND"]
    ORDER -->|srcId &gt; dstId| LOCK_BA["Lock dstId FIRST -> Lock srcId SECOND"]
    LOCK_AB --> EXEC["Execute Debit & Credit Entries"]
    LOCK_BA --> EXEC
    EXEC --> COMMIT["Commit DB Transaction"]
```

---

## 4. Luồng Chuyển Tiền P2P Transfer (P2P Sequence)

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant TC as TransferController
    participant TS as TransferServiceImpl
    participant LF as LedgerFacade
    participant LS as LedgerServiceImpl
    participant WR as WalletRepository
    participant LTR as LedgerTransactionRepository
    participant DB as PostgreSQL

    Client->>TC: POST /api/v1/transfers/p2p (sourceId, destId, amount, refId)
    TC->>TS: transfer(TransferRequest, idempotencyKey)
    TS->>LF: executeTransferWithRetry(refId, P2P_TRANSFER, amount, srcId, dstId)
    LF->>LS: executeTransfer(refId, P2P_TRANSFER, amount, srcId, dstId)

    Note over LS: Deterministic Ordering: min(src, dst) -> max(src, dst)
    LS->>WR: findByIdForUpdate(firstId)
    WR->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>WR: Lock acquired (firstWallet)

    LS->>WR: findByIdForUpdate(secondId)
    WR->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>WR: Lock acquired (secondWallet)

    Note over LS: Validation: status == ACTIVE && src.availableBalance >= amount
    alt Insufficient Funds
        LS-->>LF: throw InsufficientFundsException (400)
        LF-->>TC: Bubbled up
        TC-->>Client: HTTP 400 ProblemDetail (insufficient-funds)
    else Balance Valid
        LS->>LS: src.debit(amount) / dst.credit(amount) + save cả 2 ví
        LS->>LTR: save(LedgerTransaction + 2 entries qua cascade)
        LTR->>DB: INSERT INTO ledger_transactions (...)
        LTR->>DB: INSERT INTO ledger_entries (DEBIT src line_no=1, CREDIT dst line_no=2)

        DB-->>LS: Transaction committed
        LS-->>LF: LedgerTransaction
        LF-->>TS: LedgerTransaction
        TS-->>TC: TransferResponse
        TC-->>Client: HTTP 201 Created (JSON)
    end
```

---

## 5. Luồng Nạp Tiền (Top-Up) & Rút Tiền (Withdrawal)

Giao dịch Nạp và Rút tiền dùng chung ví hệ thống đối ứng cố định `SYS_EXTERNAL_SETTLEMENT` (`00000000-0000-0000-0000-000000000001`) để bảo toàn nguyên lý kế toán kép:
- **Top-Up**: `DEBIT` ví `SYS_EXTERNAL_SETTLEMENT` $\rightarrow$ `CREDIT` ví người dùng.
- **Withdrawal**: `DEBIT` ví người dùng $\rightarrow$ `CREDIT` ví `SYS_EXTERNAL_SETTLEMENT`.

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant TC as TransferController
    participant TS as TransferServiceImpl
    participant LF as LedgerFacade
    participant LS as LedgerServiceImpl
    participant WR as WalletRepository

    rect rgb(240, 248, 255)
    Note over Client, WR: TOP-UP FLOW
    Client->>TC: POST /api/v1/transfers/topup (walletId, paymentMethodId, amount)
    TC->>TS: topUp(TopUpRequest, idempotencyKey)
    TS->>TS: ownedCard(wallet, paymentMethodId) — check ACTIVE + chính chủ
    TS->>LF: executeTransferWithRetry(ref, TOPUP, SYS_SETTLEMENT -> userWallet)
    LF->>LS: executeTransfer(...) trong @Transactional
    LS-->>TC: HTTP 201 Created (TransferResponse)
    end

    rect rgb(255, 245, 245)
    Note over Client, WR: WITHDRAWAL FLOW
    Client->>TC: POST /api/v1/transfers/withdraw (walletId, paymentMethodId, amount)
    TC->>TS: withdraw(WithdrawRequest, idempotencyKey)
    TS->>TS: ownedCard(wallet, paymentMethodId) — check ACTIVE + chính chủ
    TS->>LF: executeTransferWithRetry(ref, WITHDRAW, userWallet -> SYS_SETTLEMENT)
    LF->>LS: executeTransfer(...) trong @Transactional
    LS-->>TC: HTTP 201 Created (TransferResponse)
    end
```

---

## 6. Luồng Đảo Ngược Giao Dịch (Reversal Sequence)

Giao dịch trong sổ cái ngân hàng **không bao giờ bị DELETE hoặc UPDATE trực tiếp**. Để sửa sai hoặc hủy thanh toán, hệ thống ghi thêm giao dịch đảo ngược (`REVERSAL`) nhằm triệt tiêu hiệu ứng tài chính:

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant TC as TransferController
    participant TS as TransferServiceImpl
    participant LF as LedgerFacade (@Retryable)
    participant LS as LedgerServiceImpl (@Transactional)
    participant LTR as LedgerTransactionRepository

    Client->>TC: POST /api/v1/transfers/reversals (originalTxId, reason)
    TC->>TS: reverse(originalTxId, idempotencyKey, description)
    TS->>LF: reverseWithRetry(originalTxId, reversalRef, description)
    LF->>LS: reverseTransfer(originalTxId, reversalRef, description)
    LS->>LTR: findById(originalTxId)
    LTR-->>LS: originalTx (không phải REVERSAL, chưa từng bị đảo)
    Note over LS: Đọc ví nguồn/đích gốc từ 2 entries, đảo chiều dòng tiền
    LS->>LS: executeTransfer(reversalRef, originalDest -> originalSource, REVERSAL)
    Note over LS: Gắn reversal.reversal_of_transaction_id = originalTxId, save
    LS-->>TC: HTTP 201 Created (TransferResponse, REVERSAL)
```

---

## 7. Vòng Đời Giao Dịch Sổ Cái (Ledger Transaction Lifecycle)

Bảng `ledger_transactions` hiện **không có cột trạng thái** — mỗi bản ghi đã commit là cuối cùng (bất biến). Vòng đời thực tế:

```mermaid
stateDiagram-v2
    [*] --> POSTED : executeTransfer commit (2 entries cân bằng)
    POSTED --> REVERSED : reverseTransfer commit tx REVERSAL mới trỏ về tx gốc
    REVERSED --> [*] : Terminal (không được đảo tiếp, không đảo REVERSAL)
    POSTED --> [*] : Terminal (không đảo)
```

- **POSTED** (ngầm định): đã commit là xong; không có `PENDING`/`FAILED` lưu DB — request lỗi chỉ ném exception, không để lại bản ghi.
- **REVERSED**: trạng thái logic của tx gốc sau khi đã tồn tại một tx `REVERSAL` trỏ về nó (chặn đảo lần 2 bằng `existsByReversalOfTransactionId`).

---

## 8. Vì Sao `@Retryable` Nằm Ở `LedgerFacade`, Không Nằm Chung `LedgerService`

Câu hỏi thường gặp: tại sao không gắn `@Retryable` trực tiếp lên `LedgerService.executeTransfer` cho gọn?

**Trả lời ngắn**: vì cả hai annotation đều là proxy bọc ngoài method, và transaction chết không retry được.

```mermaid
flowchart TD
    subgraph WRONG["SAI: chung 1 bean"]
        direction TB
        R1["@Retryable + @Transactional<br/>cùng method"] --> D1["Lỗi lock 40P01:<br/>transaction bị đánh dấu rollback-only"]
        D1 --> R2["Retry chạy lại trên ĐÚNG<br/>transaction đã chết"]
        R2 --> F1["TransactionRollbackException:<br/>retry bao nhiêu lần cũng thất bại"]
    end
    subgraph RIGHT["ĐÚNG: tách 2 bean (code hiện tại)"]
        direction TB
        FACADE["LedgerFacade<br/>(chỉ @Retryable, không transaction)"]
        SVC["LedgerService<br/>(@Transactional READ_COMMITTED)"]
        FACADE -->|"lần 1"| SVC
        SVC -.->|"lỗi lock → transaction chết bị vứt"| FACADE
        FACADE -->|"retry: mở transaction MỚI sạch"| SVC
        SVC --> OK["Commit thành công"]
    end
```

Chi tiết:

1. Khi DB ném lỗi deadlock, `TransactionInterceptor` đánh dấu transaction hiện tại là **rollback-only** — transaction này đã chết, chỉ còn chờ rollback.
2. Nếu `@Retryable` bọc cùng method đó, lần retry sẽ thực thi lại **trên đúng transaction chết này** thay vì mở cái mới → nhận `TransactionRollbackException` ngay lập tức.
3. Tách `LedgerFacade` (chỉ retry, không transaction) gọi sang `LedgerService` (chỉ transaction, không retry): mỗi lần retry đi qua proxy transaction từ đầu nên luôn được cấp connection + snapshot mới sạch sẽ.

**Quy tắc nhớ**: `@Retryable` phải bọc **ngoài** `@Transactional`, trên **bean khác nhau**. (Muốn gộp chung bean thì phải tự mở `REQUIRES_NEW` thủ công — phức tạp và dễ sai hơn nhiều.)

> Liên quan: cũng vì lý do "không nuốt lỗi", Facade cố tình **không dùng `@Recover`** — từng có `@Recover(Exception)` bọc mọi lỗi (kể cả `InsufficientFundsException` ngay lần đầu) thành 503, làm test overdraft đỏ. Xem mục 3 và 5.2.
