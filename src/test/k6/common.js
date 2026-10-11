// Helper dùng chung: seed data, header auth, key idempotency, summary.
// Quy tắc: script không hardcode token/wallet — mọi thứ lấy từ setup().
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, PASSWORD } from './config.js';

// Đăng ký user mới + liên kết thẻ Visa mock. Chạy 1 lần trong setup();
// username duy nhất theo timestamp nên không đụng data các lần chạy trước.
export function seedUser(tag) {
  const suffix = `${tag}-${Date.now()}`;
  const reg = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      username: `k6-${suffix}`,
      email: `k6-${suffix}@test.com`,
      password: PASSWORD,
      fullName: `k6 ${tag}`,
      currency: 'USD',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(reg, { 'register 201': (r) => r.status === 201 });
  const { accessToken, userId, defaultWalletId } = reg.json();

  const card = http.post(
    `${BASE_URL}/api/v1/payment-methods`,
    JSON.stringify({
      owner_id: userId,
      method_type: 'DEBIT_CARD',
      provider: 'VISA',
      card_number: '4242424242424242',
      holder_name: 'K6 LOAD',
      is_default: true,
    }),
    authHeaders(accessToken),
  );
  check(card, { 'link-card 201': (r) => r.status === 201 });

  return { token: accessToken, userId, walletId: defaultWalletId, cardId: card.json().id };
}

export function authHeaders(token, idempotencyKey) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  return { headers };
}

// Key duy nhất mỗi iteration — bắt buộc với API idempotent.
// Dùng lại key cũ sẽ nhận 409/replay (nhanh bất thường) làm TPS đo được bị ảo.
export function uniqueKey(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

export function expectStatus(res, label, status) {
  const assertion = {};
  assertion[`${label} ${status}`] = (r) => r.status === status;
  return check(res, assertion);
}

// Ghi summary JSON cạnh script để đối chiếu giữa các lần chạy (đã gitignore).
export function summaryHandler(name) {
  return (data) => {
    const file = {};
    file[`summary-${name}-${Date.now()}.json`] = JSON.stringify(data);
    return file;
  };
}
