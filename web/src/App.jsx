import React, { useState, useEffect, useCallback, useRef } from 'react';
import { api, isUnauthorized, generateIdempotencyKey } from './services/api';
import { formatCentsToUSD, readErrorMessage } from './utils/formatters';

// Pages
import AuthPage from './components/auth/AuthPage';
import Toast from './components/common/Toast';
import Navbar from './components/layout/Navbar';
import Dashboard from './components/dashboard/Dashboard';
import TransferPage from './components/transfers/TransferPage';
import TopUpPage from './components/transfers/TopUpPage';
import WithdrawPage from './components/transfers/WithdrawPage';
import CardsPage from './components/cards/CardsPage';
import HistoryPage from './components/ledger/HistoryPage';
import ContactsPage from './components/contacts/ContactsPage';

// Modals
import ProfileModal from './components/modals/ProfileModal';
import LinkCardModal from './components/modals/LinkCardModal';

const toCents = (amount) => {
  const cents = Math.round(parseFloat(amount) * 100);
  return Number.isFinite(cents) ? cents : 0;
};

export default function App() {
  // Authentication State
  const [token, setToken] = useState(() => localStorage.getItem('wallet_token') || null);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('wallet_user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  // UI State: feature pages opened from dashboard tiles
  const [activePage, setActivePage] = useState('dashboard');
  // P2P page can be opened with a prefilled recipient (from contacts "pick").
  const [transferPrefill, setTransferPrefill] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showLinkCardModal, setShowLinkCardModal] = useState(false);
  const [toast, setToast] = useState(null);
  const [copied, setCopied] = useState(false);

  // Business Data
  const [wallet, setWallet] = useState(null);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [contacts, setContacts] = useState([]);

  const toastTimer = useRef(null);
  const copiedTimer = useRef(null);

  // Idempotency keys: one key per money action, kept until a confirmed
  // response arrives so a retry reuses it instead of double-charging.
  const pendingKeys = useRef({});
  const takeKey = (action) => {
    if (!pendingKeys.current[action]) pendingKeys.current[action] = generateIdempotencyKey();
    return pendingKeys.current[action];
  };
  const consumeKey = (action) => {
    delete pendingKeys.current[action];
  };

  useEffect(() => {
    return () => {
      clearTimeout(toastTimer.current);
      clearTimeout(copiedTimer.current);
    };
  }, []);

  const showToast = useCallback((type, message) => {
    setToast({ type, message });
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  // 401 = missing/invalid/expired JWT. Try one silent refresh (rotation)
  // before dropping the session, so short-lived access tokens don't kick
  // the user out. Refresh reuse/miss -> clear everything (fail-closed).
  const refreshing = useRef(false);
  const handleSessionExpired = useCallback(async () => {
    if (refreshing.current) return;
    refreshing.current = true;
    try {
      const storedRefresh = localStorage.getItem('wallet_refresh');
      if (storedRefresh) {
        const res = await api.refresh(storedRefresh);
        if (res.ok) {
          const data = await res.json();
          setToken(data.accessToken);
          localStorage.setItem('wallet_token', data.accessToken);
          if (data.refreshToken) {
            localStorage.setItem('wallet_refresh', data.refreshToken);
          }
          return;
        }
      }
    } catch {
      // fall through to clear
    } finally {
      refreshing.current = false;
    }
    setToken(null);
    setCurrentUser(null);
    setWallet(null);
    setPaymentMethods([]);
    setContacts([]);
    localStorage.removeItem('wallet_token');
    localStorage.removeItem('wallet_refresh');
    localStorage.removeItem('wallet_user');
    showToast('error', 'Session invalid or expired. Please sign in again.');
  }, [showToast]);

  const clearSession = () => {
    setToken(null);
    setCurrentUser(null);
    setWallet(null);
    setPaymentMethods([]);
    setContacts([]);
    setActivePage('dashboard');
    localStorage.removeItem('wallet_token');
    localStorage.removeItem('wallet_refresh');
    localStorage.removeItem('wallet_user');
  };

  const copyWalletId = (id) => {
    if (!id || !navigator.clipboard) return;
    navigator.clipboard.writeText(id).catch(() => {});
    setCopied(true);
    clearTimeout(copiedTimer.current);
    copiedTimer.current = setTimeout(() => setCopied(false), 2000);
  };

  // Central navigation: navigating anywhere except transfer clears a stale prefill.
  const navigate = (page) => {
    if (page !== 'transfer') setTransferPrefill('');
    setActivePage(page);
  };

  const openTransferWith = (accountNumber) => {
    setTransferPrefill(accountNumber);
    setActivePage('transfer');
  };

  // Load Wallet Data (wallet, cards, contacts). Statement pages fetch on demand.
  const loadData = useCallback(async () => {
    if (!currentUser) return;
    const ownerId = currentUser.userId || currentUser.username || 'USER_DEMO';

    try {
      const walletRes = await api.getWallets(ownerId, token);
      if (isUnauthorized(walletRes)) {
        handleSessionExpired();
        return;
      }
      setWallet(walletRes.ok ? (await walletRes.json())[0] ?? null : null);

      const pmRes = await api.getPaymentMethods(ownerId, token);
      if (isUnauthorized(pmRes)) {
        handleSessionExpired();
        return;
      }
      setPaymentMethods(pmRes.ok ? await pmRes.json() : []);

      const contactsRes = await api.getContacts(token);
      if (isUnauthorized(contactsRes)) {
        handleSessionExpired();
        return;
      }
      setContacts(contactsRes.ok ? await contactsRes.json() : []);
    } catch {
      showToast('error', 'Failed to load data. Please check your connection.');
    }
  }, [currentUser, token, handleSessionExpired]);

  useEffect(() => {
    if (currentUser) {
      loadData();
    }
  }, [currentUser, loadData]);

  // Fetch the real fullName (AuthResponse doesn't carry it).
  const loadCurrentUser = async (accessToken, fallbackName) => {
    let fullName = fallbackName;
    try {
      const meRes = await api.me(accessToken);
      if (meRes.ok) {
        const me = await meRes.json();
        if (me.fullName) fullName = me.fullName;
      }
    } catch {
      // fullName stays as fallback username
    }
    return fullName;
  };

  // Auth Handlers
  const handleLogin = async (username, password) => {
    try {
      const res = await api.login(username, password);
      if (res.ok) {
        const data = await res.json();
        const fullName = await loadCurrentUser(data.accessToken, data.username);
        const userData = {
          userId: data.userId,
          username: data.username,
          email: data.email,
          fullName,
        };
        setToken(data.accessToken);
        setCurrentUser(userData);
        localStorage.setItem('wallet_token', data.accessToken);
        localStorage.setItem('wallet_user', JSON.stringify(userData));
        if (data.refreshToken) {
          localStorage.setItem('wallet_refresh', data.refreshToken);
        }
        showToast('success', `Welcome back, ${data.username}!`);
      } else {
        showToast('error', await readErrorMessage(res, 'Invalid username or password'));
      }
    } catch {
      showToast('error', 'Login failed. Please check your connection.');
    }
  };

  const handleRegister = async (form) => {
    try {
      const res = await api.register(form);
      if (res.ok) {
        const data = await res.json();
        const fullName = form.fullName || data.username;
        const userData = {
          userId: data.userId,
          username: data.username,
          email: data.email,
          fullName,
        };
        setToken(data.accessToken);
        setCurrentUser(userData);
        localStorage.setItem('wallet_token', data.accessToken);
        localStorage.setItem('wallet_user', JSON.stringify(userData));
        if (data.refreshToken) {
          localStorage.setItem('wallet_refresh', data.refreshToken);
        }
        showToast('success', 'Account created! Set your 6-digit Transaction PIN in Profile to enable withdrawals/transfers.');
      } else {
        showToast('error', await readErrorMessage(res, 'Could not register account'));
      }
    } catch {
      showToast('error', 'Registration failed. Please check your connection.');
    }
  };

  const handleLogout = async () => {
    setShowDropdown(false);
    const storedRefresh = localStorage.getItem('wallet_refresh');
    if (storedRefresh) {
      try {
        await api.logout(storedRefresh);
      } catch {
        // best-effort: session is cleared locally regardless
      }
    }
    clearSession();
    showToast('success', 'Logged out successfully');
  };

  // Wallet Handlers
  const handleCreateWallet = async () => {
    try {
      const res = await api.createWallet(token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        setWallet(await res.json());
        showToast('success', 'USD Wallet created successfully!');
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to create wallet'));
      }
    } catch {
      showToast('error', 'Failed to create wallet. Please check your connection.');
    }
  };

  const handleToggleFreeze = async () => {
    if (!wallet) return;
    const targetStatus = wallet.account_status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
      const res = await api.updateWalletStatus(wallet.id, targetStatus, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        setWallet(await res.json());
        showToast('success', targetStatus === 'FROZEN' ? 'Wallet safely frozen' : 'Wallet reactivated');
      } else {
        showToast('error', await readErrorMessage(res, 'Could not change status'));
      }
    } catch {
      showToast('error', 'Failed to change wallet status.');
    }
  };

  // Payment Method Handlers
  const handleLinkCard = async (cardForm) => {
    try {
      const res = await api.linkCard(
        {
          method_type: cardForm.method_type,
          provider: cardForm.provider,
          card_number: cardForm.card_number,
          holder_name: cardForm.holder_name,
          is_default: cardForm.is_default,
        },
        token
      );
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        setShowLinkCardModal(false);
        showToast('success', 'Payment card linked successfully (PCI-DSS tokenized)!');
        loadData();
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to link card'));
      }
    } catch {
      showToast('error', 'Failed to link card. Please check your connection.');
    }
  };

  const handleRevokeCard = async (id) => {
    if (!window.confirm('Remove this payment card?')) return;
    try {
      const res = await api.revokeCard(id, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok || res.status === 204) {
        showToast('success', 'Card removed successfully');
        loadData();
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to remove card'));
      }
    } catch {
      showToast('error', 'Failed to remove card. Please check your connection.');
    }
  };

  // Transfer Handlers (return true on success so pages can close/redirect)
  const handleTopUp = async (amount, pmId) => {
    if (!wallet || !pmId) {
      showToast('error', 'Please link a payment card first');
      return false;
    }
    const cents = toCents(amount);
    if (cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return false;
    }

    try {
      const res = await api.topUp(
        {
          walletId: wallet.id,
          paymentMethodId: pmId,
          amount: cents,
          description: `Top-up $${cents / 100} from card`,
        },
        takeKey('topup'),
        token
      );
      consumeKey('topup');
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return false;
      }
      if (res.ok) {
        showToast('success', `Top-up successful: +${formatCentsToUSD(cents)}`);
        loadData();
        return true;
      }
      showToast('error', await readErrorMessage(res, 'Top-up failed'));
      return false;
    } catch {
      // Network error: keep the key so a manual retry is idempotent.
      showToast('error', 'Top-up failed. Please check your connection.');
      return false;
    }
  };

  const handleWithdraw = async (amount, pmId, pin) => {
    if (!wallet || !pmId) {
      showToast('error', 'Please link a payment card first');
      return false;
    }
    const cents = toCents(amount);
    if (cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return false;
    }
    if (wallet.available_balance < cents) {
      showToast('error', 'Insufficient available balance');
      return false;
    }

    try {
      const res = await api.withdraw(
        {
          walletId: wallet.id,
          paymentMethodId: pmId,
          amount: cents,
          description: `Withdraw $${cents / 100} to card`,
          pin,
        },
        takeKey('withdraw'),
        token
      );
      consumeKey('withdraw');
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return false;
      }
      if (res.ok) {
        showToast('success', `Withdrawal successful: -${formatCentsToUSD(cents)}`);
        loadData();
        return true;
      }
      showToast('error', await readErrorMessage(res, 'Withdrawal failed'));
      return false;
    } catch {
      showToast('error', 'Withdrawal failed. Please check your connection.');
      return false;
    }
  };

  const handleP2pTransfer = async (cents, destAccountNumber, pin, nickname) => {
    if (!wallet) return false;
    if (!/^[0-9]{12}$/.test(destAccountNumber)) {
      showToast('error', 'Recipient account number must be 12 digits');
      return false;
    }
    if (cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return false;
    }
    if (wallet.available_balance < cents) {
      showToast('error', 'Insufficient available balance');
      return false;
    }

    try {
      const res = await api.p2pTransfer(
        {
          sourceWalletId: wallet.id,
          destinationAccountNumber: destAccountNumber,
          amount: cents,
          description: `P2P transfer $${cents / 100}`,
          pin,
        },
        takeKey('p2p'),
        token
      );
      consumeKey('p2p');
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return false;
      }
      if (res.ok) {
        showToast('success', `Transfer successful: -${formatCentsToUSD(cents)}`);
        if (nickname) {
          api.createContact({ accountNumber: destAccountNumber, nickname }, token)
            .then(() => loadData())
            .catch(() => {});
        }
        loadData();
        return true;
      }
      showToast('error', await readErrorMessage(res, 'Transfer failed'));
      return false;
    } catch {
      showToast('error', 'Transfer failed. Please check your connection.');
      return false;
    }
  };

  // Contacts Handlers
  const handleAddContact = async ({ accountNumber, nickname }) => {
    try {
      const res = await api.createContact({ accountNumber, nickname }, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        showToast('success', `Saved "${nickname}" to contacts`);
        loadData();
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to save contact'));
      }
    } catch {
      showToast('error', 'Failed to save contact. Please check your connection.');
    }
  };

  const handleDeleteContact = async (id, nickname) => {
    if (!window.confirm(`Remove "${nickname}" from contacts?`)) return;
    try {
      const res = await api.deleteContact(id, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok || res.status === 204) {
        setContacts((prev) => prev.filter((c) => c.id !== id));
        showToast('success', 'Contact removed');
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to remove contact'));
      }
    } catch {
      showToast('error', 'Failed to remove contact. Please check your connection.');
    }
  };

  const handleToggleFavorite = async (id) => {
    try {
      const res = await api.toggleFavoriteContact(id, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        const updated = await res.json();
        setContacts((prev) => prev.map((c) => (c.id === id ? updated : c)));
      } else {
        showToast('error', await readErrorMessage(res, 'Failed to update contact'));
      }
    } catch {
      showToast('error', 'Failed to update contact. Please check your connection.');
    }
  };

  // Render Auth View if not logged in
  if (!currentUser) {
    return (
      <AuthPage
        onLogin={handleLogin}
        onRegister={handleRegister}
        toast={toast}
        onCloseToast={() => setToast(null)}
      />
    );
  }

  // Dashboard + feature pages
  const renderPage = () => {
    switch (activePage) {
      case 'transfer':
        return (
          <TransferPage
            key={transferPrefill || 'default'}
            wallet={wallet}
            contacts={contacts}
            token={token}
            prefillAccount={transferPrefill}
            onSubmit={handleP2pTransfer}
            onSessionExpired={handleSessionExpired}
            onNavigate={navigate}
          />
        );
      case 'topup':
        return (
          <TopUpPage
            wallet={wallet}
            paymentMethods={paymentMethods}
            onSubmit={handleTopUp}
            onNavigate={navigate}
          />
        );
      case 'withdraw':
        return (
          <WithdrawPage
            wallet={wallet}
            paymentMethods={paymentMethods}
            onSubmit={handleWithdraw}
            onNavigate={navigate}
          />
        );
      case 'cards':
        return (
          <CardsPage
            paymentMethods={paymentMethods}
            onOpenLinkCard={() => setShowLinkCardModal(true)}
            onRevokeCard={handleRevokeCard}
            onNavigate={navigate}
          />
        );
      case 'history':
        return (
          <HistoryPage
            wallet={wallet}
            token={token}
            showToast={showToast}
            onSessionExpired={handleSessionExpired}
            onNavigate={navigate}
          />
        );
      case 'contacts':
        return (
          <ContactsPage
            contacts={contacts}
            onAdd={handleAddContact}
            onDelete={handleDeleteContact}
            onToggleFavorite={handleToggleFavorite}
            onPick={openTransferWith}
            onNavigate={navigate}
          />
        );
      case 'dashboard':
      default:
        return (
          <Dashboard
            wallet={wallet}
            paymentMethodCount={paymentMethods.length}
            contactCount={contacts.length}
            copied={copied}
            onCopyId={copyWalletId}
            onToggleFreeze={handleToggleFreeze}
            onNavigate={navigate}
            onCreateWallet={handleCreateWallet}
          />
        );
    }
  };

  return (
    <div className="app-container">
      <Navbar
        currentUser={currentUser}
        showDropdown={showDropdown}
        setShowDropdown={setShowDropdown}
        onOpenProfile={() => setShowProfileModal(true)}
        onLogout={handleLogout}
        onNavigateHome={() => navigate('dashboard')}
      />

      {/* Dashboard keeps its own full-width hero layout; sub-pages center their content. */}
      <main className={`main-wrapper${activePage === 'dashboard' ? '' : ' page-centered'}`}>
        <Toast toast={toast} onClose={() => setToast(null)} />
        {renderPage()}
      </main>

      {/* Modals */}
      {showProfileModal && (
        <ProfileModal
          currentUser={currentUser}
          token={token}
          showToast={showToast}
          onClose={() => setShowProfileModal(false)}
        />
      )}

      {showLinkCardModal && (
        <LinkCardModal
          onClose={() => setShowLinkCardModal(false)}
          onLinkCard={handleLinkCard}
        />
      )}
    </div>
  );
}
