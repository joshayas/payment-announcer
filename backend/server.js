require('dotenv').config();

const express = require('express');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const multer = require('multer');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const { load, save } = require('./db');
const { parseTelebirrSms } = require('./smsParser');

const app = express();
const PORT = process.env.PORT || 4000;

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret-change-me';
const DEPOSIT_AMOUNT_ETB = 500;
const DEPOSIT_ACCOUNTS = {
  cbe: process.env.CBE_ACCOUNT || 'SET_CBE_ACCOUNT_IN_ENV',
  telebirr: process.env.TELEBIRR_ACCOUNT || 'SET_TELEBIRR_NUMBER_IN_ENV',
};

app.use(cors());
app.use(express.json());

const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) fs.mkdirSync(uploadsDir);
app.use('/uploads', express.static(uploadsDir));

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadsDir),
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname) || '.jpg';
    cb(null, `deposit_${Date.now()}_${Math.round(Math.random() * 1e6)}${ext}`);
  },
});
const upload = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!file.mimetype.startsWith('image/')) return cb(new Error('Only image files are allowed'));
    cb(null, true);
  },
});

(function seedAdmin() {
  const dbData = load();
  if (dbData.admins.length === 0) {
    const defaultPassword = process.env.ADMIN_DEFAULT_PASSWORD || 'changeme123';
    dbData.admins.push({
      id: 1,
      username: 'admin',
      passwordHash: bcrypt.hashSync(defaultPassword, 10),
    });
    save(dbData);
    console.log(`Seeded default admin -> username: admin, password: ${defaultPassword}`);
    console.log('Change this immediately in production (see README).');
  }
})();

function authRequired(role) {
  return (req, res, next) => {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) return res.status(401).json({ error: 'Missing token' });
    try {
      const payload = jwt.verify(token, JWT_SECRET);
      if (role && payload.role !== role) return res.status(403).json({ error: 'Wrong role for this endpoint' });
      req.auth = payload;
      next();
    } catch (e) {
      return res.status(401).json({ error: 'Invalid or expired token' });
    }
  };
}

function todayDateString() {
  return new Date().toISOString().slice(0, 10);
}

function todayTotalsFor(dbData, driverId) {
  const today = todayDateString();
  const todays = dbData.payments.filter(
    p => p.driverId === driverId && (p.loggedAt || '').slice(0, 10) === today
  );
  const totalAmount = todays.reduce((sum, p) => sum + (p.amount || 0), 0);
  return { date: today, totalAmount, count: todays.length };
}

// =========================================================
// ACCOUNT: registration (driver or merchant), login
// =========================================================

app.post('/api/driver/register', (req, res) => {
  const { role, name, phone, password, plate, shopName } = req.body;
  const accountRole = role === 'merchant' ? 'merchant' : 'driver';

  if (!name || !phone || !password) {
    return res.status(400).json({ error: 'name, phone and password are required' });
  }
  if (accountRole === 'merchant' && !shopName) {
    return res.status(400).json({ error: 'shopName is required for merchant accounts' });
  }

  const dbData = load();
  if (dbData.drivers.find(d => d.phone === phone)) {
    return res.status(409).json({ error: 'An account with this phone number already exists' });
  }
  const driver = {
    id: dbData.nextDriverId++,
    role: accountRole,
    name,
    phone,
    plate: accountRole === 'driver' ? (plate || null) : null,
    shopName: accountRole === 'merchant' ? shopName : null,
    passwordHash: bcrypt.hashSync(password, 10),
    status: 'pending_deposit',
    depositScreenshot: null,
    createdAt: new Date().toISOString(),
    submittedAt: null,
    decidedAt: null,
    rejectionNote: null,
  };
  dbData.drivers.push(driver);
  save(dbData);

  const token = jwt.sign({ role: 'driver', driverId: driver.id }, JWT_SECRET, { expiresIn: '30d' });
  res.status(201).json({
    token,
    driver: publicDriver(driver),
    depositInstructions: { amountEtb: DEPOSIT_AMOUNT_ETB, accounts: DEPOSIT_ACCOUNTS },
  });
});

app.post('/api/driver/login', (req, res) => {
  const { phone, password } = req.body;
  const dbData = load();
  const driver = dbData.drivers.find(d => d.phone === phone);
  if (!driver || !bcrypt.compareSync(password || '', driver.passwordHash)) {
    return res.status(401).json({ error: 'Invalid phone or password' });
  }
  const token = jwt.sign({ role: 'driver', driverId: driver.id }, JWT_SECRET, { expiresIn: '30d' });
  res.json({ token, driver: publicDriver(driver) });
});

app.get('/api/driver/me', authRequired('driver'), (req, res) => {
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === req.auth.driverId);
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  res.json({
    driver: publicDriver(driver),
    depositInstructions: driver.status === 'pending_deposit'
      ? { amountEtb: DEPOSIT_AMOUNT_ETB, accounts: DEPOSIT_ACCOUNTS }
      : undefined,
  });
});

// Edit profile info (name / plate / shop name) — phone stays fixed, it's the login identity.
app.patch('/api/driver/profile', authRequired('driver'), (req, res) => {
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === req.auth.driverId);
  if (!driver) return res.status(404).json({ error: 'Driver not found' });

  const { name, plate, shopName } = req.body;
  if (name && name.trim()) driver.name = name.trim();
  if (driver.role === 'driver' && plate !== undefined) driver.plate = plate.trim() || null;
  if (driver.role === 'merchant' && shopName && shopName.trim()) driver.shopName = shopName.trim();

  save(dbData);
  res.json({ driver: publicDriver(driver) });
});

