export const formatCentsToUSD = (cents) => {
  if (cents === null || cents === undefined) return '$0.00';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(cents / 100);
};

export const formatWalletAccountNumber = (uuid) => {
  if (!uuid) return '8820 0000 0000';
  let hash = 0;
  for (let i = 0; i < uuid.length; i++) {
    hash = (hash * 31 + uuid.charCodeAt(i)) >>> 0;
  }
  const numStr = (882000000000n + BigInt(hash)).toString().slice(0, 12);
  return `${numStr.slice(0, 4)} ${numStr.slice(4, 8)} ${numStr.slice(8, 12)}`;
};

export const generateIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'idemp-' + Math.random().toString(36).substring(2, 15) + '-' + Date.now();
};
