# Mini Digital Wallet — Web Client

Giao diện Web SPA hiện đại cho hệ thống **Mini Digital Wallet**, xây dựng trên nền tảng **React 19** và **Vite 8**.

---

## 1. Tính Năng Giao Diện

- **Xác thực & Danh tính**:
  - Đăng ký tài khoản mới (tự động mở ví mặc định).
  - Đăng nhập JWT (kết nối rate-limiting backend 5 lần sai/15 phút).
  - Thanh Quick Demo Switcher đăng nhập nhanh cho các tài khoản mẫu (`alice`, `bob`, `charlie`).
- **Dashboard Thẻ Ví (Wallet Card)**:
  - Hiển thị số tài khoản 12 chữ số định dạng ngân hàng (`8820 xxxx xxxx`) kèm nút sao chép nhanh.
  - Hiển thị số dư khả dụng tức thời chuẩn USD.
  - Đóng băng an toàn (Freeze) hoặc kích hoạt lại ví (Reactivate) tức thì.
- **Phương Thức Thanh Toán (Payment Methods)**:
  - Liên kết thẻ ghi nợ/tín dụng chuẩn PCI-DSS tokenized (chỉ hiển thị `**** 4242`).
  - Hủy liên kết thẻ thanh toán.
- **Chuyển & Rút Tiền (Transfers Tab)**:
  - **Nạp tiền (Top-up)** từ thẻ liên kết vào ví.
  - **Rút tiền (Withdraw)** từ ví về thẻ liên kết: yêu cầu nhập mã PIN giao dịch 6 số nếu đã kích hoạt.
  - **Chuyển tiền P2P (P2P Transfer)**:
    - Nhập số tài khoản 12 số (tự động phân nhóm 4-4-4 khi gõ).
    - Thanh chọn nhanh người thụ hưởng từ **Danh bạ đã lưu (Saved Contacts)**.
    - Checkbox *"Lưu người nhận vào danh bạ"* kèm tên gợi nhớ (nickname).
    - Nhập mã PIN giao dịch 6 số bảo vệ giao dịch.
    - Tự động kiểm soát hạn mức giao dịch ($10,000/lệnh, $50,000/ngày).
- **Sao Kê & Lịch Sử Biến Động (Statement & Ledger Tab)**:
  - Bảng dòng tiền sổ cái chuẩn xác.
  - Thanh công cụ bộ lọc nâng cao:
    - Lọc theo loại bút toán: `Tất cả`, `CREDIT` (tiền vào), `DEBIT` (tiền ra).
    - Lọc theo khoảng thời gian: `Từ ngày (From)` $\rightarrow$ `Đến ngày (To)`.
  - **Nút Export CSV**: Gọi endpoint stream backend, tải trực tiếp file `statement-{walletId}.csv` về máy.
- **Bảo Mật Người Dùng (Profile Modal)**:
  - Xem thông tin tài khoản hiện tại.
  - Form cài đặt / thay đổi **Mã PIN giao dịch 6 chữ số** (xác thực 2 lớp bảo vệ tiền).

---

## 2. Cấu Trúc Thư Mục

```
web/
├── src/
│   ├── components/
│   │   ├── auth/           # Giao diện đăng nhập, đăng ký, demo switcher (AuthPage)
│   │   ├── common/         # Navbar, Toast thông báo, Loading indicator
│   │   ├── cards/          # Thẻ ví (WalletCard), Thẻ thanh toán (PaymentCard)
│   │   ├── transfers/      # TransfersTab (Top-up, Withdraw + PIN, P2P + PIN + Contacts)
│   │   ├── paymentmethods/ # PaymentMethodsTab (quản lý thẻ liên kết)
│   │   ├── ledger/         # LedgerTab (lịch sử sổ cái, bộ lọc sao kê, xuất CSV)
│   │   └── modals/         # Modal liên kết thẻ, tạo ví, đổi mã PIN (ProfileModal)
│   ├── services/
│   │   └── api.js          # Fetch client giao tiếp Backend API, sinh Idempotency-Key
│   ├── utils/
│   │   └── formatters.js   # Định dạng tiền tệ cents -> USD, định dạng ngày tháng
│   ├── App.jsx             # Controller chính điều phối state và events
│   ├── main.jsx            # Entry point ứng dụng React
│   └── index.css           # Bảng màu thiết kế Fintech (Blue Modern UI)
├── package.json
└── vite.config.js
```

---

## 3. Khởi Chạy Ứng Dụng

Yêu cầu môi trường: **Node.js 20+** và **npm**.

### Cài đặt thư viện:
```bash
npm install
```

### Chạy chế độ phát triển (Development):
```bash
npm run dev
```
Ứng dụng sẽ chạy tại: `http://localhost:5173`. Request đến `/api/v1` được proxy tự động về Spring Boot backend tại `http://localhost:8080`.

### Kiểm tra build sản phẩm (Production Build):
```bash
npm run build
```
Build hoàn tất sẽ xuất các file tối ưu hóa vào thư mục `dist/`.

### Xem trước bản build:
```bash
npm run preview
```
