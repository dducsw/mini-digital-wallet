import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';

export default function LinkCardModal({ onClose, onLinkCard }) {
  const [cardForm, setCardForm] = useState({
    method_type: 'DEBIT_CARD',
    provider: 'VISA',
    card_number: '4242424242424242',
    holder_name: 'ALICE TRAN',
    is_default: true,
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    // Backend only accepts 12-19 digits; strip spaces users naturally type.
    onLinkCard({ ...cardForm, card_number: cardForm.card_number.replace(/\s/g, '') });
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">Link New Card</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Payment Method Type</label>
            <select
              className="form-control"
              value={cardForm.method_type}
              onChange={(e) => setCardForm({ ...cardForm, method_type: e.target.value })}
            >
              <option value="DEBIT_CARD">Debit Card</option>
              <option value="CREDIT_CARD">Credit Card</option>
              <option value="BANK_ACCOUNT">Bank Account</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Card Provider</label>
            <select
              className="form-control"
              value={cardForm.provider}
              onChange={(e) => setCardForm({ ...cardForm, provider: e.target.value })}
            >
              <option value="VISA">Visa</option>
              <option value="MASTERCARD">Mastercard</option>
              <option value="NAPAS">Napas</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Card Number (12-19 digits)</label>
            <input
              type="text"
              className="form-control"
              placeholder="4242 4242 4242 4242"
              value={cardForm.card_number}
              onChange={(e) => setCardForm({ ...cardForm, card_number: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Cardholder Name</label>
            <input
              type="text"
              className="form-control"
              placeholder="ALICE TRAN"
              value={cardForm.holder_name}
              onChange={(e) => setCardForm({ ...cardForm, holder_name: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-checkbox-wrap">
              <input
                type="checkbox"
                checked={cardForm.is_default}
                onChange={(e) => setCardForm({ ...cardForm, is_default: e.target.checked })}
              />
              Set as default payment method
            </label>
          </div>

          <div
            style={{
              background: 'var(--blue-light)',
              border: '1px solid var(--blue-border)',
              borderRadius: 'var(--radius-xs)',
              padding: '10px 12px',
              fontSize: '12px',
              color: 'var(--blue-navy)',
              marginBottom: '18px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <ShieldCheck size={16} color="var(--blue-primary)" />
            <span>PCI-DSS compliant: Tokenized representation only.</span>
          </div>

          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Confirm Card
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