app.post('/api/driver/change-password', authRequired('driver'), (req, res) => {
  const { currentPassword, newPassword } = req.body;
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === req.auth.driverId);
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  if (!currentPassword || !bcrypt.compareSync(currentPassword, driver.passwordHash)) {
    return res.status(401).json({ error: 'Current password is incorrect' });
  }
  if (!newPassword || newPassword.length < 6) {
    return res.status(400).json({ error: 'New password must be at least 6 characters' });
  }
  driver.passwordHash = bcrypt.hashSync(newPassword, 10);
  save(dbData);
  res.json({ ok: true });
});

// =========================================================
// DEPOSIT + PAYMENT LOG
// =========================================================

app.post('/api/driver/deposit', authRequired('driver'), upload.single('screenshot'), (req, res) => {
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === req.auth.driverId);
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  if (!req.file) return res.status(400).json({ error: 'screenshot file is required' });

  driver.depositScreenshot = `/uploads/${req.file.filename}`;
  driver.status = 'pending_approval';
  driver.submittedAt = new Date().toISOString();
  driver.decidedAt = null;
  driver.rejectionNote = null;
  save(dbData);

  res.json({ driver: publicDriver(driver) });
});

app.post('/api/payments/log', authRequired('driver'), (req, res) => {
  const { rawSms } = req.body;
  const parsed = parseTelebirrSms(rawSms);
  if (!parsed) return res.status(400).json({ error: 'Could not parse this SMS as a Telebirr payment' });

  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === req.auth.driverId);
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  if (driver.status !== 'approved') {
    return res.status(403).json({ error: 'Driver is not yet approved' });
  }

  const entry = {
    id: dbData.nextPaymentId++,
    driverId: driver.id,
    ...parsed,
    loggedAt: new Date().toISOString(),
  };
  dbData.payments.push(entry);
  save(dbData);

  res.status(201).json({ payment: entry });
});

app.get('/api/driver/payments', authRequired('driver'), (req, res) => {
  const dbData = load();
  const mine = dbData.payments
    .filter(p => p.driverId === req.auth.driverId)
    .sort((a, b) => new Date(b.loggedAt) - new Date(a.loggedAt));
  res.json({ payments: mine });
});

app.get('/api/driver/payments/today', authRequired('driver'), (req, res) => {
  const dbData = load();
  res.json(todayTotalsFor(dbData, req.auth.driverId));
});

function publicDriver(d) {
  const { passwordHash, ...rest } = d;
  return rest;
}

// =========================================================
// ADMIN
// =========================================================

app.post('/api/admin/login', (req, res) => {
  const { username, password } = req.body;
  const dbData = load();
  const admin = dbData.admins.find(a => a.username === username);
  if (!admin || !bcrypt.compareSync(password || '', admin.passwordHash)) {
    return res.status(401).json({ error: 'Invalid username or password' });
  }
  const token = jwt.sign({ role: 'admin', adminId: admin.id }, JWT_SECRET, { expiresIn: '12h' });
  res.json({ token, admin: { id: admin.id, username: admin.username } });
});

app.get('/api/admin/drivers', authRequired('admin'), (req, res) => {
  const dbData = load();
  let drivers = dbData.drivers;
  if (req.query.status) drivers = drivers.filter(d => d.status === req.query.status);
  drivers = drivers
    .slice()
    .sort((a, b) => new Date(b.submittedAt || b.createdAt) - new Date(a.submittedAt || a.createdAt));
  res.json({
    drivers: drivers.map(d => ({ ...publicDriver(d), today: todayTotalsFor(dbData, d.id) })),
  });
});

app.post('/api/admin/drivers/:id/approve', authRequired('admin'), (req, res) => {
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === parseInt(req.params.id, 10));
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  if (driver.status !== 'pending_approval') {
    return res.status(400).json({ error: `Cannot approve a driver in status "${driver.status}"` });
  }
  driver.status = 'approved';
  driver.decidedAt = new Date().toISOString();
  save(dbData);
  res.json({ driver: publicDriver(driver) });
});

app.post('/api/admin/drivers/:id/reject', authRequired('admin'), (req, res) => {
  const dbData = load();
  const driver = dbData.drivers.find(d => d.id === parseInt(req.params.id, 10));
  if (!driver) return res.status(404).json({ error: 'Driver not found' });
  if (driver.status !== 'pending_approval') {
    return res.status(400).json({ error: `Cannot reject a driver in status "${driver.status}"` });
  }
  driver.status = 'rejected';
  driver.decidedAt = new Date().toISOString();
  driver.rejectionNote = req.body.note || null;
  save(dbData);
  res.json({ driver: publicDriver(driver) });
});

app.post('/api/admin/update-credentials', authRequired('admin'), (req, res) => {
  const { currentPassword, newUsername, newPassword } = req.body;
  const dbData = load();
  const admin = dbData.admins.find(a => a.id === req.auth.adminId);
  if (!admin) return res.status(404).json({ error: 'Admin not found' });
  if (!currentPassword || !bcrypt.compareSync(currentPassword, admin.passwordHash)) {
    return res.status(401).json({ error: 'Current password is incorrect' });
  }
  if (newUsername && newUsername.trim()) {
    admin.username = newUsername.trim();
  }
  if (newPassword && newPassword.trim()) {
    if (newPassword.length < 6) return res.status(400).json({ error: 'New password must be at least 6 characters' });
    admin.passwordHash = bcrypt.hashSync(newPassword, 10);
  }
  save(dbData);
  res.json({ admin: { id: admin.id, username: admin.username } });
});

app.get('/api/health', (req, res) => res.json({ ok: true, time: new Date().toISOString() }));

app.listen(PORT, () => {
  console.log(`Payment Announcer backend running on http://localhost:${PORT}`);
});