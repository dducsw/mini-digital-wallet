import React, { useEffect, useState } from 'react';
import { Download, Filter, RefreshCw, Undo2 } from 'lucide-react';
import PageHeader from '../common/PageHeader';
import { api, isUnauthorized, generateIdempotencyKey } from '../../services/api';
import { formatCentsToUSD } from '../../utils/formatters';

const PAGE_SIZE = 20;

export default function HistoryPage({
  wallet,
  token,
  showToast,
  onSessionExpired,
  onNavigate,
}) {
  const [entries, setEntries] = useState([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const [filterType, setFilterType] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const buildParams = () => ({
    type: filterType || null,
    from: fromDate ? new Date(fromDate).toISOString() : null,
    to: toDate ? new Date(toDate + 'T23:59:59Z').toISOString() : null,
  });

  const fetchPage = async (targetPage, params) => {
    if (!wallet?.id) return;
    setLoading(true);
    try {
      const res = await api.getStatement(
        wallet.id,
        { ...params, page: targetPage, size: PAGE_SIZE },
        token
      );
      if (isUnauthorized(res)) {
        onSessionExpired();
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setEntries(data.content || []);
        setTotalElements(data.totalElements ?? 0);
        setTotalPages(data.totalPages ?? 0);
      } else {
        showToast('error', 'Failed to load statement.');
      }
    } catch {
      showToast('error', 'Failed to load statement. Please check your connection.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (wallet?.id) fetchPage(page, buildParams());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wallet?.id, page, token]);

  const applyFilter = (e) => {
    e.preventDefault();
    setPage(0);
    fetchPage(0, buildParams());
  };

  const resetFilter = () => {
    setFilterType('');
    setFromDate('');
    setToDate('');
    setPage(0);
    fetchPage(0, { type: null, from: null, to: null });
  };

  const handleExport = async () => {
    try {
      const res = await api.exportStatementCsv(wallet.id, buildParams(), token);
      if (isUnauthorized(res)) {
        onSessionExpired();
        return;
      }
      if (res.ok) {
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `statement-${wallet.id}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
        showToast('success', 'Statement exported to CSV');
      } else {
        showToast('error', 'Failed to export CSV');
      }
    } catch {
      showToast('error', 'Failed to export CSV');
    }
  };

  // Reversal makes sense for P2P money the user SENT (own DEBIT entry).
  const reversible = (entry) =>
    entry.transaction_type === 'P2P_TRANSFER' &&
    entry.entry_type === 'DEBIT' &&
    entry.reference_id;

  const handleReversal = async (entry) => {
    if (!window.confirm(`Reverse transaction ${entry.reference_id}? The amount will be refunded to your wallet.`)) return;
    try {
      const res = await api.reverse(
        {
          originalTransactionId: entry.transaction_id,
          description: `Reversal of ${entry.reference_id}`,
        },
        generateIdempotencyKey(),
        token
      );
      if (isUnauthorized(res)) {
        onSessionExpired();
        return;
      }
      if (res.ok) {
        showToast('success', 'Transaction reversed successfully.');
        fetchPage(page, buildParams());
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Reversal failed.');
      }
    } catch {
      showToast('error', 'Reversal failed. Please try again.');
    }
  };

  const fromIdx = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const toIdx = Math.min(totalElements, (page + 1) * PAGE_SIZE);

  return (
    <div className="white-card">
      <PageHeader
        title="Transaction History"
        subtitle="Wallet statement (newest first)"
        onBack={() => onNavigate('dashboard')}
      />

      <form
        onSubmit={applyFilter}
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '10px',
          alignItems: 'center',
          padding: '12px',
          background: 'var(--surface-alt)',
          borderRadius: 'var(--radius-md)',
          marginBottom: '16px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Filter size={14} color="var(--text-muted)" />
          <span style={{ fontSize: '12px', fontWeight: 600 }}>Filters:</span>
        </div>

        <select
          className="form-control"
          style={{ width: 'auto', padding: '4px 8px', fontSize: '12px' }}
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
        >
          <option value="">All Types</option>
          <option value="CREDIT">CREDIT (+)</option>
          <option value="DEBIT">DEBIT (-)</option>
        </select>

        <input
          type="date"
          className="form-control"
          style={{ width: 'auto', padding: '4px 8px', fontSize: '12px' }}
          value={fromDate}
          onChange={(e) => setFromDate(e.target.value)}
          title="From date"
        />

        <input
          type="date"
          className="form-control"
          style={{ width: 'auto', padding: '4px 8px', fontSize: '12px' }}
          value={toDate}
          onChange={(e) => setToDate(e.target.value)}
          title="To date"
        />

        <button type="submit" className="btn btn-primary" style={{ padding: '5px 12px', fontSize: '12px' }}>
          Apply
        </button>

        {(filterType || fromDate || toDate) && (
          <button
            type="button"
            className="btn btn-outline"
            onClick={resetFilter}
            style={{ padding: '5px 10px', fontSize: '12px' }}
            title="Reset filters"
          >
            <RefreshCw size={12} />
          </button>
        )}

        <button
          type="button"
          className="btn btn-outline"
          onClick={handleExport}
          style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '5px 12px', fontSize: '12px', marginLeft: 'auto' }}
        >
          <Download size={14} />
          Export CSV
        </button>
      </form>

      {entries.length > 0 ? (
        <div className="soft-table-wrap">
          <table className="soft-table">
            <thead>
              <tr>
                <th style={{ width: '90px' }}>Type</th>
                <th>Transaction</th>
                <th>Amount</th>
                <th>Balance After</th>
                <th>Reference ID</th>
                <th>Date & Time</th>
                <th style={{ width: '60px' }}></th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id}>
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
                    <div style={{ fontSize: '12px' }}>{entry.transaction_type || '—'}</div>
                    {entry.description && (
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{entry.description}</div>
                    )}
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
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }} title={entry.reference_id || ''}>
                    {entry.reference_id || '—'}
                  </td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>
                    {entry.created_at ? new Date(entry.created_at).toLocaleString('en-US') : '—'}
                  </td>
                  <td>
                    {reversible(entry) && (
                      <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        style={{ padding: '2px 6px', fontSize: '11px', color: 'var(--danger)' }}
                        title="Reverse this transaction"
                        onClick={() => handleReversal(entry)}
                      >
                        <Undo2 size={12} /> Reversal
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-placeholder">
          <p>{loading ? 'Loading statement…' : 'No transactions in this wallet yet.'}</p>
        </div>
      )}

      {totalPages > 1 && (
        <div className="pagination-bar">
          <span className="page-note">
            Showing {fromIdx}–{toIdx} of {totalElements} entries
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              disabled={page === 0 || loading}
              onClick={() => setPage(page - 1)}
            >
              Prev
            </button>
            <span className="page-note">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              disabled={page + 1 >= totalPages || loading}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
