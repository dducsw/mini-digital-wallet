import React, { useState } from 'react';
import { Plus } from 'lucide-react';
import PageHeader from '../common/PageHeader';
import { formatCentsToUSD } from '../../utils/formatters';

export default function TopUpPage({
  wallet,
  paymentMethods = [],
  onSubmit,
  onNavigate,
}) {
  const [amount, setAmount] = useState('');
  const [pmId, setPmId] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    const ok = await onSubmit(amount, pmId || paymentMethods[0]?.id || null);
    if (ok) onNavigate('dashboard');
  };

  return (
    <div className="white-card" style={{ maxWidth: '560px' }}>
      <PageHeader
        title="Top Up"
        subtitle="Add USD to your wallet with a linked card"
        onBack={() => onNavigate('dashboard')}
      />

      {paymentMethods.length === 0 ? (
        <div className="empty-placeholder">
          <p>No cards linked yet.</p>
          <button
            className="btn btn-secondary btn-sm"
            style={{ marginTop: '12px' }}
            onClick={() => onNavigate('cards')}
          >
            Link a card first
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Amount (USD)</label>
            <input
              type="number"
              className="form-control"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              min="0.01"
              step="any"
              required
            />
            {wallet && (
              <p className="form-hint">Current balance: {formatCentsToUSD(wallet.available_balance)}</p>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">Source card</label>
            <select
              className="form-control"
              value={pmId || paymentMethods[0].id}
              onChange={(e) => setPmId(e.target.value)}
            >
              {paymentMethods.map((pm) => (
                <option key={pm.id} value={pm.id}>
                  {pm.provider} {pm.masked_number} ({pm.holder_name})
                </option>
              ))}
            </select>
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
            <Plus size={15} /> Confirm Top Up
          </button>
        </form>
      )}
    </div>
  );
}
