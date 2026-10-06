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
        varchar reference_id UK "Idempotency Reference Key"
        varchar transaction_type "TOP_UP, WITHDRAWAL, P2P_TRANSFER, REVERSAL"
        varchar status "PENDING, POSTED, FAILED, REVERSED"
        jsonb metadata "Audit notes, original_tx_id"
        timestamptz created_at "Created timestamp"
        timestamptz completed_at "Settled timestamp"
    }

    ledger_entries {
        uuid id PK "gen_random_uuid()"
        uuid transaction_id FK "References ledger_transactions.id"
        uuid wallet_id FK "References wallets.id"
        varchar entry_type "DEBIT or CREDIT"
        bigint amount "Positive minor units (cents)"
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
3. **Resilience Retry Layer (`@Retryable`)**: `LedgerFacade` tự động bắt `CannotAcquireLockException` hoặc `OptimisticLockingFailureException` để retry tối đa 3 lần với exponential backoff.

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
    participant LER as LedgerEntryRepository
    participant DB as PostgreSQL

    Client->>TC: POST /api/v1/transfers/p2p (sourceId, destId, amount, refId)
    TC->>TS: transfer(TransferRequest)
    TS->>LF: executeTransfer(refId, P2P_TRANSFER, amount, srcId, dstId)
    LF->>LS: recordTransaction(refId, P2P_TRANSFER, amount, srcId, dstId)

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
        LS->>LS: src.availableBalance -= amount
        LS->>LS: dst.availableBalance += amount
        LS->>WR: saveAll([src, dst])

        LS->>LTR: save(LedgerTransaction: POSTED)
        LTR->>DB: INSERT INTO ledger_transactions (...)

        LS->>LER: save(DEBIT entry, src, amount, src.balance)
        LER->>DB: INSERT INTO ledger_entries (...)
        LS->>LER: save(CREDIT entry, dst, amount, dst.balance)
        LER->>DB: INSERT INTO ledger_entries (...)

        DB-->>LS: Transaction committed
        LS-->>LF: LedgerTransactionResponse
        LF-->>TS: TransferResponse
        TS-->>TC: TransferResponse
        TC-->>Client: HTTP 200 OK (JSON)
    end
```

---

## 5. Luồng Nạp Tiền (Top-Up) & Rút Tiền (Withdrawal)

Giao dịch Nạp và Rút tiền tương tác trực tiếp với các Ví Hệ Thống (System Wallets) để bảo toàn nguyên lý kế toán kép:
- **Top-Up**: `DEBIT` Ví hệ thống `SYS_CASH_IN` $\rightarrow$ `CREDIT` Ví người dùng.
- **Withdrawal**: `DEBIT` Ví người dùng $\rightarrow$ `CREDIT` Ví hệ thống `SYS_CASH_OUT`.

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
    Client->>TC: POST /api/v1/transfers/topup (walletId, amount, paymentMethodId)
    TC->>TS: topUp(TopUpRequest)
    TS->>WR: findByUserIdAndCurrencyAndWalletType("SYSTEM_CASH_IN", currency, SYSTEM)
    WR-->>TS: systemCashInWallet
    TS->>LF: executeTransfer(ref, TOP_UP, amount, sysCashInId, userWalletId)
    LF->>LS: recordTransaction(ref, TOP_UP, amount, sysCashInId, userWalletId)
    LS-->>Client: HTTP 200 OK (DEBIT System Cash-In, CREDIT User Wallet)
    end

    rect rgb(255, 245, 245)
    Note over Client, WR: WITHDRAWAL FLOW
    Client->>TC: POST /api/v1/transfers/withdraw (walletId, amount, paymentMethodId)
    TC->>TS: withdraw(WithdrawRequest)
    TS->>WR: findByUserIdAndCurrencyAndWalletType("SYSTEM_CASH_OUT", currency, SYSTEM)
    WR-->>TS: systemCashOutWallet
    TS->>LF: executeTransfer(ref, WITHDRAWAL, amount, userWalletId, sysCashOutId)
    LF->>LS: recordTransaction(ref, WITHDRAWAL, amount, userWalletId, sysCashOutId)
    LS-->>Client: HTTP 200 OK (DEBIT User Wallet, CREDIT System Cash-Out)
    end
```

---

## 6. Luồng Đảo Ngược Giao Dịch (Reversal Sequence)

Giao dịch trong sổ cái ngân hàng **không bao giờ bị DELETE hoặc UPDATE trực tiếp**. Để sửa sai hoặc hủy thanh toán, hệ thống ghi thêm giao dịch đảo ngược (`REVERSAL`) nhằm triệt tiêu hiệu ứng tài chính:

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin / Audit System
    participant LC as LedgerController
    participant LS as LedgerServiceImpl
    participant LTR as LedgerTransactionRepository
    participant LER as LedgerEntryRepository
    participant WR as WalletRepository

    Admin->>LC: POST /api/v1/ledger/reversal (originalTxId, reason)
    LC->>LS: reverseTransaction(originalTxId, reason)
    LS->>LTR: findById(originalTxId)
    LTR-->>LS: originalTx (Must be POSTED)
    LS->>LER: findByTransactionId(originalTxId)
    LER-->>LS: [originalDebitEntry, originalCreditEntry]

    Note over LS: Hoán đổi vai trò: Source thành Dest, Dest thành Source
    LS->>WR: Lock srcWallet & dstWallet theo Deterministic Order
    Note over LS: Check: Destination wallet có đủ số dư để hoàn lại không?
    LS->>WR: Deduct destWallet & Credit back srcWallet

    LS->>LTR: save(ReversalTransaction: REVERSED_ORIGINAL)
    LS->>LER: save(DEBIT on original destination, CREDIT on original source)
    LS->>LTR: update originalTx.status = REVERSED

    LS-->>LC: LedgerTransactionResponse (REVERSAL completed)
    LC-->>Admin: HTTP 200 OK
```

---

## 7. Máy Trạng Thái Giao Dịch Sổ Cái (Ledger Transaction Lifecycle)

```mermaid
stateDiagram-v2
    [*] --> PENDING : Initiated by Client
    PENDING --> POSTED : Both entries verified & posted
    PENDING --> FAILED : Insufficient funds / Account frozen
    POSTED --> REVERSED : Reversal requested & compensated
    FAILED --> [*] : Terminal state
    REVERSED --> [*] : Terminal state
```

- **PENDING**: Giao dịch đã tiếp nhận, đang tiến hành lấy lock trên 2 ví.
- **POSTED**: Cả hai bút toán DEBIT và CREDIT đã ghi thành công, số dư khả dụng và số dư sổ cái được cập nhật nguyên tử (Atomic).
- **FAILED**: Giao dịch bị hủy do thiếu số dư hoặc vi phạm nghiệp vụ; không bản ghi bút toán nào được lưu.
- **REVERSED**: Giao dịch gốc đã được triệt tiêu hoàn toàn bởi một cặp bút toán đảo ngược tương ứng.
