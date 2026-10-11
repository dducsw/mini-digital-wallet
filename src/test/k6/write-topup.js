// Kịch bản GHI dồn (hotspot): nạp tiền vào 1 ví — ứng với dòng
// "Ghi dồn (Hotspot Account)" trong TARGET.md (mục tiêu prod >= 600 TPS).
// Mọi request dồn vào 1 row ví + 1 row SYS_EXTERNAL_SETTLEMENT nên đây là
// kịch bản khắc nghiệt nhất, không phải throughput tối đa của hệ thống.
import http from 'k6/http';
import { BASE_URL, writeThresholds } from './config.js';
import { seedUser, authHeaders, uniqueKey, expectStatus, summaryHandler } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '30s',
  thresholds: writeThresholds,
};

export function setup() {
  return seedUser('topup');
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/v1/transfers/topup`,
    JSON.stringify({
      wallet_id: data.walletId,
      payment_method_id: data.cardId,
      amount: 1000, // $10.00 — nạp từ thẻ mock nên không lo cạn số dư
      description: 'k6 topup',
    }),
    authHeaders(data.token, uniqueKey('k6-topup')),
  );
  expectStatus(res, 'topup', 201);
}

export const handleSummary = summaryHandler('write-topup');
