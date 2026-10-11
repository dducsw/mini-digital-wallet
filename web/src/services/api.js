import { generateIdempotencyKey } from '../utils/formatters';

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

  // Wallets
  getWallets: async (ownerId, token) => {
    return fetch(`/api/v1/wallets?owner_id=${encodeURIComponent(ownerId)}`, {
      headers: getHeaders(token),
    });
  },

  createWallet: async ({ ownerId, currency = 'USD', walletType = 'USER' }, token) => {
    return fetch('/api/v1/wallets', {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({
        owner_id: ownerId,
        currency,
        wallet_type: walletType,
      }),
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

  // Transfers (Idempotent)
  topUp: async ({ walletId, paymentMethodId, amount, description }, token) => {
    return fetch('/api/v1/transfers/topup', {
      method: 'POST',
      headers: getHeaders(token, { 'Idempotency-Key': generateIdempotencyKey() }),
      body: JSON.stringify({
        wallet_id: walletId,
        payment_method_id: paymentMethodId,
        amount,
        description,
      }),
    });
  },

  withdraw: async ({ walletId, paymentMethodId, amount, description }, token) => {
    return fetch('/api/v1/transfers/withdraw', {
      method: 'POST',
      headers: getHeaders(token, { 'Idempotency-Key': generateIdempotencyKey() }),
      body: JSON.stringify({
        wallet_id: walletId,
        payment_method_id: paymentMethodId,
        amount,
        description,
      }),
    });
  },

  p2pTransfer: async ({ sourceWalletId, destinationWalletId, amount, description }, token) => {
    return fetch('/api/v1/transfers/p2p', {
      method: 'POST',
      headers: getHeaders(token, { 'Idempotency-Key': generateIdempotencyKey() }),
      body: JSON.stringify({
        source_wallet_id: sourceWalletId,
        destination_wallet_id: destinationWalletId,
        amount,
        description,
      }),
    });
  },

  // Ledger Entries
  getLedgerEntries: async (walletId, token) => {
    return fetch(`/api/v1/ledger/wallets/${walletId}/entries`, {
      headers: getHeaders(token),
    });
  },
};
