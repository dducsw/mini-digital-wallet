import React from 'react';
import { CreditCard, Lock, Unlock, Copy, Check, Plus } from 'lucide-react';
import { formatCentsToUSD, formatWalletAccountNumber } from '../../utils/formatters';

export default function WalletCard({
  wallet,
  copied,
  onCopyId,
  onToggleFreeze,
  onCreateWalletClick,
}) {
  if (!wallet) {
    return (
      <div className="white-card empty-placeholder">
        <div className="empty-icon-box">
          <CreditCard size={24} />
        </div>
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '6px' }}>
          No USD Wallet Found
        </h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '18px' }}>
          Create an active USD digital wallet to start transacting.
        </p>
        <button className="btn btn-primary" onClick={onCreateWalletClick}>
          <Plus size={15} /> Create Wallet
        </button>
      </div>
    );
  }

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
              wallet.status === 'ACTIVE' ? 'badge-active-white' : 'badge-frozen-white'
            }`}
          >
            {wallet.status}
          </span>
          <button
            className="btn btn-sm"
            style={{
              background: 'rgba(255, 255, 255, 0.2)',
              color: '#ffffff',
              border: '1px solid rgba(255, 255, 255, 0.35)',
            }}
            onClick={onToggleFreeze}
            disabled={wallet.status === 'CLOSED'}
            title={wallet.status === 'CLOSED' ? 'Closed wallets cannot change status' : undefined}
          >
            {wallet.status === 'ACTIVE' ? (
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
            {formatWalletAccountNumber(wallet.id)}
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
            onClick={() => onCopyId(wallet.id)}
            title="Copy backend Wallet ID (UUID) for transfers"
          >
            {copied ? <><Check size={11} /> Copied</> : <><Copy size={11} /> Copy ID</>}
          </button>
        </div>

        <div style={{ fontSize: '12px' }}>
          <span>Type: <strong>{wallet.account_type || wallet.wallet_type || 'PERSONAL'}</strong></span>
        </div>
      </div>
    </div>
  );
}
