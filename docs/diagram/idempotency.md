# Kiến Trúc & Cơ Chế Chống Trùng Lặp 2 Tầng (Idempotency Service)

Tài liệu thiết kế chi tiết cơ chế bảo đảm tính lũy thừa (**Two-Tier Idempotency**) và an toàn đa luồng trong các giao dịch tài chính tại **Mini Digital Wallet**.

---

## 1. Vấn Đề Thực Tế: Tại Sao Phải Cần Idempotency?

### Tình huống kinh điển ngoài đời thực
1. Khách hàng bấm nút *"Nạp 500 USD"* hoặc *"Chuyển 15 USD"*.
2. Điện thoại của khách hàng đột ngột mất sóng 4G/Wifi hoặc mạng bị chập chờn.
3. Ứng dụng điện thoại quay vòng tròn và không nhận được phản hồi (HTTP Timeout).
4. Khách hàng sốt ruột và bấm nút gửi lần thứ hai.

**Nếu hệ thống không có Idempotency**:
- Request thứ nhất thực ra đã đến máy chủ và trừ tiền thành công.
- Request thứ hai đến tiếp tục trừ tiền thêm một lần nữa. Khách hàng bị mất tiền oan và khiếu nại!

### Nguyên tắc Lũy Thừa (Idempotency)
Một thao tác được gọi là lũy thừa nếu việc thực thi nó một lần hay nhiều lần liên tiếp đều mang lại **cùng một kết quả duy nhất** và **không làm thay đổi trạng thái hệ thống nhiều hơn một lần**.
$$\forall N \ge 1: \quad f(f(...f(x))) \equiv f(x)$$

---

## 2. Kiến Trúc Idempotency 2 Tầng (Two-Tier Idempotency)

Hệ thống triển khai cơ chế phòng thủ 2 lớp nghiêm ngặt:

```mermaid
flowchart TD
    Client["Client Gửi Request (Kèm Header: Idempotency-Key)"] --> L1{"TẦNG 1: Ứng Dụng (idempotency_records)"}

    subgraph Tier1["Tầng Ứng Dụng: IdempotencyService"]
        L1 -->|Chưa từng thấy Key| ACQ["Tạo record IN_PROGRESS (Khóa Lease 30s)"]
        L1 -->|Trùng Key + Khác Payload| MISMATCH["Từ chối ngay: HTTP 422 Unprocessable"]
        L1 -->|Trùng Key + Đang IN_PROGRESS| CONFLICT["Đang xử lý: HTTP 409 Conflict"]
        L1 -->|Trùng Key + Đã COMPLETED| REPLAY["Trả lại ngay kết quả JSON cũ (Không trừ tiền thêm)"]
        L1 -->|Trùng Key + Lease Hết Hạn| RECLAIM["Worker cũ bị crash -> Thu hồi lease & Xử lý lại"]
    end

    ACQ --> L2{"TẦNG 2: Cơ Sở Dữ Liệu"}

    subgraph Tier2["Tầng Database: PostgreSQL Constraint"]
        L2 --> UNIQUE["Ràng buộc UNIQUE(reference_id) trên bảng ledger_transactions"]
        UNIQUE -->|Hai luồng cùng đua nhau qua tầng 1| WIN["Luồng 1 ghi thành công"]
        UNIQUE -->|Luồng 2 bị đụng khóa vật lý| LOSE["Luồng 2 bị DataIntegrityViolation -> Bắt lỗi trả về HTTP 409"]
    end
```

---

## 3. Mô Hình Dữ Liệu & Vòng Đời Trạng Thái (ERD & State Machine)

### Mô hình bảng `idempotency_records`

```mermaid
erDiagram
    idempotency_records {
        uuid id PK "Khóa chính UUIDv4"
        varchar actor_id "ID người thực hiện (users.id)"
        varchar operation "Tên nghiệp vụ (TOPUP, WITHDRAW, P2P)"
        varchar idempotency_key "Khóa do client tự sinh (UUID)"
        varchar request_hash "Mã băm SHA-256 của toàn bộ payload"
        varchar status "IN_PROGRESS, COMPLETED, FAILED"
        int status_code "Mã HTTP Status lưu lại (201, 400...)"
        text response_body "Toàn bộ chuỗi JSON response để replay"
        timestamptz lease_expires_at "Thời hạn giữ khóa (now + 30s)"
        timestamptz created_at "Thời điểm tạo"
        timestamptz updated_at "Cập nhật cuối"
    }
```

### Máy trạng thái (State Machine) của một Idempotency Record

```mermaid
flowchart TD
    INIT(["Khách hàng gửi yêu cầu"]) --> IN_PROG["Trạng thái IN_PROGRESS (Khóa lease 30s)"]
    IN_PROG -->|Commit thành công| COMP["Trạng thái COMPLETED (Lưu JSON kết quả)"]
    IN_PROG -->|Lỗi nghiệp vụ| FAIL["Trạng thái FAILED (Lưu status code lỗi)"]
    IN_PROG -->|Hết hạn 30s do server sập| RECLAIM["Tự động thu hồi lease để chạy lại"]
    RECLAIM --> IN_PROG
    FAIL -->|Client gửi thử lại| IN_PROG
    COMP --> REPLAY(["Replay JSON cũ cho mọi lần gọi lặp lại"])
```

---

## 4. Sequence Diagram: Luồng Xử Lý & Replay Kết Quả

