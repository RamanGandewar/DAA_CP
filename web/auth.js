const AUTH_SESSION_KEY = 'cafe_auth_session_v1';

function saveSession(payload) {
  if (!payload || !payload.user || !payload.session) return;
  localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(payload));
  if (payload.user.userKey) {
    localStorage.setItem('cafe_user_key_v1', payload.user.userKey);
  }
}

function setAuthStatus(message) {
  const node = document.getElementById('authStatus');
  if (node) node.textContent = message;
}

function queryString(params) {
  return new URLSearchParams(params).toString();
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || 'Request failed');
  }
  return data;
}

function showTab(mode) {
  const loginForm = document.getElementById('loginForm');
  const signupForm = document.getElementById('signupForm');
  const loginTab = document.getElementById('loginTab');
  const signupTab = document.getElementById('signupTab');
  const authTitle = document.getElementById('authTitle');

  const loginMode = mode !== 'signup';
  loginForm.classList.toggle('hidden', !loginMode);
  signupForm.classList.toggle('hidden', loginMode);
  loginTab.classList.toggle('active', loginMode);
  signupTab.classList.toggle('active', !loginMode);
  authTitle.textContent = new URLSearchParams(window.location.search).get('role') === 'admin'
    ? 'Admin Login'
    : (loginMode ? 'User Login' : 'Create User Account');
}

document.getElementById('loginTab').addEventListener('click', () => showTab('login'));
document.getElementById('signupTab').addEventListener('click', () => showTab('signup'));

document.getElementById('loginForm').addEventListener('submit', async event => {
  event.preventDefault();
  setAuthStatus('Signing in...');
  try {
    const payload = await fetchJson(`/api/auth/login?${queryString({
      email: document.getElementById('loginEmail').value.trim(),
      password: document.getElementById('loginPassword').value
    })}`, { method: 'POST' });
    saveSession(payload);
    if (payload.user.role === 'ADMIN') {
      window.location.href = '/admin.html';
      return;
    }
    window.location.href = '/index.html';
  } catch (err) {
    setAuthStatus(err.message);
  }
});

document.getElementById('signupForm').addEventListener('submit', async event => {
  event.preventDefault();
  setAuthStatus('Creating account...');
  try {
    const payload = await fetchJson(`/api/auth/register?${queryString({
      name: document.getElementById('signupName').value.trim(),
      email: document.getElementById('signupEmail').value.trim(),
      password: document.getElementById('signupPassword').value
    })}`, { method: 'POST' });
    saveSession(payload);
    window.location.href = '/index.html';
  } catch (err) {
    setAuthStatus(err.message);
  }
});

(function initAuthPage() {
  const params = new URLSearchParams(window.location.search);
  if (params.get('role') === 'admin') {
    showTab('login');
    document.getElementById('signupTab').classList.add('hidden');
    document.getElementById('signupForm').classList.add('hidden');
    document.getElementById('loginTab').style.flex = '1';
  } else {
    showTab('login');
  }
})();
