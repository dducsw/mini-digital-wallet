# Kiến Trúc Tổng Quan Hệ Thống Mini Digital Wallet (Phase 2)

Tài liệu mô tả kiến trúc tổng thể, mô hình phân tầng Layered Architecture và luồng điều phối giao dịch tài chính cốt lõi của **mini-digital-wallet** tại mốc Phase 2.

---

## 1. Mô Hình Kiến Trúc Phân Tầng (Layered Architecture)

Hệ thống được tổ chức theo kiến trúc phân tầng (Package-by-Layer) nghiêm ngặt nhằm tách biệt ranh giới trách nhiệm, ranh giới AOP Retry và ranh giới ACID Transaction:

```mermaid
flowchart TD
    subgraph ClientLayer["Tầng Client & External Systems"]
        SPA["Web Dashboard / Mobile App"]
        PGW["Payment Gateway (Mock Tokenization)"]
    end

    subgraph PresentationLayer["Tầng Presentation (REST Controllers)"]
        AUTH_CTRL["AuthController (/api/v1/auth)"]
        WALLET_CTRL["WalletController (/api/v1/wallets)"]
        CARD_CTRL["PaymentMethodController (/api/v1/payment-methods)"]
        TX_CTRL["TransferController (/api/v1/transfers)"]
        EX_HANDLER["GlobalExceptionHandler (RFC 7807 ProblemDetail)"]
    end

    subgraph ResilienceLayer["Tầng Điều Phối & Khả Năng Phục Hồi (Resilience Facade)"]
        FACADE["LedgerFacade (@Retryable)"]
        RETRY_RULE["Retry Policy: 3 attempts, backoff 100ms x 2<br/>Catches: PessimisticLockingFailureException,<br/>CannotAcquireLockException, ConcurrencyFailureException"]
        FACADE --- RETRY_RULE
    end

    subgraph DomainServiceLayer["Tầng Nghiệp Vụ Cốt Lõi (Core Services)"]
        AUTH_SVC["AuthService / JwtTokenProvider"]
        WALLET_SVC["WalletService"]
        CARD_SVC["PaymentMethodService"]
        LEDGER_SVC["LedgerService (@Transactional READ_COMMITTED)"]
        ORDER_LOCK["Deterministic Lock Ordering: min(A,B) -> max(A,B)"]
        LEDGER_SVC --- ORDER_LOCK
    end

    subgraph DataAccessLayer["Tầng Lưu Trữ & Ràng Buộc Dữ Liệu (Spring Data JPA & DB)"]
        REPOS["WalletRepository, LedgerTransactionRepository, LedgerEntryRepository"]
        PG["PostgreSQL 16 Engine"]
        PG_CONSTRAINTS["Database Constraints:<br/>- chk_wallet_available_balance (available_balance >= 0)<br/>- uq_ledger_transactions_ref (reference_id UNIQUE)<br/>- uq_ledger_entries_tx_line (transaction_id, line_no UNIQUE)"]
        PG --- PG_CONSTRAINTS
    end

    SPA --> PresentationLayer
    PGW --> CARD_CTRL
    PresentationLayer --> ResilienceLayer
    PresentationLayer --> DomainServiceLayer
    ResilienceLayer --> LEDGER_SVC
    DomainServiceLayer --> DataAccessLayer
    DataAccessLayer --> PG
```

---

## 2. Luồng Xử Lý Giao Dịch Chuyển Tiền Cốt Lõi (Phase 2 Transaction Flow)

Biểu đồ tuần tự chi tiết luồng xử lý giao dịch chuyển tiền hai ví (P2P Transfer) với cơ chế bọc ngoài `@Retryable`, khóa thứ tự chống Deadlock và ghi nhận bút toán kép:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as TransferController
    participant Facade as LedgerFacade (@Retryable)
    participant Service as LedgerService (@Transactional)
    participant Repo as WalletRepository
    participant DB as PostgreSQL 16

    Client->>Controller: POST /api/v1/transfers/p2p
    Controller->>Facade: executeTransferWithRetry(ref, src, dst, amount, type, desc)
    Facade->>Service: executeTransfer(ref, src, dst, amount, type, desc)

    Note over Service: 1. Validate request (positive amount, src != dst)<br/>2. Check reference_id idempotency fast-fail
    Note over Service: 3. Deterministic lock ordering:<br/>firstId = min(srcId, dstId)<br/>secondId = max(srcId, dstId)

    Service->>Repo: findByIdForUpdate(firstId)
    Repo->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>Repo: Row Lock 1 Acquired

    Service->>Repo: findByIdForUpdate(secondId)
    Repo->>DB: SELECT * FROM wallets WHERE id = ? FOR UPDATE
    DB-->>Repo: Row Lock 2 Acquired

    Note over Service: 4. Check wallet states (ACTIVE, not FROZEN/CLOSED)<br/>5. Check currency match<br/>6. Debit source wallet (available_balance >= amount)<br/>7. Credit destination wallet

    Service->>DB: UPDATE wallets SET available_balance = ... WHERE id = srcId
    Service->>DB: UPDATE wallets SET available_balance = ... WHERE id = dstId

    Note over Service: 8. Build balanced Journal Header & 2 Lines:<br/>Line 1: DEBIT source (line_no = 1)<br/>Line 2: CREDIT destination (line_no = 2)

    Service->>DB: INSERT INTO ledger_transactions (Header)
    Service->>DB: INSERT INTO ledger_entries (Line 1 & Line 2)

    DB-->>Service: Commit ACID Transaction & Release Locks
    Service-->>Facade: LedgerTransaction
    Facade-->>Controller: LedgerTransaction
    Controller-->>Client: 201 Created (TransferResponse)
