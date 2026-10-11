import React, { useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, ArrowRightLeft } from 'lucide-react';

export default function TransfersTab({
  wallet,
  paymentMethods,
  onTopUp,
  onWithdraw,
  onP2pTransfer,
}) {
  const [topUpAmount, setTopUpAmount] = useState('50');
  const [topUpPmId, setTopUpPmId] = useState(paymentMethods[0]?.id || '');

  const [withdrawAmount, setWithdrawAmount] = useState('25');
  const [withdrawPmId, setWithdrawPmId] = useState(paymentMethods[0]?.id || '');

  const [p2pAmount, setP2pAmount] = useState('15');
  const [p2pDestWalletId, setP2pDestWalletId] = useState('');

  // paymentMethods load async after mount, so the initial '' state may be
  // stale; every read falls back to the first card (see value= and submit).
  const handleTopUpSubmit = (e) => {
    e.preventDefault();
    onTopUp(topUpAmount, topUpPmId || paymentMethods[0]?.id);
  };

  const handleWithdrawSubmit = (e) => {
    e.preventDefault();
    onWithdraw(withdrawAmount, withdrawPmId || paymentMethods[0]?.id);
  };

  const handleP2pSubmit = (e) => {
    e.preventDefault();
    onP2pTransfer(p2pAmount, p2pDestWalletId);
  };

  return (
    <div className="transfers-grid">
      {/* Box 1: Top-up */}
      <div className="action-box">
        <div>
          <div className="action-icon-box">
            <ArrowDownLeft size={18} />
          </div>
          <h4 className="action-box-title">Top Up Wallet</h4>
          <p className="action-box-desc">Add USD funds using a linked payment card</p>

          <form onSubmit={handleTopUpSubmit}>
            <div className="form-group">
              <label className="form-label">Amount (USD)</label>
              <input
                type="number"
                className="form-control"
                value={topUpAmount}
                onChange={(e) => setTopUpAmount(e.target.value)}
                min="1"
                step="any"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Source Card</label>
              {paymentMethods.length > 0 ? (
                <select
                  className="form-control"
                  value={topUpPmId || paymentMethods[0]?.id || ''}
                  onChange={(e) => setTopUpPmId(e.target.value)}
                >
                  {paymentMethods.map((pm) => (
                    <option key={pm.id} value={pm.id}>
                      {pm.provider} {pm.masked_number} ({pm.holder_name})
                    </option>
                  ))}
                </select>
              ) : (
                <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  No cards linked. Please add a card first.
                </p>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', marginTop: '4px' }}
              disabled={!wallet || wallet.status !== 'ACTIVE' || paymentMethods.length === 0}
            >
              Confirm Top Up
            </button>
          </form>
        </div>
      </div>

      {/* Box 2: Withdraw */}
      <div className="action-box">
        <div>
          <div className="action-icon-box">
            <ArrowUpRight size={18} />
          </div>
          <h4 className="action-box-title">Withdraw Funds</h4>
          <p className="action-box-desc">Withdraw balance to your linked card account</p>

          <form onSubmit={handleWithdrawSubmit}>
            <div className="form-group">
              <label className="form-label">Amount (USD)</label>
              <input
                type="number"
                className="form-control"
                value={withdrawAmount}
                onChange={(e) => setWithdrawAmount(e.target.value)}
                min="1"
                step="any"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Destination Card</label>
              {paymentMethods.length > 0 ? (
                <select
                  className="form-control"
                  value={withdrawPmId || paymentMethods[0]?.id || ''}
                  onChange={(e) => setWithdrawPmId(e.target.value)}
                >
                  {paymentMethods.map((pm) => (
                    <option key={pm.id} value={pm.id}>
                      {pm.provider} {pm.masked_number}
                    </option>
                  ))}
                </select>
              ) : (
                <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  No cards linked.
                </p>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-secondary"
              style={{ width: '100%', marginTop: '4px' }}
              disabled={!wallet || wallet.status !== 'ACTIVE' || paymentMethods.length === 0}
            >
              Confirm Withdraw
            </button>
          </form>
        </div>
      </div>

      {/* Box 3: P2P Transfer */}
      <div className="action-box">
        <div>
          <div className="action-icon-box">
            <ArrowRightLeft size={18} />
          </div>
          <h4 className="action-box-title">P2P Transfer</h4>
          <p className="action-box-desc">Send USD directly to another user's wallet</p>

          <form onSubmit={handleP2pSubmit}>
            <div className="form-group">
              <label className="form-label">Amount (USD)</label>
              <input
                type="number"
                className="form-control"
                value={p2pAmount}
                onChange={(e) => setP2pAmount(e.target.value)}
                min="1"
                step="any"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Recipient Wallet ID</label>
              <input
                type="text"
                className="form-control"
                placeholder="0191fa30-9b48-732a-bc91..."
                value={p2pDestWalletId}
                onChange={(e) => setP2pDestWalletId(e.target.value)}
                required
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', marginTop: '4px' }}
              disabled={!wallet || wallet.status !== 'ACTIVE'}
            >
              Send Money
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
