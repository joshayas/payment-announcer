const API_BASE = window.PAYMENT_ANNOUNCER_API_BASE || 'http://localhost:4000';

const $ = (id) => document.getElementById(id);
const loginScreen = $('loginScreen');
const dashboard = $('dashboard');
const loginForm = $('loginForm');
const loginError = $('loginError');
const driverList = $('driverList');
const tabs = document.querySelectorAll('.tab');

let currentStatus = 'pending_approval';
let pendingRejectId = null;

function getToken(){ return localStorage.getItem('pa_admin_token'); }
function setToken(t){ localStorage.setItem('pa_admin_token', t); }
function clearToken(){ localStorage.removeItem('pa_admin_token'); }

async function api(path, options = {}){
  const res = await fetch(API_BASE + path, {
    ...options,
    headers: {
      ...(options.headers || {}),
      ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
    },
  });
  if(res.status === 401){
    clearToken();
    showLogin();
    throw new Error('Session expired, please log in again');
  }
  const data = await res.json().catch(() => ({}));
  if(!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

function showLogin(){
  loginScreen.style.display = 'flex';
  dashboard.style.display = 'none';
}
function showDashboard(){
  loginScreen.style.display = 'none';
  dashboard.style.display = 'block';
  loadDrivers();
}

loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  loginError.textContent = '';
  try{
    const data = await api('/api/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: $('username').value, password: $('password').value }),
    });
    setToken(data.token);
    showDashboard();
  }catch(err){
    loginError.textContent = err.message;
  }
});

$('logoutBtn').addEventListener('click', () => {
  clearToken();
  showLogin();
});

tabs.forEach(tab => {
  tab.addEventListener('click', () => {
    tabs.forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    currentStatus = tab.dataset.status;
    loadDrivers();
  });
});

async function loadDrivers(){
  driverList.innerHTML = '<p class="empty-state">Loading...</p>';
  try{
    const qs = currentStatus ? `?status=${currentStatus}` : '';
    const data = await api(`/api/admin/drivers${qs}`);
    renderDrivers(data.drivers);
  }catch(err){
    driverList.innerHTML = `<p class="empty-state">${escapeHtml(err.message)}</p>`;
  }
}

function renderDrivers(drivers){
  if(drivers.length === 0){
    driverList.innerHTML = '<p class="empty-state">Nothing here right now.</p>';
    return;
  }
  driverList.innerHTML = drivers.map(d => {
    const roleLabel = d.role === 'merchant' ? 'Merchant' : 'Driver';
    const detailLine = d.role === 'merchant'
      ? (d.shopName ? escapeHtml(d.shopName) : '')
      : (d.plate ? escapeHtml(d.plate) : '');
    const sep = ' | ';
    return `
    <div class="driver-card" data-id="${d.id}">
      <div class="driver-head">
        <div>
          <div class="driver-name">${escapeHtml(d.name)}<span class="role-badge ${d.role || 'driver'}">${roleLabel}</span></div>
          <div class="driver-meta">${escapeHtml(d.phone)}${detailLine ? sep + detailLine : ''}</div>
          <div class="driver-meta">Submitted ${d.submittedAt ? timeAgo(d.submittedAt) : 'unknown'}</div>
        </div>
        <span class="badge ${d.status}">${statusLabel(d.status)}</span>
      </div>
      ${d.depositScreenshot ? `<img class="driver-shot" src="${API_BASE}${d.depositScreenshot}" alt="Deposit screenshot for ${escapeHtml(d.name)}">` : ''}
      ${d.rejectionNote ? `<div class="rejection-note">Note: ${escapeHtml(d.rejectionNote)}</div>` : ''}
      ${d.status === 'pending_approval' ? `
        <div class="driver-actions" style="margin-top:14px;">
          <button class="btn btn-danger" data-action="reject" data-id="${d.id}">Reject</button>
          <button class="btn btn-mint" data-action="approve" data-id="${d.id}">Approve</button>
        </div>
      ` : ''}
    </div>
  `;
  }).join('');

  driverList.querySelectorAll('[data-action="approve"]').forEach(btn => {
    btn.addEventListener('click', () => approveDriver(btn.dataset.id));
  });
  driverList.querySelectorAll('[data-action="reject"]').forEach(btn => {
    btn.addEventListener('click', () => openRejectModal(btn.dataset.id));
  });
}

async function approveDriver(id){
  try{
    await api(`/api/admin/drivers/${id}/approve`, { method: 'POST' });
    loadDrivers();
  }catch(err){
    alert(err.message);
  }
}

function openRejectModal(id){
  pendingRejectId = id;
  $('rejectNote').value = '';
  $('rejectModal').style.display = 'flex';
}
$('cancelRejectBtn').addEventListener('click', () => { $('rejectModal').style.display = 'none'; });
$('confirmRejectBtn').addEventListener('click', async () => {
  try{
    await api(`/api/admin/drivers/${pendingRejectId}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note: $('rejectNote').value.trim() }),
    });
    $('rejectModal').style.display = 'none';
    loadDrivers();
  }catch(err){
    alert(err.message);
  }
});

function statusLabel(s){
  return { pending_deposit: 'Awaiting deposit', pending_approval: 'Pending', approved: 'Approved', rejected: 'Rejected' }[s] || s;
}
function timeAgo(iso){
  const diffMin = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if(diffMin < 1) return 'just now';
  if(diffMin < 60) return `${diffMin}m ago`;
  const h = Math.round(diffMin / 60);
  if(h < 24) return `${h}h ago`;
  return `${Math.round(h / 24)}d ago`;
}
function escapeHtml(str){
  const d = document.createElement('div');
  d.textContent = str == null ? '' : String(str);
  return d.innerHTML;
}

$('credentialsBtn').addEventListener('click', () => {
  $('currentPasswordInput').value = '';
  $('newUsernameInput').value = '';
  $('newPasswordInput').value = '';
  $('credentialsError').textContent = '';
  $('credentialsModal').style.display = 'flex';
});
$('cancelCredentialsBtn').addEventListener('click', () => { $('credentialsModal').style.display = 'none'; });
$('confirmCredentialsBtn').addEventListener('click', async () => {
  $('credentialsError').textContent = '';
  try{
    const body = {
      currentPassword: $('currentPasswordInput').value,
      newUsername: $('newUsernameInput').value.trim() || undefined,
      newPassword: $('newPasswordInput').value.trim() || undefined,
    };
    await api('/api/admin/update-credentials', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    $('credentialsModal').style.display = 'none';
    alert('Credentials updated. Use your new login next time.');
  }catch(err){
    $('credentialsError').textContent = err.message;
  }
});

// Boot
if(getToken()) showDashboard(); else showLogin();