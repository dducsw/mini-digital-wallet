export const formatCentsToUSD = (cents) => {
  if (cents === null || cents === undefined) return '$0.00';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(cents / 100);
};

// "882012345678" -> "8820 1234 5678" for readable account-number inputs.
export const formatAccountInput = (raw) => {
  const digits = String(raw || '').replace(/\D/g, '').slice(0, 12);
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim();
};

export const generateIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'idemp-' + Math.random().toString(36).substring(2, 15) + '-' + Date.now();
};

// Extract a human-readable message from an RFC 7807 error response.
export const readErrorMessage = async (res, fallback) => {
  const err = await res.json().catch(() => ({}));
  return err.detail || fallback;
};
