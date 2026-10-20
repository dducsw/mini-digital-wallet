import { generateIdempotencyKey, readErrorMessage } from '../utils/formatters';

export const isUnauthorized = (res) => !!res && res.status === 401;

const getHeaders = (token, extra = {}) => {
  const headers = {
    'Content-Type': 'application/json',
    ...extra,
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

export const api = {
  // Auth
  login: async (username, password) => {
    return fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
  },

  register: async ({ username, password, email, fullName }) => {
    return fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        email,
        password,
        fullName: fullName || username,
        currency: 'USD',
      }),
    });
  },

  me: async (token) => {
    return fetch('/api/v1/auth/me', { headers: getHeaders(token) });
  },

  refresh: async (refreshToken) => {
    return fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
  },

  logout: async (refreshToken) => {
    return fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
  },

  setPin: async (pin, token) => {
    return fetch('/api/v1/auth/pin', {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({ pin }),
    });
  },

  // Wallets
  getWallets: async (ownerId, token) => {
    return fetch(`/api/v1/wallets?owner_id=${encodeURIComponent(ownerId)}`, {
      headers: getHeaders(token),
    });
  },

  createWallet: async (token) => {
    // owner_id/currency/wallet_type are all derived server-side from the JWT.
    return fetch('/api/v1/wallets', {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({ currency: 'USD' }),
    });
  },

  updateWalletStatus: async (walletId, status, token) => {
    return fetch(`/api/v1/wallets/${walletId}/status`, {
      method: 'PATCH',
      headers: getHeaders(token),
      body: JSON.stringify({ status }),
    });
  },

  // Payment Methods
  getPaymentMethods: async (ownerId, token) => {
    return fetch(`/api/v1/payment-methods?owner_id=${encodeURIComponent(ownerId)}`, {
      headers: getHeaders(token),
    });
  },

  linkCard: async (cardData, token) => {
    return fetch('/api/v1/payment-methods', {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify(cardData),
    });
  },

  revokeCard: async (id, token) => {
    return fetch(`/api/v1/payment-methods/${id}`, {
      method: 'DELETE',
      headers: getHeaders(token),
    });
  },

  // Transfers. Idempotency keys are supplied by callers so a retry reuses the
  // same key (the caller clears it only after a confirmed response).
  topUp: async ({ walletId, paymentMethodId, amount, description }, idempotencyKey, token) => {
    return fetch('/api/v1/transfers/topup', {
      method: 'POST',
      headers: getHeaders(token, { 'Idempotency-Key': idempotencyKey }),
      body: JSON.stringify({
        wallet_id: walletId,
        payment_method_id: paymentMethodId,
        amount,
        description,
      }),
    });
  },

  withdraw: async ({ walletId, paymentMethodId, amount, description, pin }, idempotencyKey, token) => {
    const extraHeaders = { 'Idempotency-Key': idempotencyKey };
    if (pin) extraHeaders['X-Transaction-Pin'] = pin;
    return fetch('/api/v1/transfers/withdraw', {
      method: 'POST',
      headers: getHeaders(token, extraHeaders),
      body: JSON.stringify({
        wallet_id: walletId,
        payment_method_id: paymentMethodId,
        amount,
        description,
      }),
    });
  },

  p2pTransfer: async ({ sourceWalletId, destinationAccountNumber, amount, description, pin }, idempotencyKey, token) => {
    const extraHeaders = { 'Idempotency-Key': idempotencyKey };
    if (pin) extraHeaders['X-Transaction-Pin'] = pin;
    return fetch('/api/v1/transfers/p2p', {
      method: 'POST',
      headers: getHeaders(token, extraHeaders),
      body: JSON.stringify({
        source_wallet_id: sourceWalletId,
        destination_account_number: destinationAccountNumber,
        amount,
        description,
      }),
    });
  },

  reverse: async ({ originalTransactionId, description }, idempotencyKey, token) => {
    return fetch('/api/v1/transfers/reversals', {
      method: 'POST',
      headers: getHeaders(token, { 'Idempotency-Key': idempotencyKey }),
      body: JSON.stringify({
        original_transaction_id: originalTransactionId,
        description,
      }),
    });
  },

  // Resolve 12-digit account number -> recipient preview before sending.
  lookupWalletByAccountNumber: async (accountNumber, token) => {
    const digits = String(accountNumber || '').replace(/\s+/g, '');
    return fetch(`/api/v1/wallets/account/${encodeURIComponent(digits)}`, {
      headers: getHeaders(token),
    });
  },

  // Statements (paginated)
  getStatement: async (walletId, { type, from, to, page = 0, size = 20 } = {}, token) => {
    const params = new URLSearchParams({ page, size });
    if (type) params.append('type', type);
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return fetch(`/api/v1/ledger/wallets/${walletId}/statement?${params.toString()}`, {
      headers: getHeaders(token),
    });
  },

  exportStatementCsv: async (walletId, { type, from, to } = {}, token) => {
    const params = new URLSearchParams();
    if (type) params.append('type', type);
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return fetch(`/api/v1/ledger/wallets/${walletId}/statement/export?${params.toString()}`, {
      headers: getHeaders(token),
    });
  },

  // Contacts
  getContacts: async (token) => {
    return fetch('/api/v1/contacts', {
      headers: getHeaders(token),
    });
  },

  createContact: async ({ accountNumber, nickname }, token) => {
    return fetch('/api/v1/contacts', {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({
        account_number: accountNumber,
        nickname,
      }),
    });
  },

  deleteContact: async (id, token) => {
    return fetch(`/api/v1/contacts/${id}`, {
      method: 'DELETE',
      headers: getHeaders(token),
    });
  },

  toggleFavoriteContact: async (id, token) => {
    return fetch(`/api/v1/contacts/${id}/favorite`, {
      method: 'PATCH',
      headers: getHeaders(token),
    });
  },
};

export { generateIdempotencyKey, readErrorMessage };
