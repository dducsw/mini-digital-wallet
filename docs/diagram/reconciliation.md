# Kiến Trúc & Luồng Đối Soát Sổ Cái (Phase 4 Reconciliation)

Tài liệu thiết kế job đối soát định kỳ: chứng minh mọi snapshot số dư khớp với nhật ký bút toán, toàn sổ zero-sum, và vết audit cho kiểm toán.

---

## 1. Nguyên Lý Đối Soát (Reconciliation Invariants)

Mỗi lần chạy kiểm tra 2 bất biến trên dữ liệu thật (PostgreSQL), không tin snapshot:

1. **Khớp từng ví**: `wallets.available_balance == sum(CREDIT) - sum(DEBIT)` (cộng từ `ledger_entries` theo `wallet_id`).
2. **Zero-sum toàn sổ**: `sum(DEBIT) == sum(CREDIT)` trên mọi entries.

```mermaid
flowchart TD
    subgraph Run["Một lần chạy đối soát (ReconciliationService.runOnce)"]
        direction TB
        DRIFT["1 truy vấn set-based: GROUP BY ví + HAVING<br/>chỉ trả về các ví lệch"]
        GLOBAL["1 truy vấn: SUM(DEBIT) - SUM(CREDIT) toàn sổ (zero-sum)"]
        COUNT["COUNT(*) wallets (số ví đã kiểm)"]
        SAVE["INSERT reconciliation_runs (counts only)"]
        METRICS["counter reconciliation.runs<br/>counter reconciliation.mismatched.wallets"]
        DRIFT --> GLOBAL --> COUNT --> SAVE --> METRICS
    end
```

---

## 2. Mô Hình Dữ Liệu Audit (ERD)

```mermaid
erDiagram
    reconciliation_runs {
        uuid id PK "gen_random_uuid()"
        timestamptz started_at "Bắt đầu chạy"
        timestamptz finished_at "Kết thúc chạy"
        bigint wallets_checked "Số ví đã kiểm"
        bigint mismatched_count "Số ví lệch (counts only)"
        boolean globally_balanced "Toàn sổ zero-sum?"
        varchar status "OK, MISMATCH, ERROR"
        timestamptz created_at "Created timestamp"
    }
```

> Bảng chỉ lưu **số lượng**, tuyệt đối không lưu số dư từng ví. Chi tiết ví lệch ghi server log (`WARN`), API chỉ trả counts — tránh lộ số dư người khác. Hai endpoint admin yêu cầu `ROLE_ADMIN`.

---

## 3. Luồng Chạy Định Kỳ & Chạy Tay (Sequence)

```mermaid
sequenceDiagram
    autonumber
    actor Scheduler as Spring Scheduler (2h đêm)
    actor Ops as Ops / Developer
    participant SVC as ReconciliationService
    participant WR as WalletRepository
    participant LER as LedgerEntryRepository
    participant RR as ReconciliationRunRepository
    participant DB as PostgreSQL

    rect rgb(240, 248, 255)
    Note over Scheduler, DB: CHẠY TỰ ĐỘNG
    Scheduler->>SVC: runScheduled() (cron app.reconciliation.cron)
    SVC->>SVC: runOnce()
    end

    rect rgb(240, 255, 240)
    Note over Ops, DB: CHẠY TAY / XEM LỊCH SỬ
    Ops->>SVC: POST /api/v1/admin/reconciliation/run
    SVC->>SVC: runOnce()
    SVC-->>Ops: 200 OK (ReconciliationSummary, counts only)
    Ops->>SVC: GET /api/v1/admin/reconciliation/runs
    SVC-->>Ops: 200 OK (50 lần gần nhất)
    end

    Note over SVC: runOnce() — 1 transaction duy nhất
    SVC->>WR: findDriftedWallets()
    WR->>DB: SELECT w.id, w.available_balance, SUM(CASE...) FROM wallets w LEFT JOIN ledger_entries e ... GROUP BY ... HAVING ...
    DB-->>WR: chỉ các ví lệch (derived != stored)
    Note over SVC: drift != empty => WARN log từng ví + mismatchedCount = size
    SVC->>LER: sumNetAmount()
    LER->>DB: SELECT SUM(CASE WHEN DEBIT THEN amount ELSE -amount END) FROM ledger_entries
    DB-->>LER: net
    Note over SVC: net != 0 => WARN zero-sum + globallyBalanced = false
    SVC->>WR: count()
    WR->>DB: SELECT COUNT(*) FROM wallets
    SVC->>RR: save(ReconciliationRun OK/MISMATCH)
    RR->>DB: INSERT INTO reconciliation_runs (...)
    DB-->>SVC: Commit
```

---

## 4. Vòng Đời & Luồng Phân Nhánh Đối Soát (Run Lifecycle)

```mermaid
flowchart TD
    START(["Khởi động đối soát (runOnce)"]) --> CHECK_DRIFT["1. Quét tìm ví lệch số dư (findDriftedWallets)"]
    CHECK_DRIFT --> CHECK_ZERO["2. Kiểm tra cân bằng Zero-Sum toàn sổ (sumNetAmount)"]
    CHECK_ZERO --> EVAL{"Đánh giá 2 bất biến"}

    EVAL -->|0 ví lệch & Toàn sổ cân bằng| STATUS_OK["Trạng thái: OK"]
    EVAL -->|Có ví lệch hoặc lệch Zero-Sum| STATUS_MISMATCH["Trạng thái: MISMATCH"]

    STATUS_OK --> SAVE_OK["Lưu reconciliation_runs (status = OK)"]
    STATUS_MISMATCH --> LOG_WARN["Ghi server log WARN chi tiết từng ví lệch"]
    LOG_WARN --> SAVE_MISMATCH["Lưu reconciliation_runs (status = MISMATCH)"]

    SAVE_OK --> END_OK(["Hoàn tất: Hệ thống toàn vẹn"])
    SAVE_MISMATCH --> END_ALERT(["Báo động: Điều tra nguyên nhân gốc"])
```

- **OK**: Hệ thống hoàn toàn toàn vẹn, không có ví nào bị lệch số dư, toàn sổ cân bằng Zero-Sum.
- **MISMATCH**: Kiểm toán phát hiện lệch tiền. Đọc server log `Reconciliation mismatch: wallet=... derived=... stored=... diff=...` để điều tra nguyên nhân gốc (tuyệt đối không sửa số dư bằng tay để khớp số).

---

## 5. Bảng Tổng Hợp Hành Động & Lý Thuyết (Action & Theory Summary)

> **[Action]**: Đối soát `available_balance == sum(CREDIT) - sum(DEBIT)` từ journal thay vì tin snapshot, chạy đêm + lưu `reconciliation_runs`.
> **[Theory Recall]**: Audit Trail & Eventual Consistency Verification — snapshot là projection có thể drift, journal append-only mới là sự thật.  
> **[Design Pattern]**: Scheduled Audit Job (không can thiệp luồng ghi, chỉ đọc).  
> **[Catastrophe Prevented]**: Phát hiện sớm bug làm lệch tiền (ghi snapshot sai, update tay) trước khi thành thất thoát.  
> **[Interview TL;DR]**: "Không bao giờ tin số dư cache; đêm nào cũng cộng lại toàn bộ sổ từ dòng bút toán bất biến, lệch là báo động."
