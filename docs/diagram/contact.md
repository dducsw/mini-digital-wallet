# Kiến Trúc & Luồng Tương Tác Contact Service (Danh Bạ Thụ Hưởng)

Tài liệu thiết kế chi tiết kiến trúc tầng, mô hình dữ liệu và các sequence diagram cho tính năng lưu danh bạ thụ hưởng chuyển tiền P2P theo **Số tài khoản 12 chữ số** trong **Mini Digital Wallet**.

---

## 1. Mục Đích & Nguyên Tắc Thiết Kế

Khi chuyển tiền giữa các cá nhân (P2P Transfer), việc yêu cầu người dùng nhập chuỗi UUID dài 36 ký tự (ví dụ: `5f425555-2f70-4d56-bc5e-828049fe93c6`) là điều bất khả thi và gây ức chế lớn cho khách hàng.

Hệ thống giải quyết vấn đề này bằng hai giải pháp kết hợp:
1. **Số tài khoản 12 chữ số thân thiện (`account_number`)**: Mỗi ví được cấp một số tài khoản 12 số dễ nhớ (đầu số `8820`).
2. **Danh bạ thụ hưởng (Saved Contacts)**: Cho phép lưu số tài khoản của bạn bè, đồng nghiệp kèm biệt danh (nickname) và đánh dấu người nhận yêu thích.

### Các Quy Tắc Bảo Vệ Toàn Vẹn Danh Bạ:
- **Tồn tại thực tế**: Số tài khoản muốn lưu bắt buộc phải tồn tại trên hệ thống (kiểm tra qua `WalletLookup`).
- **Không tự lưu chính mình**: Hệ thống từ chối lưu số tài khoản của chính bản thân người gọi (tránh tự chuyển tiền cho mình gây lãng phí tài nguyên và nhầm lẫn).
- **Không lưu trùng**: Mỗi người dùng chỉ được lưu một số tài khoản đúng 1 lần (`UNIQUE(owner_id, account_number)`).
- **Bảo mật BOLA**: Người dùng chỉ có quyền xem, sửa trạng thái yêu thích hoặc xóa danh bạ do chính mình tạo ra.

---

## 2. Mô Hình Dữ Liệu Danh Bạ (ERD)

```mermaid
erDiagram
    users ||--o{ contacts : "creates (1:N)"
    wallets ||--o{ contacts : "referenced by account_number"

    contacts {
        uuid id PK "Khóa chính UUIDv4"
        varchar owner_id "ID chủ danh bạ (users.id)"
        varchar account_number "STK 12 chữ số thụ hưởng"
        varchar nickname "Biệt danh gợi nhớ (Alice, Mẹ...)"
        boolean is_favorite "Cờ đánh dấu tài khoản ưu tiên"
        timestamptz created_at "Thời điểm tạo"
        timestamptz updated_at "Cập nhật cuối"
    }
```

---

## 3. Sequence Diagram: Luồng Lưu Người Thụ Hưởng Mới (`POST /api/v1/contacts`)

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Người Dùng
    participant Controller as ContactController
    participant Service as ContactServiceImpl
    participant Lookup as WalletLookup
    participant Repo as ContactRepository
    participant DB as PostgreSQL

    KhachHang->>Controller: POST /api/v1/contacts<br/>{"accountNumber": "8820 1234 5678", "nickname": "Alice"}
    Note over Controller: 1. Kiểm tra định dạng đầu vào (@Valid)
    Controller->>Service: createContact(callerOwnerId, request)

    Note over Service: 2. Chuẩn hóa: Loại bỏ khoảng trắng thừa -> 882012345678
    Service->>Lookup: resolveWalletIdByAccountNumber("882012345678")
    Lookup->>DB: SELECT id FROM wallets WHERE account_number = ?
    DB-->>Lookup: targetWalletId (hoặc null)

    alt Số tài khoản không tồn tại
        Service-->>Controller: throw AppException (HTTP 404 account-not-found)
        Controller-->>KhachHang: HTTP 404 Not Found
    else Số tài khoản có thật
        Service->>Lookup: getWalletOwnerId(targetWalletId)
        Lookup-->>Service: targetOwnerId

        alt Tự thêm tài khoản của chính mình (callerOwnerId == targetOwnerId)
            Service-->>Controller: throw AppException (HTTP 400 cannot-add-self)
            Controller-->>KhachHang: HTTP 400 Bad Request
        else Hợp lệ
            Service->>Repo: existsByOwnerIdAndAccountNumber(callerOwnerId, cleanAcct)
            Repo->>DB: SELECT EXISTS(...)
            DB-->>Repo: true / false

            alt Đã có trong danh bạ từ trước
                Service-->>Controller: throw DuplicateRequestException (HTTP 409)
                Controller-->>KhachHang: HTTP 409 Conflict
            else Chưa có
                Service->>Repo: save(Contact: nickname, cleanAcct, isFavorite=false)
                Repo->>DB: INSERT INTO contacts (...)
                DB-->>Repo: Saved Entity
                Service-->>Controller: ContactResponse DTO
                Controller-->>KhachHang: HTTP 201 Created
            end
        end
    end
