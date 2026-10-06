import React, { useState, useEffect } from 'react';
import {
  Wallet,
  CreditCard,
  PlusCircle,
  ShieldCheck,
  RefreshCw,
  Trash2,
  Lock,
  Unlock,
  CheckCircle2,
  AlertCircle,
  Copy,
  Check,
  User,
  ExternalLink
} from 'lucide-react';

const formatCentsToUSD = (cents) => {
  if (cents === null || cents === undefined) return '$0.00';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(cents / 100);
};

export default function App() {
  const [currentOwner, setCurrentOwner] = useState('USER_987654');
  const [wallet, setWallet] = useState(null);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState(null);
  const [copied, setCopied] = useState(false);

  // Modals
  const [showLinkCardModal, setShowLinkCardModal] = useState(false);
  const [showCreateWalletModal, setShowCreateWalletModal] = useState(false);

  // Form states
  const [cardForm, setCardForm] = useState({
    method_type: 'DEBIT_CARD',
    provider: 'VISA',
    card_number: '4242424242424242',
    holder_name: 'NGUYEN VAN A',
    is_default: true,
  });

  const showToast = (type, message) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 4000);
  };

  const copyWalletId = (id) => {
    if (!id) return;
    navigator.clipboard.writeText(id);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Fetch Wallet & Payment Methods for current user
  const loadData = async (ownerId = currentOwner) => {
    setLoading(true);
    try {
      // 1. Fetch wallet
      const walletRes = await fetch(`/api/v1/wallets?owner_id=${ownerId}`);
      if (walletRes.ok) {
        const wallets = await walletRes.json();
        if (wallets && wallets.length > 0) {
          setWallet(wallets[0]);
        } else {
          setWallet(null);
        }
      } else {
        setWallet(null);
      }

      // 2. Fetch payment methods
      const pmRes = await fetch(`/api/v1/payment-methods?owner_id=${ownerId}`);
      if (pmRes.ok) {
        const pms = await pmRes.json();
        setPaymentMethods(pms);
      } else {
        setPaymentMethods([]);
      }
    } catch {
      // Mock fallback if backend is offline so UI is immediately interactive
      setWallet({
        id: '0191fa30-9b48-732a-bc91-23588921a100',
        owner_id: ownerId,
        account_type: 'USER',
        status: 'ACTIVE',
        available_balance: 50000, // $500.00
        ledger_balance: 50000,
        currency: 'USD',
        version: 1,
      });
      setPaymentMethods([
        {
          id: 'pm-01-mock',
          method_type: 'DEBIT_CARD',
          provider: 'VISA',
          masked_number: '**** **** **** 4242',
          holder_name: 'NGUYEN VAN A',
          is_default: true,
          status: 'ACTIVE',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData(currentOwner);
  }, [currentOwner]);

  // Handle Create Wallet
  const handleCreateWallet = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch('/api/v1/wallets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          owner_id: currentOwner,
          currency: 'USD',
          wallet_type: 'USER',
        }),
      });

      if (res.ok) {
        const newWallet = await res.json();
        setWallet(newWallet);
        setShowCreateWalletModal(false);
        showToast('success', 'Mở ví thanh toán USD thành công!');
      } else {
        const err = await res.json();
        showToast('error', err.detail || 'Không thể tạo ví');
      }
    } catch {
      // Fallback
      setWallet({
        id: 'wal-new-mock-' + Date.now(),
        owner_id: currentOwner,
        account_type: 'USER',
        status: 'ACTIVE',
        available_balance: 0,
        ledger_balance: 0,
        currency: 'USD',
        version: 0,
      });
      setShowCreateWalletModal(false);
      showToast('success', 'Mở ví thanh toán thành công (Chế độ mô phỏng)');
    }
  };

  // Handle Link Card
  const handleLinkCard = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch('/api/v1/payment-methods', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          owner_id: currentOwner,
          method_type: cardForm.method_type,
          provider: cardForm.provider,
          card_number: cardForm.card_number,
          holder_name: cardForm.holder_name,
          is_default: cardForm.is_default,
        }),
      });

      if (res.ok) {
        setShowLinkCardModal(false);
        showToast('success', 'Liên kết thẻ thành công (Token hóa an toàn)');
        loadData(currentOwner);
      } else {
        const err = await res.json();
        showToast('error', err.detail || 'Lỗi liên kết thẻ');
      }
    } catch {
      // Fallback
      const newCard = {
        id: 'pm-' + Date.now(),
        method_type: cardForm.method_type,
        provider: cardForm.provider,
        masked_number: '**** **** **** ' + cardForm.card_number.slice(-4),
        holder_name: cardForm.holder_name.toUpperCase(),
        is_default: cardForm.is_default,
        status: 'ACTIVE',
      };
      setPaymentMethods((prev) => [newCard, ...prev]);
      setShowLinkCardModal(false);
      showToast('success', 'Liên kết thẻ thành công (Chế độ mô phỏng)');
    }
  };

  // Handle Revoke Card
  const handleRevokeCard = async (id) => {
    if (!window.confirm('Bạn có chắc muốn hủy liên kết phương thức này?')) return;
    try {
      const res = await fetch(`/api/v1/payment-methods/${id}`, {
        method: 'DELETE',
      });
      if (res.ok || res.status === 204) {
        showToast('success', 'Đã hủy liên kết thẻ thành công');
        loadData(currentOwner);
      } else {
        showToast('error', 'Không thể hủy thẻ');
      }
    } catch {
      setPaymentMethods((prev) => prev.filter((item) => item.id !== id));
      showToast('success', 'Đã hủy thẻ (Chế độ mô phỏng)');
    }
  };

  // Toggle Wallet Freeze / Unfreeze
  const handleToggleFreeze = async () => {
    if (!wallet) return;
    const targetStatus = wallet.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
      const res = await fetch(`/api/v1/wallets/${wallet.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: targetStatus }),
      });
      if (res.ok) {
        const updated = await res.json();
        setWallet(updated);
        showToast(
          'success',
          targetStatus === 'FROZEN' ? 'Đã đóng băng ví an toàn' : 'Đã mở khóa ví hoạt động'
        );
      } else {
        const err = await res.json();
        showToast('error', err.detail || 'Không thể đổi trạng thái');
      }
    } catch {
      setWallet((prev) => ({ ...prev, status: targetStatus }));
      showToast(
        'success',
        targetStatus === 'FROZEN' ? 'Đã đóng băng ví (Mô phỏng)' : 'Đã mở khóa ví (Mô phỏng)'
      );
    }
  };

  return (
    <div className="app-container">
      {/* Top Navbar */}
      <header className="top-navbar">
        <div className="nav-content">
          <div className="brand-area">
            <div className="brand-icon">
              <Wallet size={20} strokeWidth={2.4} />
            </div>
            <div>
              <div className="brand-title">Mini Digital Wallet</div>
              <div className="brand-subtitle">Core Banking Ledger Architecture</div>
            </div>
          </div>

          <div className="user-selector-area">
            <div className="user-chip">
              <User size={16} color="#1d4ed8" />
              <span>Chủ ví:</span>
              <select
                value={currentOwner}
                onChange={(e) => setCurrentOwner(e.target.value)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  fontWeight: 600,
                  color: 'inherit',
                  cursor: 'pointer',
                  outline: 'none',
                }}
              >
                <option value="USER_987654">USER_987654 (Nguyễn Văn A)</option>
                <option value="USER_ALICE">USER_ALICE (Alice Tran)</option>
                <option value="USER_BOB">USER_BOB (Bob Le)</option>
              </select>
            </div>

            <button
              className="btn btn-secondary btn-sm"
              onClick={() => loadData(currentOwner)}
              title="Làm mới dữ liệu"
            >
              <RefreshCw size={14} className={loading ? 'spin' : ''} />
              Làm mới
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="main-content">
        {/* Toast Feedback */}
        {toast && (
          <div className={`toast-bar toast-${toast.type}`}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              {toast.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
              <span>{toast.message}</span>
            </div>
            <button
              onClick={() => setToast(null)}
              style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
            >
              ×
            </button>
          </div>
        )}

        {/* Balance Grid Section */}
        {wallet ? (
          <section className="balance-grid">
            {/* Card 1: Available Balance */}
            <div className="card card-primary">
              <div className="card-label">
                <span>Số Dư Khả Dụng (Available)</span>
                <span className="badge badge-active" style={{ background: 'rgba(255,255,255,0.2)', color: '#fff', border: 'none' }}>
                  {wallet.currency || 'USD'}
                </span>
              </div>
              <div className="balance-amount">{formatCentsToUSD(wallet.available_balance)}</div>
              <div className="card-subtext">
                Tức thời O(1) Snapshot Projection • Minor Units ({wallet.available_balance?.toLocaleString()} cents)
              </div>

              <div className="actions-bar">
                <button
                  className="btn btn-outline-white"
                  onClick={handleToggleFreeze}
                >
                  {wallet.status === 'ACTIVE' ? (
                    <>
                      <Lock size={15} /> Khóa Ví (Freeze)
                    </>
                  ) : (
                    <>
                      <Unlock size={15} /> Mở Khóa Ví
                    </>
                  )}
                </button>
                <button
                  className="btn btn-outline-white"
                  onClick={() => setShowLinkCardModal(true)}
                >
                  <CreditCard size={15} /> Liên kết thẻ
                </button>
              </div>
            </div>

            {/* Card 2: Ledger Balance */}
            <div className="card">
              <div className="card-label">
                <span>Số Dư Sổ Cái (Ledger)</span>
                <ShieldCheck size={16} color="#1d4ed8" />
              </div>
              <div className="balance-amount" style={{ color: '#0f172a' }}>
                {formatCentsToUSD(wallet.ledger_balance)}
              </div>
              <div className="card-subtext">
                Số dư kế toán kép xác nhận từ bảng <code>ledger_entries</code>.
              </div>
            </div>

            {/* Card 3: Account Status & Metadata */}
            <div className="card">
              <div className="card-label">
                <span>Thông Tin Định Danh</span>
                <span
                  className={`badge ${
                    wallet.status === 'ACTIVE' ? 'badge-active' : 'badge-frozen'
                  }`}
                >
                  {wallet.status || 'ACTIVE'}
                </span>
              </div>
              
              <div style={{ marginTop: '0.75rem' }}>
                <div style={{ fontSize: '0.75rem', color: '#64748b' }}>Mã ví (Wallet UUID)</div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                    fontSize: '0.85rem',
                    fontWeight: 600,
                    marginTop: '0.2rem',
                    fontFamily: 'monospace',
                  }}
                >
                  <span title={wallet.id}>
                    {wallet.id?.slice(0, 14)}...
                  </span>
                  <button
                    onClick={() => copyWalletId(wallet.id)}
                    className="btn btn-secondary btn-sm"
                    style={{ padding: '0.2rem 0.4rem' }}
                    title="Sao chép ID"
                  >
                    {copied ? <Check size={12} color="#16a34a" /> : <Copy size={12} />}
                  </button>
                </div>

                <div style={{ marginTop: '0.85rem', fontSize: '0.8125rem', color: '#475569' }}>
                  Loại ví: <strong>{wallet.account_type || wallet.wallet_type || 'USER'}</strong>
                  <br />
                  Phiên bản lock: <strong>v{wallet.version || 0}</strong>
                </div>
              </div>
            </div>
          </section>
        ) : (
          /* Empty Wallet State */
          <div className="card" style={{ textAlign: 'center', padding: '3rem 1.5rem', marginBottom: '2rem' }}>
            <Wallet size={48} color="#94a3b8" style={{ margin: '0 auto 1rem' }} />
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem' }}>
              Chưa tìm thấy ví thanh toán cho {currentOwner}
            </h3>
            <p style={{ color: '#64748b', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
              Người dùng này chưa có ví tiền tệ USD. Mở ví ngay để bắt đầu giao dịch.
            </p>
            <button className="btn btn-primary" onClick={() => setShowCreateWalletModal(true)}>
              <PlusCircle size={16} /> Mở Ví Mới Ngay
            </button>
          </div>
        )}

        {/* Linked Cards Section */}
        <section>
          <div className="section-title-bar">
            <h2 className="section-title">
              <CreditCard size={20} color="#1d4ed8" />
              Thẻ & Phương Thức Thanh Toán (PCI-DSS Tokenized)
            </h2>
            <button className="btn btn-primary btn-sm" onClick={() => setShowLinkCardModal(true)}>
              <PlusCircle size={15} /> Thêm Thẻ Mới
            </button>
          </div>

          {paymentMethods.length > 0 ? (
            <div className="cards-grid">
              {paymentMethods.map((pm) => (
                <div
                  key={pm.id}
                  className={`payment-card-item ${pm.is_default ? 'is-default' : ''}`}
                >
                  <div className="payment-card-header">
                    <span className="card-provider-pill">{pm.provider}</span>
                    <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                      {pm.is_default && <span className="badge badge-default">Mặc định</span>}
                      <span className="badge badge-active">{pm.status}</span>
                    </div>
                  </div>

                  <div>
                    <div className="masked-number-display">{pm.masked_number}</div>
                    <div className="cardholder-name">{pm.holder_name}</div>
                  </div>

                  <div className="card-footer">
                    <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                      {pm.method_type}
                    </span>
                    <button
                      className="btn btn-danger-outline btn-sm"
                      onClick={() => handleRevokeCard(pm.id)}
                      title="Hủy liên kết thẻ"
                    >
                      <Trash2 size={13} /> Hủy thẻ
                    </button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="card" style={{ textAlign: 'center', padding: '2rem 1.5rem', color: '#64748b' }}>
              <CreditCard size={32} color="#cbd5e1" style={{ margin: '0 auto 0.75rem' }} />
              <p style={{ fontSize: '0.875rem' }}>
                Chưa có phương thức thanh toán nào liên kết. Nhấn <strong>Thêm Thẻ Mới</strong> để nạp tiền vào ví.
              </p>
            </div>
          )}
        </section>
      </main>

      {/* Modal: Link Card */}
      {showLinkCardModal && (
        <div className="modal-overlay" onClick={() => setShowLinkCardModal(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Liên Kết Thẻ Thanh Toán Mới</h3>
              <button className="close-btn" onClick={() => setShowLinkCardModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleLinkCard}>
              <div className="form-group">
                <label className="form-label">Loại Thẻ / Tài Khoản</label>
                <select
                  className="form-select"
                  value={cardForm.method_type}
                  onChange={(e) => setCardForm({ ...cardForm, method_type: e.target.value })}
                >
                  <option value="DEBIT_CARD">Thẻ Ghi Nợ (Debit Card)</option>
                  <option value="CREDIT_CARD">Thẻ Tín Dụng (Credit Card)</option>
                  <option value="BANK_ACCOUNT">Tài Khoản Ngân Hàng</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Tổ Chức Phát Hành</label>
                <select
                  className="form-select"
                  value={cardForm.provider}
                  onChange={(e) => setCardForm({ ...cardForm, provider: e.target.value })}
                >
                  <option value="VISA">Visa</option>
                  <option value="MASTERCARD">Mastercard</option>
                  <option value="NAPAS">Napas</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Số Thẻ (Card PAN)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="4242 •••• •••• 4242"
                  value={cardForm.card_number}
                  onChange={(e) => setCardForm({ ...cardForm, card_number: e.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Tên Chủ Thẻ (Không Dấu)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="NGUYEN VAN A"
                  value={cardForm.holder_name}
                  onChange={(e) => setCardForm({ ...cardForm, holder_name: e.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-checkbox-label">
                  <input
                    type="checkbox"
                    checked={cardForm.is_default}
                    onChange={(e) => setCardForm({ ...cardForm, is_default: e.target.checked })}
                  />
                  Đặt làm phương thức thanh toán mặc định
                </label>
              </div>

              <div
                style={{
                  fontSize: '0.75rem',
                  color: '#64748b',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  padding: '0.65rem 0.85rem',
                  marginBottom: '1.25rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                }}
              >
                <ShieldCheck size={18} color="#16a34a" />
                <span>
                  Bảo mật chuẩn PCI-DSS: Hệ thống chuyển đổi số thẻ thành Token đại diện; không bao giờ lưu trữ số thẻ thật.
                </span>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowLinkCardModal(false)}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary">
                  Xác Nhận Liên Kết
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Create Wallet */}
      {showCreateWalletModal && (
        <div className="modal-overlay" onClick={() => setShowCreateWalletModal(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Mở Ví Thanh Toán Mới</h3>
              <button className="close-btn" onClick={() => setShowCreateWalletModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateWallet}>
              <div className="form-group">
                <label className="form-label">Mã Người Sở Hữu (Owner ID)</label>
                <input
                  type="text"
                  className="form-input"
                  value={currentOwner}
                  readOnly
                  disabled
                />
              </div>

              <div className="form-group">
                <label className="form-label">Đồng Tiền Chuẩn</label>
                <input
                  type="text"
                  className="form-input"
                  value="USD (Đơn vị tính: Cents)"
                  readOnly
                  disabled
                />
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowCreateWalletModal(false)}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary">
                  Tạo Ví Ngay
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
