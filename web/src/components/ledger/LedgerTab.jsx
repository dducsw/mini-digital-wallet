import React from 'react';
import { History } from 'lucide-react';
import { formatCentsToUSD } from '../../utils/formatters';

export default function LedgerTab({ ledgerEntries }) {
  return (
    <div className="white-card">
      <div className="card-title-bar">
        <h3>
          <History size={17} color="var(--blue-primary)" />
          Activity & Balance History
        </h3>
        <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
          {ledgerEntries.length} transactions recorded
        </span>
      </div>

      {ledgerEntries.length > 0 ? (
        <div className="soft-table-wrap">
          <table className="soft-table">
            <thead>
              <tr>
                <th style={{ width: '60px' }}>#</th>
                <th style={{ width: '120px' }}>Type</th>
                <th>Amount</th>
                <th>Balance After</th>
                <th>Reference ID</th>
                <th>Date & Time</th>
              </tr>
            </thead>
            <tbody>
              {ledgerEntries.map((entry, idx) => (
                <tr key={entry.id || idx}>
                  <td style={{ fontWeight: 600, color: 'var(--text-muted)' }}>
                    #{entry.line_no !== undefined ? entry.line_no : idx + 1}
                  </td>
                  <td>
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: 'var(--radius-xs)',
                        fontSize: '11px',
                        fontWeight: 700,
                        background: entry.entry_type === 'CREDIT' ? 'var(--success-bg)' : 'var(--blue-light)',
                        color: entry.entry_type === 'CREDIT' ? 'var(--success)' : 'var(--blue-primary)',
                      }}
                    >
                      {entry.entry_type}
                    </span>
                  </td>
                  <td>
                    <span className={entry.entry_type === 'CREDIT' ? 'amount-plus' : 'amount-minus'}>
                      {entry.entry_type === 'CREDIT' ? '+' : '-'}
                      {formatCentsToUSD(entry.amount)}
                    </span>
                  </td>
                  <td style={{ fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                    {formatCentsToUSD(entry.balance_after)}
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }} title={String(entry.id ?? '')}>
                    {entry.id ? `${String(entry.id).slice(0, 14)}...` : 'N/A'}
                  </td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>
                    {entry.created_at ? new Date(entry.created_at).toLocaleString('en-US') : 'N/A'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-placeholder">
          <p>No transaction history recorded for this wallet yet.</p>
        </div>
      )}
    </div>
  );
}
