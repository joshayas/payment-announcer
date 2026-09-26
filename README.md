# Payment Announcer

A verification system for Addis Ababa taxi/redat Telebirr payments: the driver's
phone reads Telebirr's payment SMS (short code **127**) automatically, shows the
sender's name in large text, and reads it out loud — so no one has to check a
screenshot. Drivers must register and pay a refundable deposit before they can
use it, approved by an admin.

Three parts, in this repo:

```
backend/       Node.js + Express API — driver/admin accounts, deposit approval, payment log
admin-web/     Static HTML/JS admin dashboard (login, approve/reject drivers)
android-app/   Android/Kotlin app source — driver login, deposit upload, SMS listener, voice
```

## 1. Backend

```bash
cd backend
npm install
cp .env.example .env      # then edit .env with real values
npm start
```

Runs on `http://localhost:4000`. On first run it seeds one admin account —
username `admin`, password from `ADMIN_DEFAULT_PASSWORD` in your `.env`
(default `changeme123` if unset). **Log in and note this down; there's no
"forgot password" flow yet, see Known gaps below.**

Data is stored in `backend/data/db.json` (a plain JSON file) and uploaded
screenshots in `backend/uploads/`. This is fine to get started and test with a
handful of drivers; move to a real database (Postgres, MySQL) before you have
more than a few dozen, since concurrent writes to a single JSON file aren't
safe at scale.

**Deploying:** any Node host works (a small VPS, Render, Railway, etc.). Set
`JWT_SECRET`, `ADMIN_DEFAULT_PASSWORD`, `CBE_ACCOUNT`, and `TELEBIRR_ACCOUNT`
as real environment variables there — don't ship your `.env` file.

### API summary

| Endpoint | Who | What |
|---|---|---|
| `POST /api/driver/register` | driver | create account, returns token + deposit instructions |
| `POST /api/driver/login` | driver | phone + password → token |
| `GET /api/driver/me` | driver | current status |
| `POST /api/driver/deposit` | driver | multipart upload of the deposit screenshot |
| `POST /api/payments/log` | driver | logs a parsed Telebirr SMS (called automatically by the app) |
| `GET /api/driver/payments` | driver | this driver's payment history |
| `POST /api/admin/login` | admin | username + password → token |
| `GET /api/admin/drivers?status=` | admin | list drivers, optionally filtered |
| `POST /api/admin/drivers/:id/approve` | admin | approve a pending driver |
| `POST /api/admin/drivers/:id/reject` | admin | reject, with an optional `note` |

Driver statuses: `pending_deposit` → `pending_approval` → `approved` | `rejected`.

I ran the full flow (register → deposit → admin approve → SMS logged) against
this server while building it — it works as described above.

## 2. Admin web

Plain HTML/CSS/JS, no build step needed.

```bash
cd admin-web
python3 -m http.server 8080
# then open http://localhost:8080
```

It talks to the backend at `http://localhost:4000` by default. To point it at
a deployed backend, either edit `API_BASE` at the top of `app.js`, or set
`window.PAYMENT_ANNOUNCER_API_BASE` before `app.js` loads (e.g. add a small
`<script>window.PAYMENT_ANNOUNCER_API_BASE = "https://your-api.example.com";</script>`
tag above the `app.js` `<script>` tag in `index.html`).

Deploy it anywhere that serves static files (Netlify, Vercel, GitHub Pages, or
the same server as the backend).

## 3. Android app

This is a full Kotlin source tree, structured as a normal Gradle Android
project — but it wasn't compiled here (this sandbox has no Android SDK, and
network access here is restricted to package registries, not Google's SDK
manager). To build it:

1. Open the `android-app/` folder in Android Studio.
2. Let Gradle sync — it'll download the Android Gradle Plugin and the
   dependencies listed in `app/build.gradle` (Retrofit, OkHttp, coroutines).
3. In `app/build.gradle`, update `API_BASE_URL`:
   - `http://10.0.2.2:4000` reaches your machine's localhost **from the emulator only**.
   - For a real phone, use your backend's real deployed URL (and switch it to `https://`).
4. Run on an emulator or a real device.

**What's implemented:**
- `LoginActivity` / `RegisterActivity` — driver auth against the backend
- `DepositActivity` — shows the live deposit amount/accounts from the backend, lets the driver pick and upload a screenshot
- `StatusActivity` — polls `/api/driver/me` and routes to the right screen (waiting / approved / rejected)
- `PaymentActivity` — the live screen: big sender-name text, English/Amharic toggle, text-to-speech announcement, repeat button
- `SmsReceiver` — a `BroadcastReceiver` that filters incoming SMS for Telebirr's short code `127`, parses it with `TelebirrSmsParser`, updates the live screen, and logs the payment to the backend
- `TelebirrSmsParser` — regex parser matching the exact SMS format you provided, mirrored from `backend/smsParser.js`

**Before this is store-ready:**
- **Runtime permission prompts.** The manifest declares `RECEIVE_SMS`/`READ_SMS`, but Android 6+ requires asking for these at runtime with a visible dialog — not yet wired up in these activities. Without it, `SmsReceiver` simply won't fire.
- **Play Store SMS policy.** Google restricts apps that read SMS to a small set of approved categories, and payment-verification apps generally do *not* qualify for the default `RECEIVE_SMS`/`READ_SMS` permissions — you'll likely need to apply for the special "SMS or Call Log permission" exception, or distribute the app outside the Play Store (direct APK, since this is an internal tool for your own drivers rather than a public consumer app).
- **Notification when the app isn't open.** Right now `PaymentEventBus` only updates the screen if `PaymentActivity` is on-screen. A driver who has the app in the background will miss it. Worth adding a foreground service + notification with sound, so it works even when the phone is just sitting in a mount with the screen off.
- **Amharic voice quality.** Android's built-in TTS falls back to English if the device doesn't have an Amharic voice pack installed (common on cheaper phones). Worth testing on the actual devices your drivers use, and possibly bundling a pre-recorded audio approach (numbers 0–999 + common name fragments) as a fallback if native Amharic TTS isn't reliable enough.
- **App icon / launcher assets** aren't included — Android Studio can generate placeholders via *New > Image Asset*.

## Known gaps across the whole system

- No password reset flow for drivers or the admin.
- No rate limiting on login endpoints.
- Single hardcoded admin account — fine for one person reviewing deposits, but if you want more than one admin reviewing, add real admin accounts with their own logins instead of sharing one.
- The 12-hour approval SLA is currently just a promise in the UI text — there's no automatic reminder/escalation if an admin hasn't acted within that window yet.
