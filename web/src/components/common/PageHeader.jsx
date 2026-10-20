import React from 'react';
import { ArrowLeft } from 'lucide-react';

export default function PageHeader({ title, subtitle, onBack }) {
  return (
    <div className="page-header">
      {onBack && (
        <button type="button" className="back-btn" onClick={onBack} aria-label="Back to dashboard">
          <ArrowLeft size={16} />
        </button>
      )}
      <div>
        <div className="page-title">{title}</div>
        {subtitle && <div className="page-subtitle">{subtitle}</div>}
      </div>
    </div>
  );
}
