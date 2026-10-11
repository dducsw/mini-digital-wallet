# Load Test (k6) — Mini Digital Wallet

Đo TPS/latency của API trên môi trường thật (PostgreSQL + app đang chạy),
đối chiếu với `TARGET.md`. Kết quả trên máy dev là **baseline**, không thay thế
đo nghiệm thu trên máy prod 2vCPU/4GB.

## 1. Chuẩn bị

```powershell
docker compose up -d postgres          # DB ở localhost:5436
.\mvnw.cmd package -DskipTests         # build 1 lần
java -jar target\mini-digital-wallet-*.jar   # app ở localhost:8080
```

Cần [k6](https://k6.io/docs/get-started/installation/) trong PATH.

## 2. Chạy (đứng tại thư mục này)

```powershell
k6 run read-balance.js    # đọc số dư: VUS=50, 30s
k6 run write-topup.js     # nạp tiền hotspot: VUS=20, 30s
k6 run p2p-transfer.js    # chuyển P2P A->B: VUS=20, 30s
```

Ghi đè nhanh cho smoke test:

```powershell
k6 run --vus 5 --duration 10s read-balance.js
```

Mỗi lần chạy tự `setup()`: đăng ký user mới + liên kết thẻ Visa mock,
xong tự dọn ý nghĩa — không cần seed tay, không hardcode token.

## 3. Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | App cần đo |
| `VUS` / `DURATION` | theo từng script | Tải và thời lượng |
| `READ_P95_MS` | `100` | Cổng p95 kịch bản đọc (TARGET prod: `30`) |
| `WRITE_P95_MS` | `1000` | Cổng p95 kịch bản ghi (TARGET prod P2P: `120`) |
| `MAX_ERROR_RATE` | `0.01` (1%) | Cổng tỉ lệ lỗi (TARGET prod: `< 0.01%`) |

Siết chuẩn prod khi đo nghiệm thu, ví dụ:

```powershell
$env:READ_P95_MS=30; k6 run read-balance.js
```

## 4. Đọc kết quả

- `http_reqs` + `....../s` = TPS thực tế; `http_req_failed` = tỉ lệ lỗi.
- `http_req_duration p(95)` so với cổng ở mục 3; vượt cổng → k6 exit 99.
- File `summary-<kịch-bản>-<timestamp>.json` (đã gitignore) giữ lại để so sánh.

## 5. Lưu ý trung thực khi diễn giải

- Mọi iteration ghi dùng `Idempotency-Key` duy nhất (xem `uniqueKey` trong
  `common.js`). Dùng lại key sẽ nhận 409/replay nhanh bất thường → TPS ảo.
- `write-topup` và `p2p-transfer` dồn vào 1 ví nguồn = hotspot 1 row
  (kịch bản khắc nghiệt nhất). KPI "P2P ngẫu nhiên 400-800 TPS" trong TARGET.md
  cần N ví nguồn, chưa có ở đây — muốn đo thì seed N user trong `setup()`
  rồi cho mỗi VU chọn ví theo `__VU % N`.
- Sau lượt ghi, kiểm tra bất biến sổ kép: tổng DEBIT phải bằng tổng CREDIT
  trên `ledger_entries`, không ví nào âm (trừ tài khoản `SYSTEM`).
