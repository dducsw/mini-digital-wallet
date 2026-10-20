import React, { useState } from 'react';
import { Minus } from 'lucide-react';
import PageHeader from '../common/PageHeader';
import { formatCentsToUSD } from '../../utils/formatters';

export default function WithdrawPage({
  wallet,
  paymentMethods = [],
  onSubmit,
  onNavigate,
}) {
  const [amount, setAmount] = useState('');
  const [pmId, setPmId] = useState('');
  const [pin, setPin] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    const ok = await onSubmit(amount, pmId || paymentMethods[0]?.id || null, pin || null);
    if (ok) onNavigate('dashboard');
  };

  return (
    <div className="white-card" style={{ maxWidth: '560px' }}>
      <PageHeader
        title="Withdraw"
        subtitle="Withdraw USD to a linked card"
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
              <p className="form-hint">Available balance: {formatCentsToUSD(wallet.available_balance)}</p>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">Destination card</label>
            <select
              className="form-control"
              value={pmId || paymentMethods[0].id}
              onChange={(e) => setPmId(e.target.value)}
            >
              {paymentMethods.map((pm) => (
                <option key={pm.id} value={pm.id}>
                  {pm.provider} {pm.masked_number}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Transaction PIN</label>
            <input
              type="password"
              inputMode="numeric"
              maxLength={6}
              className="form-control"
              placeholder="6 digits (required if PIN is set)"
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
            />
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
            <Minus size={15} /> Confirm Withdraw
          </button>
        </form>
      )}
    </div>
  );
}
