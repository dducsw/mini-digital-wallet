// Cấu hình chung cho mọi kịch bản k6. Mọi giá trị đọc từ biến môi trường,
// có default chạy được ngay trên máy dev (xem README + TARGET.md).
function num(name, fallback) {
  const raw = __ENV[name];
  return raw === undefined || raw === '' ? fallback : Number(raw);
}

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PASSWORD = __ENV.K6_PASSWORD || 'Password123!';

// Ngưỡng fail/pass. Default = cổng dev; TARGET.md (máy prod 2vCPU/4GB) chặt hơn,
// truyền ENV để siết khi đo nghiệm thu (ví dụ: READ_P95_MS=30).
export const READ_P95_MS = num('READ_P95_MS', 100);
export const WRITE_P95_MS = num('WRITE_P95_MS', 1000);
export const MAX_ERROR_RATE = __ENV.MAX_ERROR_RATE === undefined ? 0.01 : Number(__ENV.MAX_ERROR_RATE);

export const readThresholds = {
  http_req_failed: [`rate<${MAX_ERROR_RATE}`],
  http_req_duration: [`p(95)<${READ_P95_MS}`],
};

export const writeThresholds = {
  http_req_failed: [`rate<${MAX_ERROR_RATE}`],
  http_req_duration: [`p(95)<${WRITE_P95_MS}`],
};