Biểu đồ mô tả chi tiết trường hợp mạng lag, client gọi lại đúng key và hệ thống trả lại kết quả cũ mà không thực hiện giao dịch sổ cái thêm lần nào:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as TransferController
    participant IdemService as IdempotencyServiceImpl
    participant Storage as IdempotencyStorage
    participant Ledger as LedgerService
    participant DB as PostgreSQL

    Note over Client, DB: LẦN GỌI ĐẦU TIÊN (MẠNG CHẬP CHỜN)
    Client->>Controller: POST /transfers/p2p (Header: Idempotency-Key: KEY_123, Payload: A->B $15)
    Controller->>IdemService: execute(actorId, "P2P", "KEY_123", payload, action)
    IdemService->>IdemService: Tính mã băm SHA-256 của payload -> HASH_XYZ
    IdemService->>Storage: acquireOrInspect(actorId, "P2P", "KEY_123", HASH_XYZ)
    Storage->>DB: INSERT INTO idempotency_records (status=IN_PROGRESS, lease=30s)
    DB-->>Storage: Record created -> Decision = ACQUIRED
    Storage-->>IdemService: Decision = ACQUIRED

    IdemService->>Ledger: action.get() -> executeTransfer(...)
    Ledger->>DB: Ghi sổ cái + Trừ tiền ví A + Cộng tiền ví B
    DB-->>Ledger: Transaction Committed (TxId: TX_999)
    Ledger-->>IdemService: ResponseEntity<TransferResponse> (201 Created)

    IdemService->>Storage: markCompleted("KEY_123", status=201, body=JSON)
    Storage->>DB: UPDATE idempotency_records SET status='COMPLETED', response_body=JSON
    Note over Client, Controller: Mạng rớt! Client không nhận được HTTP Response 201

    Note over Client, DB: LẦN GỌI LẶP LẠI (CLIENT RETRY)
    Client->>Controller: POST /transfers/p2p (Header: Idempotency-Key: KEY_123, Payload: A->B $15)
    Controller->>IdemService: execute(actorId, "P2P", "KEY_123", payload, action)
    IdemService->>IdemService: Tính SHA-256 -> HASH_XYZ (Trùng khớp)
    IdemService->>Storage: acquireOrInspect(actorId, "P2P", "KEY_123", HASH_XYZ)
    Storage->>DB: SELECT * FROM idempotency_records WHERE key = 'KEY_123'
    DB-->>Storage: Record found (status = COMPLETED)
    Storage-->>IdemService: Decision = REPLAY (statusCode=201, body=JSON)

    Note over IdemService: Tuyệt đối KHÔNG gọi LedgerService thêm lần nào!
    IdemService->>IdemService: Deserialize JSON thành TransferResponse
    IdemService-->>Controller: ResponseEntity.status(201).body(cachedBody)
    Controller-->>Client: HTTP 201 Created (Kết quả cũ được replay an toàn)
```

---

## 5. Sequence Diagram: Phát Hiện Xung Đột Payload (`HTTP 422`)

Nếu kẻ xấu hoặc client vô tình dùng lại `Idempotency-Key` cũ nhưng lại thay đổi số tiền từ 15 USD thành 500 USD:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as TransferController
    participant IdemService as IdempotencyServiceImpl
    participant Storage as IdempotencyStorage

    Client->>Controller: POST /transfers/p2p (Key: KEY_123, Payload: A->B $500)
    Controller->>IdemService: execute(...)
    IdemService->>IdemService: Tính SHA-256 mới -> HASH_ABC
    IdemService->>Storage: acquireOrInspect(key="KEY_123", requestHash=HASH_ABC)
    Note over Storage: Kiểm tra DB thấy key KEY_123 có hash cũ là HASH_XYZ
    Note over Storage: HASH_ABC != HASH_XYZ -> PAYLOAD_MISMATCH
    Storage-->>IdemService: Decision = PAYLOAD_MISMATCH
    IdemService-->>Controller: throw AppException (HTTP 422 idempotency-payload-mismatch)
    Controller-->>Client: HTTP 422 Unprocessable Content (Từ chối ngay lập tức)
```

---

## 6. Bảng Tổng Hợp Hành Động & Lý Thuyết (Action & Theory Summary)

> **[Hành Động]**: Kết hợp mã băm SHA-256 payload với `Idempotency-Key` trong bảng `idempotency_records`.  
> **[Lý Thuyết]**: Idempotent Receiver Pattern & Cryptographic Hash Verification.  
> **[Mẫu Thiết Kế]**: Two-Phase Idempotent Execution.  
> **[Thảm Họa Tránh Được]**: Dùng lại key cũ cho một giao dịch khác làm sai lệch trạng thái hoặc chuyển nhầm số tiền lớn.  
> **[Phỏng Vấn TL;DR]**: "Mã băm SHA-256 xác thực rằng request được thử lại có đúng nội dung ban đầu hay không; nếu khác nội dung, hệ thống lập tức từ chối với mã lỗi 422 Unprocessable Content."

---

> **[Hành Động]**: Thiết lập `lease_expires_at = now + 30s` cho các bản ghi `IN_PROGRESS`.  
> **[Lý Thuyết]**: Distributed Lease & Fault Tolerance.  
> **[Mẫu Thiết Kế]**: Auto-Expiring Lease Pattern.  
> **[Thảm Họa Tránh Được]**: Server bị crash giữa chừng lúc đang xử lý khiến key bị kẹt vĩnh viễn ở trạng thái IN_PROGRESS, khách hàng không thể thử lại.  
> **[Phỏng Vấn TL;DR]**: "Cơ chế khóa thuê có thời hạn tự động giải phóng tài nguyên sau 30 giây nếu worker bị sập nguồn đột ngột, cho phép request tiếp theo thu hồi và xử lý an toàn."
