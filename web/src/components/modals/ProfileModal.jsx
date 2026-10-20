import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { api } from '../../services/api';

export default function ProfileModal({ currentUser, token, showToast, onClose }) {
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [loading, setLoading] = useState(false);

  if (!currentUser) return null;

  const handleSetPin = async (e) => {
    e.preventDefault();
    if (!/^[0-9]{6}$/.test(pin)) {
      showToast?.('error', 'PIN must be exactly 6 digits (0-9)');
      return;
    }
    if (pin !== confirmPin) {
      showToast?.('error', 'Confirmation PIN does not match');
      return;
    }

    setLoading(true);
    try {
      const res = await api.setPin(pin, token);
      if (res.ok) {
        showToast?.('success', 'Transaction PIN updated successfully!');
        setPin('');
        setConfirmPin('');
      } else {
        const err = await res.json().catch(() => ({}));
        showToast?.('error', err.detail || 'Failed to update PIN');
      }
    } catch {
      showToast?.('error', 'Failed to update PIN. Please check your connection.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">User Profile & Security</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
            ×
          </button>
        </div>

        <div className="profile-info-grid">
          <div className="profile-info-item">
            <div className="profile-info-label">Full Name</div>
            <div className="profile-info-val">{currentUser.fullName || currentUser.username}</div>
          </div>

          <div className="profile-info-item">
            <div className="profile-info-label">Username</div>
            <div className="profile-info-val">@{currentUser.username}</div>
          </div>

          <div className="profile-info-item">
            <div className="profile-info-label">Email Address</div>
            <div className="profile-info-val">{currentUser.email || 'N/A'}</div>
          </div>

          <div className="profile-info-item">
            <div className="profile-info-label">Owner ID</div>
            <div className="profile-info-val" style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>
              {currentUser.userId || currentUser.username}
            </div>
          </div>
        </div>

        {/* Security & PIN Section */}
        <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--border-color)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <ShieldCheck size={18} color="var(--blue-primary)" />
            <h4 style={{ fontSize: '14px', fontWeight: 600 }}>Transaction PIN (6 digits)</h4>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>
            Protects withdrawals and transfers. Once set, PIN is required for money movement.
          </p>

          <form onSubmit={handleSetPin}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '12px' }}>
              <input
                type="password"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                className="form-control"
                placeholder="New 6-digit PIN"
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
                required
              />
              <input
                type="password"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                className="form-control"
                placeholder="Confirm 6-digit PIN"
                value={confirmPin}
                onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-secondary"
              style={{ width: '100%', fontSize: '13px', padding: '7px 12px' }}
              disabled={loading || pin.length !== 6 || confirmPin.length !== 6}
            >
              {loading ? 'Updating...' : 'Set / Update Transaction PIN'}
            </button>
          </form>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '20px' }}>
          <button className="btn btn-outline" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
