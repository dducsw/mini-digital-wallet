import React, { useState, useEffect, useCallback, useRef } from 'react';
import { ArrowRightLeft, CreditCard, History } from 'lucide-react';
import { api, isUnauthorized } from './services/api';
import { formatCentsToUSD } from './utils/formatters';

// Common & Layout
import Toast from './components/common/Toast';
import Navbar from './components/layout/Navbar';

// Views & Tabs
import AuthPage from './components/auth/AuthPage';
import WalletCard from './components/wallet/WalletCard';
import TransfersTab from './components/transfers/TransfersTab';
import PaymentMethodsTab from './components/cards/PaymentMethodsTab';
import LedgerTab from './components/ledger/LedgerTab';

// Modals
import ProfileModal from './components/modals/ProfileModal';
import LinkCardModal from './components/modals/LinkCardModal';
import CreateWalletModal from './components/modals/CreateWalletModal';

export default function App() {
  // Authentication State
  const [token, setToken] = useState(() => localStorage.getItem('wallet_token') || null);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('wallet_user');
      if (!saved) return null;
      const parsed = JSON.parse(saved);
      if (parsed && parsed.fullName) {
        parsed.fullName = parsed.fullName.replace(/\s*\(.*?\)/g, '').trim();
      }
      return parsed;
    } catch {
      return null;
    }
  });

  // UI State
  const [showDropdown, setShowDropdown] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showLinkCardModal, setShowLinkCardModal] = useState(false);
  const [showCreateWalletModal, setShowCreateWalletModal] = useState(false);
  const [activeTab, setActiveTab] = useState('transfers'); // 'transfers' | 'cards' | 'ledger'
  const [toast, setToast] = useState(null);
  const [copied, setCopied] = useState(false);

  // Business Data
  const [wallet, setWallet] = useState(null);
  const [paymentMethods, setPaymentMethods] = useState([]);
  const [ledgerEntries, setLedgerEntries] = useState([]);

  const toastTimer = useRef(null);
  const copiedTimer = useRef(null);

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

  // 401 = missing/invalid/expired JWT (or a demo token the backend rejects).
  // Drop the dead session so the user lands back on login with a clear message
  // instead of staring at an empty dashboard.
  const handleSessionExpired = useCallback(() => {
    setToken(null);
    setCurrentUser(null);
    setWallet(null);
    setPaymentMethods([]);
    setLedgerEntries([]);
    localStorage.removeItem('wallet_token');
    localStorage.removeItem('wallet_user');
    showToast('error', 'Session invalid or expired. Please sign in again.');
  }, [showToast]);

  const copyWalletId = (id) => {
    if (!id || !navigator.clipboard) return;
    navigator.clipboard.writeText(id).catch(() => {});
    setCopied(true);
    clearTimeout(copiedTimer.current);
    copiedTimer.current = setTimeout(() => setCopied(false), 2000);
  };

  // Load Wallet Data
  const loadData = useCallback(async () => {
    if (!currentUser) return;
    const ownerId = currentUser.userId || currentUser.username || 'USER_DEMO';

    try {
      let activeWallet = null;

      // 1. Fetch Wallets
      const walletRes = await api.getWallets(ownerId, token);
      if (isUnauthorized(walletRes)) {
        handleSessionExpired();
        return;
      }
      if (walletRes.ok) {
        const wallets = await walletRes.json();
        if (wallets && wallets.length > 0) {
          activeWallet = wallets[0];
          setWallet(activeWallet);
        } else {
          setWallet(null);
        }
      } else {
        setWallet(null);
      }

      // 2. Fetch Payment Methods
      const pmRes = await api.getPaymentMethods(ownerId, token);
      if (isUnauthorized(pmRes)) {
        handleSessionExpired();
        return;
      }
      if (pmRes.ok) {
        const pms = await pmRes.json();
        setPaymentMethods(pms);
      } else {
        setPaymentMethods([]);
      }

      // 3. Fetch Ledger Entries
      if (activeWallet && activeWallet.id) {
        const entriesRes = await api.getLedgerEntries(activeWallet.id, token);
        if (isUnauthorized(entriesRes)) {
          handleSessionExpired();
          return;
        }
        if (entriesRes.ok) {
          const entries = await entriesRes.json();
          setLedgerEntries(entries);
        } else {
          setLedgerEntries([]);
        }
      }
    } catch {
      // Offline fallback mock data
      const fallbackWallet = {
        id: '0191fa30-9b48-732a-bc91-23588921a100',
        owner_id: ownerId,
        account_type: 'USER',
        status: 'ACTIVE',
        available_balance: 145000,
        ledger_balance: 145000,
        currency: 'USD',
        version: 1,
      };
      setWallet(fallbackWallet);

      const fallbackPms = [
        {
          id: 'pm-01-mock',
          method_type: 'DEBIT_CARD',
          provider: 'VISA',
          masked_number: '•••• •••• •••• 4242',
          holder_name: currentUser.fullName || currentUser.username || 'ALICE TRAN',
          is_default: true,
          status: 'ACTIVE',
        },
      ];
      setPaymentMethods(fallbackPms);

      setLedgerEntries([
        {
          id: 'entry-01',
          wallet_id: fallbackWallet.id,
          entry_type: 'CREDIT',
          amount: 100000,
          line_no: 1,
          balance_after: 100000,
          created_at: new Date(Date.now() - 7200000).toISOString(),
        },
        {
          id: 'entry-02',
          wallet_id: fallbackWallet.id,
          entry_type: 'CREDIT',
          amount: 45000,
          line_no: 2,
          balance_after: 145000,
          created_at: new Date().toISOString(),
        },
      ]);
    }
  }, [currentUser, token, handleSessionExpired]);

  useEffect(() => {
    if (currentUser) {
      loadData();
    }
  }, [currentUser, loadData]);

  // Auth Handlers
  const handleLogin = async (username, password) => {
    try {
      const res = await api.login(username, password);
      if (res.ok) {
        const data = await res.json();
        setToken(data.accessToken);
        const userData = {
          userId: data.userId,
          username: data.username,
          email: data.email,
          fullName: data.username,
        };
        setCurrentUser(userData);
        localStorage.setItem('wallet_token', data.accessToken);
        localStorage.setItem('wallet_user', JSON.stringify(userData));
        showToast('success', `Welcome back, ${data.username}!`);
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Invalid username or password');
      }
    } catch {
      const demoUser = {
        userId: 'USER_' + username.toUpperCase(),
        username,
        email: `${username}@example.com`,
        fullName: username.toUpperCase(),
      };
      const demoToken = 'mock-jwt-token-' + Date.now();
      setToken(demoToken);
      setCurrentUser(demoUser);
      localStorage.setItem('wallet_token', demoToken);
      localStorage.setItem('wallet_user', JSON.stringify(demoUser));
      showToast('success', `Signed in (Demo Mode): ${demoUser.username}`);
    }
  };

  const handleRegister = async (form) => {
    try {
      const res = await api.register(form);
      if (res.ok) {
        const data = await res.json();
        setToken(data.accessToken);
        const userData = {
          userId: data.userId,
          username: data.username,
          email: data.email,
          fullName: form.fullName || data.username,
        };
        setCurrentUser(userData);
        localStorage.setItem('wallet_token', data.accessToken);
        localStorage.setItem('wallet_user', JSON.stringify(userData));
        showToast('success', 'Account created successfully!');
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Could not register account');
      }
    } catch {
      const demoUser = {
        userId: 'USER_' + form.username.toUpperCase(),
        username: form.username,
        email: form.email,
        fullName: form.fullName || form.username,
      };
      const demoToken = 'mock-jwt-token-' + Date.now();
      setToken(demoToken);
      setCurrentUser(demoUser);
      localStorage.setItem('wallet_token', demoToken);
      localStorage.setItem('wallet_user', JSON.stringify(demoUser));
      showToast('success', 'Account created (Demo Mode)!');
    }
  };

  const handleQuickDemoLogin = (demoName) => {
    const demoUser = {
      userId: `USER_${demoName.toUpperCase()}`,
      username: demoName.toLowerCase(),
      email: `${demoName.toLowerCase()}@wallet.io`,
      fullName: `${demoName} Tran`,
    };
    setToken('demo-token-' + demoName);
    setCurrentUser(demoUser);
    localStorage.setItem('wallet_token', 'demo-token');
    localStorage.setItem('wallet_user', JSON.stringify(demoUser));
    showToast('success', `Quick login as: ${demoUser.fullName}`);
  };

  const handleLogout = () => {
    setShowDropdown(false);
    setToken(null);
    setCurrentUser(null);
    setWallet(null);
    setPaymentMethods([]);
    setLedgerEntries([]);
    localStorage.removeItem('wallet_token');
    localStorage.removeItem('wallet_user');
    showToast('success', 'Logged out successfully');
  };

  // Wallet Handlers
  const handleCreateWallet = async () => {
    const ownerId = currentUser?.userId || currentUser?.username;
    try {
      const res = await api.createWallet({ ownerId, currency: 'USD', walletType: 'USER' }, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        const newWallet = await res.json();
        setWallet(newWallet);
        setShowCreateWalletModal(false);
        showToast('success', 'USD Wallet created successfully!');
        loadData();
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Failed to create wallet');
      }
    } catch {
      setWallet({
        id: 'wal-' + Date.now(),
        owner_id: ownerId,
        account_type: 'USER',
        status: 'ACTIVE',
        available_balance: 0,
        ledger_balance: 0,
        currency: 'USD',
        version: 0,
      });
      setShowCreateWalletModal(false);
      showToast('success', 'Wallet created (Demo Mode)!');
    }
  };

  const handleToggleFreeze = async () => {
    if (!wallet) return;
    const targetStatus = wallet.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
      const res = await api.updateWalletStatus(wallet.id, targetStatus, token);
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        const updated = await res.json();
        setWallet(updated);
        showToast(
          'success',
          targetStatus === 'FROZEN' ? 'Wallet safely frozen' : 'Wallet reactivated'
        );
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Could not change status');
      }
    } catch {
      setWallet((prev) => ({ ...prev, status: targetStatus }));
      showToast(
        'success',
        targetStatus === 'FROZEN' ? 'Wallet frozen (Demo Mode)' : 'Wallet reactivated (Demo Mode)'
      );
    }
  };

  // Payment Method Handlers
  const handleLinkCard = async (cardForm) => {
    const ownerId = currentUser?.userId || currentUser?.username;
    try {
      const res = await api.linkCard(
        {
          owner_id: ownerId,
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
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Failed to link card');
      }
    } catch {
      const newCard = {
        id: 'pm-' + Date.now(),
        method_type: cardForm.method_type,
        provider: cardForm.provider,
        masked_number: '•••• •••• •••• ' + cardForm.card_number.slice(-4),
        holder_name: cardForm.holder_name.toUpperCase(),
        is_default: cardForm.is_default,
        status: 'ACTIVE',
      };
      setPaymentMethods((prev) => [newCard, ...prev]);
      setShowLinkCardModal(false);
      showToast('success', 'Card linked successfully (Demo Mode)');
    }
  };

  const handleRevokeCard = async (id) => {
    if (!window.confirm('Are you sure you want to revoke this payment card?')) return;
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
        showToast('error', 'Failed to remove card');
      }
    } catch {
      setPaymentMethods((prev) => prev.filter((item) => item.id !== id));
      showToast('success', 'Card removed (Demo Mode)');
    }
  };

  // Transfer Handlers
  const handleTopUp = async (amount, pmId) => {
    if (!wallet || !pmId) {
      showToast('error', 'Please link a payment card first');
      return;
    }
    const cents = Math.round(parseFloat(amount) * 100);
    if (isNaN(cents) || cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return;
    }

    try {
      const res = await api.topUp(
        {
          walletId: wallet.id,
          paymentMethodId: pmId,
          amount: cents,
          description: `Top-up $${amount} from card`,
        },
        token
      );
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        showToast('success', `Top-up successful: +${formatCentsToUSD(cents)}`);
        loadData();
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Top-up failed');
      }
    } catch {
      setWallet((prev) => ({
        ...prev,
        available_balance: prev.available_balance + cents,
        ledger_balance: prev.ledger_balance + cents,
      }));
      setLedgerEntries((prev) => [
        {
          id: 'entry-' + Date.now(),
          wallet_id: wallet.id,
          entry_type: 'CREDIT',
          amount: cents,
          line_no: prev.length + 1,
          balance_after: wallet.available_balance + cents,
          created_at: new Date().toISOString(),
        },
        ...prev,
      ]);
      showToast('success', `Top-up successful (Demo Mode): +${formatCentsToUSD(cents)}`);
    }
  };

  const handleWithdraw = async (amount, pmId) => {
    if (!wallet || !pmId) {
      showToast('error', 'Please link a payment card first');
      return;
    }
    const cents = Math.round(parseFloat(amount) * 100);
    if (isNaN(cents) || cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return;
    }
    if (wallet.available_balance < cents) {
      showToast('error', 'Insufficient available balance');
      return;
    }

    try {
      const res = await api.withdraw(
        {
          walletId: wallet.id,
          paymentMethodId: pmId,
          amount: cents,
          description: `Withdraw $${amount} to card`,
        },
        token
      );
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        showToast('success', `Withdrawal successful: -${formatCentsToUSD(cents)}`);
        loadData();
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Withdrawal failed');
      }
    } catch {
      setWallet((prev) => ({
        ...prev,
        available_balance: prev.available_balance - cents,
        ledger_balance: prev.ledger_balance - cents,
      }));
      setLedgerEntries((prev) => [
        {
          id: 'entry-' + Date.now(),
          wallet_id: wallet.id,
          entry_type: 'DEBIT',
          amount: cents,
          line_no: prev.length + 1,
          balance_after: wallet.available_balance - cents,
          created_at: new Date().toISOString(),
        },
        ...prev,
      ]);
      showToast('success', `Withdrawal successful (Demo Mode): -${formatCentsToUSD(cents)}`);
    }
  };

  const handleP2pTransfer = async (amount, destWalletId) => {
    if (!wallet) return;
    if (!destWalletId.trim()) {
      showToast('error', 'Recipient wallet ID is required');
      return;
    }
    const cents = Math.round(parseFloat(amount) * 100);
    if (isNaN(cents) || cents <= 0) {
      showToast('error', 'Please enter a valid amount');
      return;
    }
    if (wallet.available_balance < cents) {
      showToast('error', 'Insufficient available balance');
      return;
    }

    try {
      const res = await api.p2pTransfer(
        {
          sourceWalletId: wallet.id,
          destinationWalletId: destWalletId.trim(),
          amount: cents,
          description: `P2P transfer $${amount}`,
        },
        token
      );
      if (isUnauthorized(res)) {
        handleSessionExpired();
        return;
      }
      if (res.ok) {
        showToast('success', `Transfer successful: -${formatCentsToUSD(cents)}`);
        loadData();
      } else {
        const err = await res.json().catch(() => ({}));
        showToast('error', err.detail || 'Transfer failed');
      }
    } catch {
      setWallet((prev) => ({
        ...prev,
        available_balance: prev.available_balance - cents,
        ledger_balance: prev.ledger_balance - cents,
      }));
      setLedgerEntries((prev) => [
        {
          id: 'entry-' + Date.now(),
          wallet_id: wallet.id,
          entry_type: 'DEBIT',
          amount: cents,
          line_no: prev.length + 1,
          balance_after: wallet.available_balance - cents,
          created_at: new Date().toISOString(),
        },
        ...prev,
      ]);
      showToast('success', `Transfer successful (Demo Mode): -${formatCentsToUSD(cents)}`);
    }
  };

  // Render Auth View if not logged in
  if (!currentUser) {
    return (
      <AuthPage
        onLogin={handleLogin}
        onRegister={handleRegister}
        onQuickDemoLogin={handleQuickDemoLogin}
        toast={toast}
        onCloseToast={() => setToast(null)}
      />
    );
  }

  // Render Dashboard
  return (
    <div className="app-container">
      <Navbar
        currentUser={currentUser}
        showDropdown={showDropdown}
        setShowDropdown={setShowDropdown}
        onOpenProfile={() => setShowProfileModal(true)}
        onLogout={handleLogout}
      />

      <main className="main-wrapper">
        <Toast toast={toast} onClose={() => setToast(null)} />

        <WalletCard
          wallet={wallet}
          copied={copied}
          onCopyId={copyWalletId}
          onToggleFreeze={handleToggleFreeze}
          onCreateWalletClick={() => setShowCreateWalletModal(true)}
        />

        {/* Tab Controls */}
        <div className="app-tabs">
          <button
            className={`app-tab-btn ${activeTab === 'transfers' ? 'active' : ''}`}
            onClick={() => setActiveTab('transfers')}
          >
            <ArrowRightLeft size={15} />
            Transfers
          </button>
          <button
            className={`app-tab-btn ${activeTab === 'cards' ? 'active' : ''}`}
            onClick={() => setActiveTab('cards')}
          >
            <CreditCard size={15} />
            Payment Methods ({paymentMethods.length})
          </button>
          <button
            className={`app-tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
            onClick={() => setActiveTab('ledger')}
          >
            <History size={15} />
            History ({ledgerEntries.length})
          </button>
        </div>

        {/* Tab Contents */}
        {activeTab === 'transfers' && (
          <TransfersTab
            wallet={wallet}
            paymentMethods={paymentMethods}
            onTopUp={handleTopUp}
            onWithdraw={handleWithdraw}
            onP2pTransfer={handleP2pTransfer}
          />
        )}

        {activeTab === 'cards' && (
          <PaymentMethodsTab
            paymentMethods={paymentMethods}
            onOpenLinkCard={() => setShowLinkCardModal(true)}
            onRevokeCard={handleRevokeCard}
          />
        )}

        {activeTab === 'ledger' && (
          <LedgerTab ledgerEntries={ledgerEntries} />
        )}
      </main>

      {/* Modals */}
      {showProfileModal && (
        <ProfileModal
          currentUser={currentUser}
          onClose={() => setShowProfileModal(false)}
        />
      )}

      {showLinkCardModal && (
        <LinkCardModal
          onClose={() => setShowLinkCardModal(false)}
          onLinkCard={handleLinkCard}
        />
      )}

      {showCreateWalletModal && (
        <CreateWalletModal
          currentUser={currentUser}
          onClose={() => setShowCreateWalletModal(false)}
          onCreateWallet={handleCreateWallet}
        />
      )}
    </div>
  );
}
