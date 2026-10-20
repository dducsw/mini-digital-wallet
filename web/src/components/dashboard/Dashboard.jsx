import React from 'react';
import { Send, Plus, Minus, CreditCard, History, Users, PlusCircle } from 'lucide-react';
import WalletCard from '../wallet/WalletCard';

const TILES = [
  { id: 'transfer', label: 'Transfer', desc: 'Send money via 12-digit account number', icon: Send },
  { id: 'topup', label: 'Top Up', desc: 'Add USD via a linked card', icon: Plus },
  { id: 'withdraw', label: 'Withdraw', desc: 'Withdraw USD to a linked card', icon: Minus },
  { id: 'cards', label: 'My Cards', desc: 'Manage linked payment cards', icon: CreditCard, badge: 'paymentMethods' },
  { id: 'history', label: 'History', desc: 'Statement & CSV export', icon: History },
  { id: 'contacts', label: 'Contacts', desc: 'Saved beneficiaries', icon: Users, badge: 'contacts' },
];

export default function Dashboard({
  wallet,
  paymentMethodCount,
  contactCount,
  copied,
  onCopyId,
  onToggleFreeze,
  onNavigate,
  onCreateWallet,
}) {
  if (!wallet) {
    return (
      <div className="white-card empty-placeholder">
        <div className="empty-icon-box">
          <PlusCircle size={24} />
        </div>
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '6px' }}>
          No USD Wallet Found
        </h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '18px' }}>
          Create an active USD digital wallet to start transacting.
        </p>
        <button className="btn btn-primary" onClick={onCreateWallet}>
          <PlusCircle size={15} /> Create Wallet
        </button>
      </div>
    );
  }

  const moneyLocked = wallet.account_status !== 'ACTIVE';

  return (
    <>
      <WalletCard
        wallet={wallet}
        copied={copied}
        onCopyId={onCopyId}
        onToggleFreeze={onToggleFreeze}
      />

      {moneyLocked && (
        <div
          style={{
            padding: '10px 14px',
            marginBottom: '16px',
            borderRadius: 'var(--radius-xs)',
            background: 'var(--surface-alt)',
            border: '1px solid var(--border-color)',
            fontSize: '12px',
            color: 'var(--text-muted)',
          }}
        >
          Wallet is {wallet.account_status}. Freeze/unfreeze it above to enable money moves.
        </div>
      )}

      <div className="tile-grid">
        {TILES.map((tile) => {
          const Icon = tile.icon;
          const count = tile.badge === 'paymentMethods' ? paymentMethodCount : tile.badge === 'contacts' ? contactCount : 0;
          const disabled = (tile.id === 'transfer' || tile.id === 'topup' || tile.id === 'withdraw') && moneyLocked;
          return (
            <button
              key={tile.id}
              type="button"
              className="feature-tile"
              disabled={disabled}
              onClick={() => onNavigate(tile.id)}
            >
              <span className="tile-icon"><Icon size={19} /></span>
              <span className="tile-label">{tile.label}</span>
              <span className="tile-desc">{tile.desc}</span>
              {count > 0 && <span className="tile-badge">{count}</span>}
            </button>
          );
        })}
      </div>
    </>
  );
}
