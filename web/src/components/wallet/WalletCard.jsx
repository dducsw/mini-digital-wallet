import React from 'react';
import { Lock, Unlock, Copy, Check } from 'lucide-react';
import { formatCentsToUSD } from '../../utils/formatters';

const formatAccountNo = (accountNumber) => {
  const digits = String(accountNumber || '').replace(/\D/g, '');
  if (/^[0-9]{12}$/.test(digits)) {
    return `${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)}`;
  }
  return digits || '—';
};

export default function WalletCard({ wallet, copied, onCopyId, onToggleFreeze }) {
  return (
    <div className="hero-balance-card">
      <div className="hero-top-row">
        <div>
          <span className="balance-title-label">Available Balance</span>
          <div className="hero-main-balance">
            {formatCentsToUSD(wallet.available_balance)}
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span
            className={`status-badge-pill ${
              wallet.account_status === 'ACTIVE' ? 'badge-active-white' : 'badge-frozen-white'
            }`}
          >
            {wallet.account_status}
          </span>
          <button
            className="btn btn-sm"
            style={{
              background: 'rgba(255, 255, 255, 0.2)',
              color: '#ffffff',
              border: '1px solid rgba(255, 255, 255, 0.35)',
            }}
            onClick={onToggleFreeze}
            disabled={wallet.account_status === 'CLOSED'}
            title={wallet.account_status === 'CLOSED' ? 'Closed wallets cannot change status' : undefined}
          >
            {wallet.account_status === 'ACTIVE' ? (
              <>
                <Lock size={13} /> Freeze
              </>
            ) : (
              <>
                <Unlock size={13} /> Unfreeze
              </>
            )}
          </button>
        </div>
      </div>

      <div
        style={{
          marginTop: '16px',
          paddingTop: '14px',
          borderTop: '1px solid rgba(255, 255, 255, 0.2)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '12px',
          fontSize: '12px',
          color: 'rgba(255, 255, 255, 0.9)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.04em', opacity: 0.85 }}>
            Account No:
          </span>
          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, letterSpacing: '0.06em' }}>
            {formatAccountNo(wallet.account_number)}
          </span>
          <button
            className="btn btn-sm"
            style={{
              padding: '2px 8px',
              fontSize: '11px',
              background: 'rgba(255, 255, 255, 0.2)',
              color: '#ffffff',
              border: '1px solid rgba(255, 255, 255, 0.35)',
            }}
            onClick={() => onCopyId(String(wallet.account_number || '').replace(/\s+/g, ''))}
            title="Copy 12-digit account number for P2P transfers"
          >
            {copied ? <><Check size={11} /> Copied</> : <><Copy size={11} /> Copy</>}
          </button>
        </div>

        <div style={{ fontSize: '12px' }}>
          <span>Type: <strong>{wallet.wallet_type || 'USER'}</strong></span>
        </div>
      </div>
    </div>
  );
}
