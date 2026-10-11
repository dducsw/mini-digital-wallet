import React from 'react';

export default function ProfileModal({ currentUser, onClose }) {
  if (!currentUser) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">User Profile</h3>
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

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn-outline" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
