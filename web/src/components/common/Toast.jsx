import React from 'react';
import { CheckCircle2, AlertCircle } from 'lucide-react';

export default function Toast({ toast, onClose }) {
  if (!toast) return null;

  return (
    <div className={`toast-banner toast-${toast.type}`}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        {toast.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
        <span>{toast.message}</span>
      </div>
      <button
        onClick={onClose}
        style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
        aria-label="Close notification"
      >
        ×
      </button>
    </div>
  );
}
