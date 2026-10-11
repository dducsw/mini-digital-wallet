// Kịch bản ĐỌC: xem số dư ví — ứng với dòng "Đọc (Read-only)" trong TARGET.md
// (mục tiêu prod: 1500-3000 TPS, p95 < 30ms, p99 < 80ms).
import http from 'k6/http';
import { BASE_URL, readThresholds } from './config.js';
import { seedUser, authHeaders, expectStatus, summaryHandler } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURATION || '30s',
  thresholds: readThresholds,
};

export function setup() {
  return seedUser('read');
}

export default function (data) {
  const res = http.get(
    `${BASE_URL}/api/v1/wallets/${data.walletId}/balance`,
    authHeaders(data.token),
  );
  expectStatus(res, 'get-balance', 200);
}

export const handleSummary = summaryHandler('read-balance');
