import React from 'react';
import { CreditCard, Plus, Trash2 } from 'lucide-react';

export default function PaymentMethodsTab({
  paymentMethods,
  onOpenLinkCard,
  onRevokeCard,
}) {
  return (
    <div className="white-card">
      <div className="card-title-bar">
        <h3>
          <CreditCard size={17} color="var(--blue-primary)" />
          Payment Methods (PCI-DSS Tokenized)
        </h3>
        <button className="btn btn-primary btn-sm" onClick={onOpenLinkCard}>
          <Plus size={14} /> Link New Card
        </button>
      </div>

      {paymentMethods.length > 0 ? (
        <div className="cards-layout">
          {paymentMethods.map((pm) => (
            <div key={pm.id} className={`digital-card ${pm.is_default ? 'is-default' : ''}`}>
              <div className="digital-card-header">
                <span className="provider-pill">{pm.provider}</span>
                <div style={{ display: 'flex', gap: '6px' }}>
                  {pm.is_default && (
                    <span
                      style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        color: 'var(--blue-primary)',
                        background: 'var(--blue-light)',
                        padding: '2px 8px',
                        borderRadius: 'var(--radius-xs)',
                      }}
                    >
                      DEFAULT
                    </span>
                  )}
                  <span
                    style={{
                      fontSize: '11px',
                      fontWeight: 700,
                      color: 'var(--success)',
                      background: 'var(--success-bg)',
                      padding: '2px 8px',
                      borderRadius: 'var(--radius-xs)',
                    }}
                  >
                    {pm.status}
                  </span>
                </div>
              </div>

              <div>
                <div className="masked-digits">{pm.masked_number}</div>
                <div className="holder-text">{pm.holder_name}</div>
              </div>

              <div className="digital-card-footer">
                <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  {pm.method_type}
                </span>
                <button
                  className="btn btn-outline btn-sm"
                  style={{ padding: '3px 8px', color: 'var(--danger)' }}
                  onClick={() => onRevokeCard(pm.id)}
                  title="Revoke this card"
                >
                  <Trash2 size={13} /> Revoke
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-placeholder">
          <p>No payment methods connected yet.</p>
          <button
            className="btn btn-secondary btn-sm"
            style={{ marginTop: '12px' }}
            onClick={onOpenLinkCard}
          >
            <Plus size={14} /> Link First Card
          </button>
        </div>
      )}
    </div>
  );
}
