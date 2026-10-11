// Mini Digital Wallet - logic giao dien (khong phu thuoc thu vien ngoai)

var API_BASE = '/api/v1';

var state = {
  token: localStorage.getItem('wallet_token') || null,
  user: JSON.parse(localStorage.getItem('wallet_user') || 'null'),
  currentWallet: null,
  wallets: [],
  paymentMethods: [],
  showBalance: true
};

document.addEventListener('DOMContentLoaded', function () {
  if (state.token && state.user) {
    showAppView();
    refreshData();
  } else {
    showAuthView();
  }
});

// --- MESSAGE BAR ---
function showMsg(text, type) {
  var box = document.getElementById('msg');
  box.className = type === 'err' ? 'err' : 'ok';
  box.textContent = text;
  window.scrollTo(0, 0);
}

// --- AUTH ---
async function handleLogin(e) {
  e.preventDefault();
  var username = document.getElementById('login-username').value.trim();
  var password = document.getElementById('login-password').value;
  try {
    var data = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username, password: password })
    });
    setSession(data);
    showAppView();
    refreshData();
    showMsg('Chao mung tro lai, ' + data.username + '!', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

async function handleRegister(e) {
  e.preventDefault();
  var body = {
    username: document.getElementById('reg-username').value.trim(),
    email: document.getElementById('reg-email').value.trim(),
    password: document.getElementById('reg-password').value,
    fullName: document.getElementById('reg-fullname').value.trim()
  };
  try {
    var data = await apiFetch('/auth/register', { method: 'POST', body: JSON.stringify(body) });
    setSession(data);
    showAppView();
    refreshData();
    showMsg('Tao tai khoan thanh cong! Vi USD mac dinh da duoc mo.', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

function setSession(authData) {
  state.token = authData.accessToken;
  state.user = { id: authData.userId, username: authData.username, email: authData.email };
  localStorage.setItem('wallet_token', state.token);
  localStorage.setItem('wallet_user', JSON.stringify(state.user));
}

function handleLogout() {
  state.token = null;
  state.user = null;
  state.currentWallet = null;
  localStorage.removeItem('wallet_token');
  localStorage.removeItem('wallet_user');
  showAuthView();
  return false;
}

function showAuthView() {
  document.getElementById('auth-view').classList.remove('hidden');
  document.getElementById('app-view').classList.add('hidden');
}

function showAppView() {
  document.getElementById('auth-view').classList.add('hidden');
  document.getElementById('app-view').classList.remove('hidden');
  if (state.user) {
    document.getElementById('user-display-name').textContent = state.user.username;
    document.getElementById('account-avatar').textContent =
      state.user.username.charAt(0).toUpperCase();
  }
}

// --- FETCH HELPER (kem JWT + Idempotency-Key khi can) ---
async function apiFetch(endpoint, options) {
  options = options || {};
  var headers = Object.assign({}, options.headers || {});
  if (state.token) {
    headers['Authorization'] = 'Bearer ' + state.token;
  }
  if (!headers['Content-Type'] && options.body) {
    headers['Content-Type'] = 'application/json';
  }
  var res = await fetch(API_BASE + endpoint, {
    method: options.method || 'GET',
    headers: headers,
    body: options.body
  });
  if (res.status === 401) {
    handleLogout();
    throw new Error('Phien dang nhap het han. Vui long dang nhap lai.');
  }
  var isJson = (res.headers.get('content-type') || '').indexOf('application/json') !== -1;
  var data = isJson ? await res.json() : null;
  if (!res.ok) {
    var msg = (data && (data.detail || data.message)) || ('Loi he thong (' + res.status + ')');
    if (data && data.errors) {
      msg = Object.values(data.errors).join(', ');
    }
    throw new Error(msg);
  }
  return data;
}

// --- NAVIGATION ---
function navigate(sectionId) {
  var sections = ['overview', 'transfers', 'p2p', 'cards', 'reversal'];
  sections.forEach(function (id) {
    var el = document.getElementById('section-' + id);
    if (el) {
      el.classList.toggle('hidden', id !== sectionId);
    }
    var link = document.getElementById('nav-' + id);
    if (link) {
      link.classList.toggle('active', id === sectionId);
    }
  });
  return false;
}

// --- FORMAT (USD cents -> X.XX) ---
function formatAmount(cents) {
  if (cents === null || cents === undefined) return '0.00';
  return (cents / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(iso) {
  return new Date(iso).toLocaleString('vi-VN');
}

// --- BALANCE CARD ---
function toggleBalance() {
  state.showBalance = !state.showBalance;
  document.getElementById('eye-toggle').textContent = state.showBalance ? 'Ẩn' : 'Hiện';
  if (state.currentWallet) {
    updateWalletUI(state.currentWallet);
  }
  return false;
}

function setP2pAmount(cents) {
  var input = document.getElementById('p2p-amount');
  if (cents === 'ALL') {
    if (!state.currentWallet) return showMsg('Khong tim thay vi', 'err');
    input.value = (state.currentWallet.available_balance / 100).toFixed(2);
  } else {
    input.value = (cents / 100).toFixed(2);
  }
}

// --- DATA ---
async function refreshData() {
  try {
    var wallets = await apiFetch('/wallets?owner_id=' + encodeURIComponent(state.user.id));
    state.wallets = wallets || [];
    if (state.wallets.length > 0) {
      state.currentWallet = state.wallets[0];
      updateWalletUI(state.currentWallet);
      await fetchLedgerEntries(state.currentWallet.id);
    }
    var pms = await apiFetch('/payment-methods?owner_id=' + encodeURIComponent(state.user.id));
    state.paymentMethods = pms || [];
    updateCardsUI(state.paymentMethods);
  } catch (err) {
    showMsg(err.message, 'err');
  }
}

function updateWalletUI(wallet) {
  var masked = '••••••';
  document.getElementById('display-available-balance').textContent =
    state.showBalance ? formatAmount(wallet.available_balance) : masked;
  document.getElementById('display-ledger-balance').textContent =
    '$' + (state.showBalance ? formatAmount(wallet.ledger_balance) : masked) + ' USD';
  document.getElementById('display-wallet-id').textContent =
    wallet.id.length > 18 ? wallet.id.substring(0, 18) + '...' : wallet.id;
  document.getElementById('btn-toggle-wallet').textContent =
    wallet.status === 'ACTIVE' ? 'Khóa ví' : 'Mở ví';
}

async function fetchLedgerEntries(walletId) {
  try {
    var entries = await apiFetch('/ledger/wallets/' + walletId + '/entries');
    renderLedgerList(entries || []);
  } catch (err) {
    showMsg(err.message, 'err');
  }
}

function renderLedgerList(entries) {
  var box = document.getElementById('ledger-list');
  if (entries.length === 0) {
    box.innerHTML = '<p class="muted">Chưa có phát sinh giao dịch nào.</p>';
    return;
  }
  box.innerHTML = entries.map(function (e) {
    var isCredit = e.entry_type === 'CREDIT';
    return '<div class="row">' +
      '<div><div><b>' + e.entry_type + '</b> · dòng ' + e.line_no + '</div>' +
      '<div class="sub">' + formatDate(e.created_at) + ' · số dư sau ' + formatAmount(e.balance_after) + '</div></div>' +
      '<div class="' + (isCredit ? 'plus' : 'minus') + '">' + (isCredit ? '+' : '-') + '$' + formatAmount(e.amount) + '</div>' +
      '</div>';
  }).join('');
}

function updateCardsUI(cards) {
  var optionsHtml = '<option value="">-- Chọn thẻ --</option>' + cards.map(function (c) {
    return '<option value="' + c.id + '">' + c.provider + ' (' + c.masked_number + ')</option>';
  }).join('');
  document.getElementById('topup-card-select').innerHTML = optionsHtml;
  document.getElementById('withdraw-card-select').innerHTML = optionsHtml;

  var box = document.getElementById('payment-card-container');
  if (cards.length === 0) {
    box.innerHTML = '<p class="muted">Chưa liên kết thẻ nào.</p>';
    return;
  }
  box.innerHTML = cards.map(function (c) {
    return '<div class="row">' +
      '<div><div><b>' + c.provider + '</b> <span class="mono">' + c.masked_number + '</span></div>' +
      '<div class="sub">' + c.holder_name + (c.is_default ? ' · mặc định' : '') + '</div></div>' +
      '</div>';
  }).join('');
}

// --- ACTIONS ---
async function handleTopUp(e) {
  e.preventDefault();
  if (!state.currentWallet) return showMsg('Khong tim thay vi', 'err');
  var dollars = parseFloat(document.getElementById('topup-amount').value);
  try {
    await apiFetch('/transfers/topup', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'TOPUP_' + Date.now() },
      body: JSON.stringify({
        wallet_id: state.currentWallet.id,
        payment_method_id: document.getElementById('topup-card-select').value,
        amount: Math.round(dollars * 100)
      })
    });
    document.getElementById('topup-amount').value = '';
    refreshData();
    navigate('overview');
    showMsg('Nap thanh cong $' + dollars.toFixed(2) + ' vao vi!', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

async function handleWithdraw(e) {
  e.preventDefault();
  if (!state.currentWallet) return showMsg('Khong tim thay vi', 'err');
  var dollars = parseFloat(document.getElementById('withdraw-amount').value);
  try {
    await apiFetch('/transfers/withdraw', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'WTD_' + Date.now() },
      body: JSON.stringify({
        wallet_id: state.currentWallet.id,
        payment_method_id: document.getElementById('withdraw-card-select').value,
        amount: Math.round(dollars * 100)
      })
    });
    document.getElementById('withdraw-amount').value = '';
    refreshData();
    navigate('overview');
    showMsg('Rut thanh cong $' + dollars.toFixed(2) + ' ve the!', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

async function handleP2PTransfer(e) {
  e.preventDefault();
  if (!state.currentWallet) return showMsg('Khong tim thay vi nguon', 'err');
  var dollars = parseFloat(document.getElementById('p2p-amount').value);
  try {
    await apiFetch('/transfers/p2p', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'P2P_' + Date.now() },
      body: JSON.stringify({
        source_wallet_id: state.currentWallet.id,
        destination_wallet_id: document.getElementById('p2p-dest-id').value.trim(),
        amount: Math.round(dollars * 100),
        description: document.getElementById('p2p-note').value.trim() || null
      })
    });
    document.getElementById('p2p-dest-id').value = '';
    document.getElementById('p2p-amount').value = '';
    document.getElementById('p2p-note').value = '';
    refreshData();
    navigate('overview');
    showMsg('Chuyen khoan thanh cong $' + dollars.toFixed(2) + '!', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

async function handleLinkCard(e) {
  e.preventDefault();
  if (!state.user) return showMsg('Vui long dang nhap lai', 'err');
  try {
    await apiFetch('/payment-methods', {
      method: 'POST',
      body: JSON.stringify({
        owner_id: state.user.id,
        method_type: document.getElementById('card-method-type').value,
        provider: document.getElementById('card-provider').value,
        card_number: document.getElementById('card-number').value.replace(/\s+/g, ''),
        holder_name: document.getElementById('card-holder').value.trim(),
        is_default: document.getElementById('card-is-default').checked
      })
    });
    document.getElementById('card-holder').value = '';
    document.getElementById('card-number').value = '';
    refreshData();
    showMsg('Lien ket the thanh cong!', 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}

async function toggleWalletStatus() {
  if (!state.currentWallet) return;
  var newStatus = state.currentWallet.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
  try {
    await apiFetch('/wallets/' + state.currentWallet.id + '/status', {
      method: 'PATCH',
      body: JSON.stringify({ status: newStatus })
    });
    refreshData();
    showMsg('Trang thai vi da chuyen sang: ' + newStatus, 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
}

function copyWalletId() {
  if (!state.currentWallet) return false;
  navigator.clipboard.writeText(state.currentWallet.id);
  showMsg('Da sao chep Wallet ID!', 'ok');
  return false;
}

async function handleReversal(e) {
  e.preventDefault();
  var txId = document.getElementById('reversal-tx-id').value.trim();
  var reason = document.getElementById('reversal-reason').value.trim();
  try {
    var data = await apiFetch('/transfers/reversals', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'REV_' + Date.now() },
      body: JSON.stringify({ original_transaction_id: txId, description: reason })
    });
    document.getElementById('reversal-tx-id').value = '';
    document.getElementById('reversal-reason').value = '';
    refreshData();
    navigate('overview');
    showMsg('Hoan tien thanh cong! Giao dich dao: ' + data.reference_id, 'ok');
  } catch (err) {
    showMsg(err.message, 'err');
  }
  return false;
}
