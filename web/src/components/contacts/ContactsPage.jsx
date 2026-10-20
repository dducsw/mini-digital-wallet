import React, { useState } from 'react';
import { Plus, Trash2, Star } from 'lucide-react';
import PageHeader from '../common/PageHeader';
import { formatAccountInput } from '../../utils/formatters';

export default function ContactsPage({
  contacts = [],
  onAdd,
  onDelete,
  onToggleFavorite,
  onPick,
  onNavigate,
}) {
  const [accountNumber, setAccountNumber] = useState('');
  const [nickname, setNickname] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    const digits = accountNumber.replace(/\s+/g, '');
    if (!/^[0-9]{12}$/.test(digits) || !nickname.trim()) return;
    onAdd({ accountNumber: digits, nickname: nickname.trim() });
    setAccountNumber('');
    setNickname('');
  };

  const sorted = [...contacts].sort((a, b) => (b.is_favorite ? 1 : 0) - (a.is_favorite ? 1 : 0));

  return (
    <div className="white-card" style={{ maxWidth: '620px' }}>
      <PageHeader
        title="Contacts"
        subtitle="Saved beneficiaries — click one to start a transfer"
        onBack={() => onNavigate('dashboard')}
      />

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '8px', marginBottom: '18px', flexWrap: 'wrap' }}>
        <input
          type="text"
          inputMode="numeric"
          className="form-control"
          style={{ flex: 1, minWidth: '170px' }}
          placeholder="8820 1234 5678"
          value={accountNumber}
          onChange={(e) => setAccountNumber(formatAccountInput(e.target.value))}
          maxLength={14}
          required
        />
        <input
          type="text"
          className="form-control"
          style={{ flex: 1, minWidth: '130px' }}
          placeholder="Nickname"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          required
        />
        <button type="submit" className="btn btn-primary">
          <Plus size={14} /> Save
        </button>
      </form>

      {sorted.length > 0 ? (
        <div>
          {sorted.map((c) => (
            <div
              key={c.id}
              className="contact-row"
              onClick={() => onPick?.(c.account_number)}
              title="Click to transfer to this account"
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span className="contact-avatar">{c.nickname.charAt(0).toUpperCase()}</span>
                <div>
                  <div style={{ fontSize: '13px', fontWeight: 700 }}>{c.nickname}</div>
                  <div style={{ fontSize: '12px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                    {c.account_number}
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  type="button"
                  className="icon-btn"
                  aria-label="Toggle favorite"
                  style={c.is_favorite ? { color: 'var(--blue-primary)' } : undefined}
                  onClick={(e) => {
                    e.stopPropagation();
                    onToggleFavorite(c.id);
                  }}
                >
                  <Star size={15} fill={c.is_favorite ? 'currentColor' : 'none'} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  style={{ color: 'var(--danger)' }}
                  aria-label="Delete contact"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(c.id, c.nickname);
                  }}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-placeholder">
          <p>No saved contacts yet. Save an account number for faster transfers.</p>
        </div>
      )}
    </div>
  );
}