```

---

## 3. Các Thành Phần Trách Nhiệm & Bất Biến Kỹ Thuật

### 3.1 Phân định ranh giới Facade vs Transaction

- **`LedgerFacade`**: Mang annotation `@Retryable`. Chịu trách nhiệm thực hiện thử lại (tối đa 3 lần) khi cơ sở dữ liệu xảy ra xung đột khóa hoặc tranh chấp tài nguyên tạm thời. Mỗi lần thử lại kích hoạt một giao dịch ACID mới hoàn toàn.
- **`LedgerService`**: Mang annotation `@Transactional(isolation = Isolation.READ_COMMITTED)`. Tuyệt đối không chứa logic retry bên trong để tránh retry trên Session Hibernate đã bị bẩn (dirty) hoặc bị đánh dấu rollback.

### 3.2 Giải pháp phá vỡ Deadlock (Deterministic Lock Ordering)

Khi hai luồng thực thi giao dịch chuyển tiền ngược chiều cùng thời điểm ($A \rightarrow B$ và $B \rightarrow A$):
- Nếu lấy khóa theo thứ tự tham số truyền vào: Luồng 1 giữ lock $A$ chờ $B$; Luồng 2 giữ lock $B$ chờ $A$ $\rightarrow$ Sinh chu trình chờ (Circular Wait) gây Deadlock.
- **Giải pháp**: Luôn so sánh UUID theo thứ tự từ điển:
  $$\text{First Lock} = \min(A, B), \quad \text{Second Lock} = \max(A, B)$$
  Cả hai luồng bắt buộc phải xin khóa ví nhỏ hơn trước, loại bỏ hoàn toàn chu trình chờ trong đồ thị cấp phát tài nguyên.

### 3.3 Tính toàn vẹn sổ kép (Double-Entry Bookkeeping)

- Tiền tệ được định lượng bằng đơn vị nguyên nhỏ nhất (`Long` cents).
- Mỗi giao dịch biến động số dư phải thỏa mãn phương trình kế toán:
  $$\sum \text{Amount}_{\text{DEBIT}} = \sum \text{Amount}_{\text{CREDIT}}$$
- Bảng `ledger_entries` đánh số dòng `line_no` (1 cho DEBIT, 2 cho CREDIT) kèm ràng buộc `UNIQUE(transaction_id, line_no)` để bảo đảm thứ tự và tính toàn vẹn của bút toán.
- Hoàn tiền (`reversal`) tuân thủ tính chất bất biến (Append-Only): Không cập nhật hoặc xóa bút toán cũ; tạo giao dịch mới kiểu `REVERSAL` đảo ngược dòng tiền và liên kết qua `reversal_of_transaction_id`.

---

## 4. Bảng Tổng Hợp Lý Thuyết & Hành Động Kỹ Thuật (Theory Summary)

> **[Action]**: Tách ranh giới `@Retryable` trên `LedgerFacade` và `@Transactional` trên `LedgerService`.  
> **[Theory Recall]**: Spring AOP Proxy Chain & Transaction Boundary Lifecycle. Retry phải bọc ngoài Transaction để mở session mới khi gặp lỗi rollback.  
> **[Design Pattern]**: Facade Pattern + Retry Pattern.  
> **[Catastrophe Prevented]**: Tránh ngoại lệ `TransactionSystemException` do thực thi trên session Hibernate đã rollback.  
> **[Interview TL;DR]**: "Tách Facade mang `@Retryable` ra ngoài Service mang `@Transactional` bảo đảm mỗi lần thử lại chạy trong một transaction độc lập."

> **[Action]**: Sắp xếp thứ tự khóa tài khoản `min(A, B) -> max(A, B)` trước khi thực hiện `SELECT ... FOR UPDATE`.  
> **[Theory Recall]**: Phá vỡ điều kiện Circular Wait trong 4 điều kiện sinh Deadlock của Coffman (Mutual Exclusion, Hold & Wait, No Preemption, Circular Wait).  
> **[Design Pattern]**: Deterministic Resource Allocation / Ordered Lock Acquisition.  
> **[Catastrophe Prevented]**: Triệt tiêu hoàn toàn database deadlock khi 2 tài khoản chuyển tiền đối xứng song song.  
> **[Interview TL;DR]**: "Sắp xếp khóa theo thứ tự đơn điệu chuyển đồ thị chờ tài nguyên thành đồ thị có hướng không chu trình (DAG)."
