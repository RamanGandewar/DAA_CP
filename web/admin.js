const AUTH_SESSION_KEY = 'cafe_auth_session_v1';

function getStoredSession() {
  try {
    const raw = localStorage.getItem(AUTH_SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    return null;
  }
}

function setAdminStatus(message) {
  const node = document.getElementById('adminStatus');
  if (node) node.textContent = message;
}

function statCard(label, value) {
  return `<article class="stat-card"><div class="label">${label}</div><div class="value">${value}</div></article>`;
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || 'Request failed');
  }
  return data;
}

function renderOverview(data) {
  document.getElementById('statsGrid').innerHTML = [
    statCard('Users', data.totalUsers),
    statCard('Admins', data.totalAdmins),
    statCard('Logins', data.totalLogins),
    statCard('Searches', data.totalSearches),
    statCard('Onboarding Done', data.onboardingCompletedCount)
  ].join('');

  const tbody = document.querySelector('#usersTable tbody');
  tbody.innerHTML = '';
  data.users.forEach(user => {
    const tr = document.createElement('tr');
    const location = user.lastLocationLat != null && user.lastLocationLon != null
      ? `${user.lastLocationLat.toFixed(4)}, ${user.lastLocationLon.toFixed(4)} (${user.lastLocationSource})`
      : 'Not captured';
    const preferences = [user.preferredCafeType, user.defaultBudgetRange, user.dietaryPreference]
      .filter(Boolean)
      .join(' | ') || 'Not filled';
    tr.innerHTML = `
      <td>${user.displayName || '-'}</td>
      <td>${user.email || '-'}</td>
      <td>${user.role}</td>
      <td>${user.onboardingCompleted ? 'Completed' : 'Pending'}</td>
      <td>${user.loginCount}</td>
      <td>${user.totalSearches}</td>
      <td>${preferences}</td>
      <td>${location}</td>
      <td>${user.lastLoginAt || '-'}</td>
    `;
    tbody.appendChild(tr);
  });
}

document.getElementById('adminLogoutBtn').addEventListener('click', async () => {
  const session = getStoredSession();
  try {
    if (session && session.session && session.session.sessionToken) {
      await fetchJson(`/api/auth/logout?sessionToken=${encodeURIComponent(session.session.sessionToken)}`, { method: 'POST' });
    }
  } catch (err) {
    // ignore and continue clearing local session
  }
  localStorage.removeItem(AUTH_SESSION_KEY);
  window.location.href = '/landing.html';
});

(async function initAdmin() {
  const session = getStoredSession();
  if (!session || !session.session || !session.session.sessionToken) {
    window.location.href = '/login.html?role=admin';
    return;
  }
  try {
    const me = await fetchJson(`/api/auth/me?sessionToken=${encodeURIComponent(session.session.sessionToken)}`);
    if (!me.user || me.user.role !== 'ADMIN') {
      window.location.href = '/index.html';
      return;
    }
    const overview = await fetchJson(`/api/admin/overview?sessionToken=${encodeURIComponent(session.session.sessionToken)}`);
    renderOverview(overview);
    setAdminStatus(`Signed in as ${me.user.displayName || me.user.email}`);
  } catch (err) {
    setAdminStatus(err.message);
    localStorage.removeItem(AUTH_SESSION_KEY);
    window.location.href = '/login.html?role=admin';
  }
})();
