# User Stories & Acceptance Criteria

Tài liệu đặc tả các User Story cốt lõi theo chuẩn Agile/Scrum. Mỗi Story bao gồm lý do nghiệp vụ và tiêu chí nghiệm thu (Acceptance Criteria) viết theo định dạng Gherkin (**Given - When - Then**).

---

## Epic 1: Chuyển tiền P2P & Bảo vệ số dư (P2P Transfer)

### Story US-01: Chuyển tiền nội bộ bằng số tài khoản
> **Là một** chủ ví cá nhân,  
> **Tôi muốn** chuyển tiền sang tài khoản người khác bằng số tài khoản 12 chữ số,  
> **Để** thanh toán hoặc gửi tiền cho người thân nhanh chóng.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Chuyển tiền thành công (Happy Path)
  Given Ví người gửi A đang ACTIVE có số dư khả dụng $100.00
  And Ví người nhận B đang ACTIVE
  And Người gửi đã nhập đúng mã PIN giao dịch
  When Người gửi yêu cầu chuyển $30.00 sang ví B với Idempotency-Key hợp lệ
  Then Số dư khả dụng của A giảm còn $70.00
  And Số dư khả dụng của B tăng thêm $30.00
  And Hệ thống tạo 1 ledger_transaction kèm 2 bút toán: DEBIT ví A ($30), CREDIT ví B ($30)
  And Trả về mã HTTP 200 kèm mã tham chiếu giao dịch

Scenario: Số dư khả dụng không đủ
  Given Ví người gửi A có số dư khả dụng $20.00
  When Người gửi yêu cầu chuyển $50.00 sang ví B
  Then Giao dịch bị từ chối với lỗi "Insufficient Balance"
  And Số dư hai ví không thay đổi
  And Không phát sinh bút toán mới trong sổ cái

Scenario: Người nhận là chính mình (Self-transfer)
  When Người gửi yêu cầu chuyển tiền vào chính số tài khoản của mình
  Then Giao dịch bị từ chối với thông báo "Cannot transfer to the same account"
```

---

### Story US-02: Xác thực mã PIN và cơ chế chống dò mật khẩu
> **Là một** chủ ví,  
> **Tôi muốn** mọi giao dịch chuyển/rút tiền phải nhập mã PIN bảo mật,  
> **Để** kẻ xấu không thể tự ý chuyển tiền khi cầm điện thoại của tôi.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Nhập sai PIN dưới 5 lần
  Given Người dùng thực hiện chuyển tiền
  When Nhập sai mã PIN lần thứ 1 đến lần thứ 4
  Then Giao dịch bị từ chối
  And Hệ thống tăng biến đếm số lần sai
  And Hiển thị số lần thử còn lại cho người dùng

Scenario: Khóa tính năng giao dịch khi sai PIN 5 lần liên tiếp
  Given Người dùng đã nhập sai PIN 4 lần trước đó
  When Người dùng tiếp tục nhập sai PIN lần thứ 5
  Then Hệ thống khóa quyền giao dịch bằng PIN trong vòng 15 phút
  And Trả về thông báo: "Bạn đã nhập sai PIN 5 lần. Vui lòng thử lại sau 15 phút"
  And Các giao dịch tiếp theo trong 15 phút bị chặn ngay tại tầng xác thực
```

---

## Epic 2: Kiểm soát hạn mức & Chống bấm trùng (Risk & Idempotency)

### Story US-03: Chống trừ tiền trùng lặp khi mạng chập chờn (Idempotency)
> **Là một** người dùng thanh toán trên di động,  
> **Tôi muốn** hệ thống không trừ tiền hai lần khi tôi bấm xác nhận nhiều lần do mạng lag,  
> **Để** không bị thất thoát tiền oan uổng.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Bấm chuyển tiền liên tiếp với cùng một Idempotency-Key
  Given Người gửi bấm chuyển $50.00 kèm Idempotency-Key "IDEM-ABC-123"
  And Request thứ nhất đang được xử lý trong database
  When Request thứ hai với cùng key "IDEM-ABC-123" được gửi tới
  Then Request thứ hai nhận HTTP 409 Conflict với thông báo "Giao dịch đang được xử lý"
  