```

---

## 4. Sequence Diagram: Luồng Lấy Danh Sách Danh Bạ (`GET /api/v1/contacts`)

Hệ thống tối ưu hóa trải nghiệm người dùng bằng cách sắp xếp các tài khoản thường xuyên giao dịch hoặc được gắn sao yêu thích lên đầu danh sách:

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Người Dùng
    participant Controller as ContactController
    participant Service as ContactServiceImpl
    participant Repo as ContactRepository
    participant DB as PostgreSQL

    KhachHang->>Controller: GET /api/v1/contacts
    Controller->>Service: getContacts(callerOwnerId)
    Service->>Repo: findByOwnerIdOrderByIsFavoriteDescCreatedAtDesc(callerOwnerId)
    Repo->>DB: SELECT * FROM contacts WHERE owner_id = ?<br/>ORDER BY is_favorite DESC, created_at DESC
    DB-->>Repo: List<Contact>
    Service-->>Controller: List<ContactResponse>
    Controller-->>KhachHang: HTTP 200 OK (Danh bạ ưu tiên tài khoản yêu thích)
```

---

## 5. Sequence Diagram: Bật/Tắt Yêu Thích (`PATCH /api/v1/contacts/{id}/favorite`)

```mermaid
sequenceDiagram
    autonumber
    actor KhachHang as Người Dùng
    participant Controller as ContactController
    participant Service as ContactServiceImpl
    participant Repo as ContactRepository
    participant DB as PostgreSQL

    KhachHang->>Controller: PATCH /api/v1/contacts/{id}/favorite
    Controller->>Service: toggleFavorite(callerOwnerId, id)
    Service->>Repo: findByIdAndOwnerId(id, callerOwnerId)
    Repo->>DB: SELECT * FROM contacts WHERE id = ? AND owner_id = ?
    DB-->>Repo: Optional<Contact>

    alt Không tìm thấy contact của caller
        Service-->>Controller: throw AppException (HTTP 404 contact-not-found)
        Controller-->>KhachHang: HTTP 404 Not Found
    else Tìm thấy contact hợp lệ
        Note over Service: Đảo ngược cờ: isFavorite = !isFavorite
        Service->>Repo: save(contact)
        Repo->>DB: UPDATE contacts SET is_favorite = ?, updated_at = NOW() WHERE id = ?
        DB-->>Repo: Updated
        Service-->>Controller: ContactResponse (với trạng thái mới)
        Controller-->>KhachHang: HTTP 200 OK
    end
```

---

## 6. Bảng Tổng Hợp Hành Động & Lý Thuyết (Action & Theory Summary)

> **[Hành Động]**: Kiểm tra `WalletLookup` và cấm tự thêm chính mình (`callerOwnerId != targetOwnerId`) trước khi lưu danh bạ.  
> **[Lý Thuyết]**: Domain Invariant Verification & Input Sanitization.  
> **[Mẫu Thiết Kế]**: Guard Clause Pattern.  
> **[Thảm Họa Tránh Được]**: Người dùng lưu số tài khoản ma hoặc tự chuyển tiền qua lại cho chính mình gây nghẽn log giao dịch.  
> **[Phỏng Vấn TL;DR]**: "Xác thực sự tồn tại của thực thể đích qua dịch vụ phân giải tài khoản và áp dụng guard clause chặn tự tham chiếu ngay tại tầng service."

---

> **[Hành Động]**: Thiết lập ràng buộc cơ sở dữ liệu `UNIQUE(owner_id, account_number)`.  
> **[Lý Thuyết]**: Database Level Uniqueness Constraint.  
> **[Mẫu Thiết Kế]**: Idempotent Data Model.  
> **[Thảm Họa Tránh Được]**: Người dùng bấm lưu danh bạ nhiều lần sinh ra các bản ghi trùng lặp gây rối danh bạ.  
> **[Phỏng Vấn TL;DR]**: "Chỉ mục duy nhất ở database là chốt chặn cuối cùng đảm bảo không bao giờ có 2 bản ghi trùng lặp số tài khoản cho cùng một chủ sở hữu."
