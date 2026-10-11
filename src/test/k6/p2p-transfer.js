// Kịch bản GHI chuẩn: chuyển P2P một chiều A -> B — ứng với dòng
// "Ghi chuẩn (P2P Transfer)" trong TARGET.md (mục tiêu prod: 400-800 TPS).
// Một chiều nên không deadlock; mọi VU dùng chung ví A (hotspot 1 row).
// P2P ngẫu nhiên N ví (đúng điều kiện đo KPI) là bước tiếp theo, xem README.
import http from 'k6/http';
import { BASE_URL, writeThresholds } from './config.js';
import { seedUser, authHeaders, uniqueKey, expectStatus, summaryHandler } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '30s',
  thresholds: writeThresholds,
};

const FUND_CENTS = 10000000; // $100k — đủ cho cả lượt chạy với amount nhỏ bên dưới

export function setup() {
  const a = seedUser('p2p-a');
  const b = seedUser('p2p-b');
  const fund = http.post(
    `${BASE_URL}/api/v1/transfers/topup`,
    JSON.stringify({
      wallet_id: a.walletId,
      payment_method_id: a.cardId,
      amount: FUND_CENTS,
      description: 'k6 fund',
    }),
    authHeaders(a.token, `k6-fund-${Date.now()}`),
  );
  expectStatus(fund, 'fund', 201);
  return { sender: a, receiverWalletId: b.walletId };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/v1/transfers/p2p`,
    JSON.stringify({
      source_wallet_id: data.sender.walletId,
      destination_wallet_id: data.receiverWalletId,
      amount: 1000, // $10.00 — nhỏ để ví nguồn không bao giờ cạn giữa chừng
      description: 'k6 p2p',
    }),
    authHeaders(data.sender.token, uniqueKey('k6-p2p')),
  );
  expectStatus(res, 'p2p', 201);
}

export const handleSummary = summaryHandler('p2p-transfer');