Scenario: Gửi lại request sau khi giao dịch đầu đã hoàn tất
  Given Request với key "IDEM-ABC-123" đã xử lý thành công trước đó
  When Client vô tình gửi lại request với cùng key "IDEM-ABC-123"
  Then Hệ thống trả về nguyên vẹn kết quả của lần xử lý trước mà không trừ tiền thêm lần nào
```

---

### Story US-04: Áp dụng hạn mức giao dịch (Velocity Limits)
> **Là một** chuyên viên kiểm soát rủi ro (Risk Manager),  
> **Tôi muốn** hệ thống áp trần số tiền chuyển tối đa theo lần và theo ngày,  
> **Để** giảm thiểu thiệt hại nếu tài khoản người dùng bị hack.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Giao dịch vượt hạn mức một lần ($10,000)
  When Người dùng yêu cầu chuyển $10,001.00 trong một giao dịch
  Then Giao dịch bị từ chối với lỗi "Exceeded per-transaction limit of $10,000"

Scenario: Tổng giao dịch trong ngày vượt $50,000
  Given Người dùng đã chuyển thành công $45,000.00 trong ngày
  When Người dùng tiếp tục yêu cầu chuyển $6,000.00 cùng ngày
  Then Giao dịch bị từ chối với lỗi "Exceeded daily transfer limit of $50,000"
```

---

## Epic 3: Trải nghiệm người dùng & Giảm sai sót (UX & Retention)

### Story US-05: Lưu danh bạ người thụ hưởng (Saved Contacts)
> **Là một** người thường xuyên chuyển tiền cho bạn bè/đối tác,  
> **Tôi muốn** lưu lại số tài khoản người nhận kèm biệt danh,  
> **Để** không phải nhập lại 12 số tài khoản mỗi lần chuyển và tránh chuyển nhầm.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Thêm danh bạ người thụ hưởng hợp lệ
  Given Người dùng muốn lưu số tài khoản "100000000002" với biệt danh "Chủ Nhà Trọ"
  When Hệ thống kiểm tra số tài khoản tồn tại và đang ACTIVE
  Then Lưu thông tin vào danh bạ của người dùng
  And Người dùng có thể chọn nhanh người này từ danh sách gợi ý khi chuyển tiền

Scenario: Thêm số tài khoản không tồn tại
  When Người dùng nhập một số tài khoản không có trên hệ thống
  Then Báo lỗi "Tài khoản thụ hưởng không tồn tại" và không cho phép lưu
```

---

## Epic 4: Vận hành & Tra soát đối soát (Ops & Reconciliation)

### Story US-06: Hoàn tiền giao dịch không tẩy xóa (Reversal)
> **Là một** nhân viên vận hành (Ops),  
> **Tôi muốn** tạo lệnh hoàn trả tiền (Reversal) liên kết với mã giao dịch gốc,  
> **Để** giải quyết khiếu nại của khách hàng mà vẫn giữ nguyên vẹn lịch sử sổ cái.

#### Acceptance Criteria (AC):
```gherkin
Scenario: Hoàn trả tiền thành công
  Given Giao dịch gốc TX-100 chuyển $50 từ A sang B đã hoàn tất
  And Ví B vẫn còn đủ số dư khả dụng >= $50
  When Quản trị viên kích hoạt Reversal cho TX-100
  Then Hệ thống tạo giao dịch đảo REVERSAL-TX-100 liên kết với TX-100
  And Ghi DEBIT ví B ($50) và CREDIT ví A ($50)
  And Bản ghi TX-100 cũ không bị sửa hay xóa
```
