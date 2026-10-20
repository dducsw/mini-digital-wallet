import React, { useState } from 'react';

export default function AuthPage({ onLogin, onRegister, toast, onCloseToast }) {
  const [authMode, setAuthMode] = useState('login');
  const [authForm, setAuthForm] = useState({
    username: '',
    password: '',
    email: '',
    fullName: '',
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (authMode === 'login') {
      onLogin(authForm.username, authForm.password);
    } else {
      onRegister(authForm);
    }
  };

  return (
    <div className="auth-page-container">
      {toast && (
        <div className={`toast-banner toast-${toast.type}`} style={{ width: '100%', maxWidth: '400px' }}>
          <span>{toast.message}</span>
          <button
            onClick={onCloseToast}
            style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
            aria-label="Close"
          >
            ×
          </button>
        </div>
      )}

      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Mini Digital Wallet</h1>
          <p className="auth-subtitle">Secure Double-Entry Ledger Core</p>
        </div>

        <div className="auth-tab-switch">
          <button
            type="button"
            className={`auth-tab-btn ${authMode === 'login' ? 'active' : ''}`}
            onClick={() => setAuthMode('login')}
          >
            Sign In
          </button>
          <button
            type="button"
            className={`auth-tab-btn ${authMode === 'register' ? 'active' : ''}`}
            onClick={() => setAuthMode('register')}
          >
            Create Account
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {authMode === 'register' && (
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input
                type="text"
                className="form-control"
                placeholder="Alice Tran"
                value={authForm.fullName}
                onChange={(e) => setAuthForm({ ...authForm, fullName: e.target.value })}
              />
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              type="text"
              className="form-control"
              placeholder={authMode === 'login' ? 'Enter your username' : 'alicetran'}
              value={authForm.username}
              onChange={(e) => setAuthForm({ ...authForm, username: e.target.value })}
              required
            />
          </div>

          {authMode === 'register' && (
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                type="email"
                className="form-control"
                placeholder="alice@wallet.io"
                value={authForm.email}
                onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })}
                required
              />
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Password {authMode === 'register' ? '(min 6 characters)' : ''}</label>
            <input
              type="password"
              className="form-control"
              placeholder="••••••••"
              value={authForm.password}
              onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '10px' }}>
            {authMode === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  );
}
