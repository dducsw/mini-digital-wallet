import React, { useState, useEffect } from 'react';
import { ArrowRightLeft, UserCheck, UserX } from 'lucide-react';
import PageHeader from '../common/PageHeader';
import { api, isUnauthorized } from '../../services/api';
import { formatAccountInput, formatCentsToUSD } from '../../utils/formatters';

export default function TransferPage({
  wallet,
  contacts = [],
  token,
  prefillAccount = '',
  onSubmit,
  onSessionExpired,
  onNavigate,
}) {
  const [amount, setAmount] = useState('');
  const [destAccount, setDestAccount] = useState(formatAccountInput(prefillAccount));
  const [pin, setPin] = useState('');
  const [saveToContacts, setSaveToContacts] = useState(false);
  const [nickname, setNickname] = useState('');

  // Recipient preview: verified once the input reaches 12 digits.
  const [recipient, setRecipient] = useState(null);
  const [recipientStatus, setRecipientStatus] = useState('idle'); // idle | checking | found | notfound

  const digits = destAccount.replace(/\s+/g, '');
  const loginAccount = String(wallet?.account_number || '').replace(/\s+/g, '');

  // Auto-verify a prefilled recipient on first render.
  useEffect(() => {
    if (/^[0-9]{12}$/.test(prefillAccount)) {
      verifyWith(prefillAccount);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const verifyWith = async (targetDigits) => {
    if (!/^[0-9]{12}$/.test(targetDigits)) return;
    setRecipientStatus('checking');
    try {
      const res = await api.lookupWalletByAccountNumber(targetDigits, token);
      if (isUnauthorized(res)) {
        onSessionExpired();
        return;
      }
      if (res.ok) {
        setRecipient(await res.json());
        setRecipientStatus('found');
      } else {
        setRecipient(null);
        setRecipientStatus('notfound');
      }
    } catch {
      setRecipient(null);
      setRecipientStatus('notfound');
    }
  };

  const handleDestChange = (raw) => {
    const value = formatAccountInput(raw);
    setDestAccount(value);
    setRecipient(null);
    setRecipientStatus('idle');
    const nextDigits = value.replace(/\s+/g, '');
    if (nextDigits.length === 12) {
      // Verify once the 12th digit is typed. Verify the edited value, not stale state.
      verifyWith(nextDigits);
    }
  };

  const alreadyInContacts = contacts.some((c) => c.account_number === digits);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const cents = Math.round(parseFloat(amount) * 100);
    if (!Number.isFinite(cents) || cents <= 0 || recipientStatus !== 'found') return;
    const ok = await onSubmit(cents, digits, pin || null, saveToContacts ? nickname.trim() : null);
    if (ok) {
      setAmount('');
      setDestAccount('');
      setPin('');
      setSaveToContacts(false);
      setNickname('');
      setRecipient(null);
      setRecipientStatus('idle');
      onNavigate('dashboard');
    }
  };

  const canSubmit =
    recipientStatus === 'found' &&
    digits !== loginAccount &&
    Number.isFinite(parseFloat(amount)) &&
    parseFloat(amount) > 0;

  return (
    <div className="white-card" style={{ maxWidth: '560px' }}>
      <PageHeader
        title="P2P Transfer"
        subtitle="Send USD to another wallet via 12-digit account number"
        onBack={() => onNavigate('dashboard')}
      />

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label">Amount (USD)</label>
          <input
            type="number"
            className="form-control"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            min="0.01"
            step="any"
            required
          />
          {wallet && (
            <p className="form-hint">Available balance: {formatCentsToUSD(wallet.available_balance)}</p>
          )}
        </div>

        <div className="form-group">
          <label className="form-label">Recipient account number (12 digits)</label>

          {contacts.length > 0 && (
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '8px' }}>
              {contacts.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  className="contact-chip"
                  onClick={() => handleDestChange(c.account_number)}
                  title={c.account_number}
                >
                  {c.is_favorite && <span style={{ color: 'var(--blue-primary)' }}>★</span>}
                  {c.nickname}
                </button>
              ))}
            </div>
          )}

          <input
            type="text"
            inputMode="numeric"
            className="form-control"
            placeholder="8820 1234 5678"
            value={destAccount}
            onChange={(e) => handleDestChange(e.target.value)}
            maxLength={14}
            required
          />
        </div>

        {/* Recipient preview (server-verified) */}
        {recipientStatus === 'checking' && (
          <div className="recipient-preview checking">Verifying account number…</div>
        )}
        {recipientStatus === 'found' && recipient && (
          <div className="recipient-preview found">
            <UserCheck size={15} />
            <span>
              {recipient.wallet_type || 'USER'} · {recipient.currency || 'USD'} — valid account number
              {digits === loginAccount && ' (⚠ this is your own wallet)'}
            </span>
          </div>
        )}
        {recipientStatus === 'notfound' && (
          <div className="recipient-preview error">
            <UserX size={15} />
            <span>No wallet found with this account number.</span>
          </div>
        )}

        <div className="form-group">
          <label className="form-label">Transaction PIN</label>
          <input
            type="password"
            inputMode="numeric"
            maxLength={6}
            className="form-control"
            placeholder="6 digits (required if PIN is set)"
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
          />
        </div>

        {!alreadyInContacts && (
          <div style={{ marginBottom: '14px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={saveToContacts}
                onChange={(e) => setSaveToContacts(e.target.checked)}
              />
              Save recipient to contacts
            </label>
            {saveToContacts && (
              <input
                type="text"
                className="form-control"
                placeholder="Nickname (e.g. Bob)"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                style={{ marginTop: '6px' }}
                required={saveToContacts}
              />
            )}
          </div>
        )}

        <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={!canSubmit}>
          <ArrowRightLeft size={15} /> Send Money
        </button>
        {!canSubmit && (
          <p className="form-hint" style={{ marginTop: '8px' }}>
            Enter all 12 digits so the system can verify the recipient before sending.
          </p>
        )}
      </form>
    </div>
  );
}
