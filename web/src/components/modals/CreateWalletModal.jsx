import React from 'react';

export default function CreateWalletModal({ currentUser, onClose, onCreateWallet }) {
  const handleSubmit = (e) => {
    e.preventDefault();
    onCreateWallet();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">Create USD Wallet</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Owner ID</label>
            <input
              type="text"
              className="form-control"
              value={currentUser?.userId || currentUser?.username}
              readOnly
              disabled
            />
          </div>

          <div className="form-group">
            <label className="form-label">Currency</label>
            <input
              type="text"
              className="form-control"
              value="USD (Cents - Minor Units)"
              readOnly
              disabled
            />
          </div>

          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '20px' }}>
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Create Wallet Now
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
