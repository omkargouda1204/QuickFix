# QuickFix Backend — Authentication API

Flask + Supabase + Gmail SMTP authentication backend.

---

## 📁 Project Structure

```
backend/
├── .env                          ← Secrets (never commit)
├── .gitignore
├── requirements.txt
├── run.py                        ← Entry point
├── schema.sql                    ← Run once in Supabase SQL Editor
├── app/
│   ├── __init__.py               ← App factory
│   ├── config.py                 ← Settings from .env
│   ├── models/
│   │   ├── db.py                 ← Supabase client alias
│   │   └── user_model.py         ← All DB operations for users table
│   ├── routes/
│   │   └── auth_routes.py        ← All API endpoints
│   ├── services/
│   │   ├── supabase_client.py    ← Lazy Supabase client
│   │   ├── email_service.py      ← Gmail SMTP OTP sender
│   │   └── otp_service.py        ← OTP generate / store / verify
│   └── utils/
│       ├── password_utils.py     ← bcrypt hash & verify
│       ├── jwt_utils.py          ← JWT create & decode
│       ├── validators.py         ← Email / phone / password rules
│       └── response_utils.py     ← Standard JSON responses
```

---

## ⚙️ Setup

### 1. Create Supabase Project
1. Go to https://supabase.com and create a project
2. Go to **SQL Editor** → paste the full contents of `schema.sql` → Run
3. Go to **Settings → API** and copy:
   - `Project URL` → `SUPABASE_URL`
   - `service_role` secret key → `SUPABASE_SERVICE_ROLE_KEY`

### 2. Configure Gmail SMTP
1. Enable 2-Step Verification on your Google account
2. Go to https://myaccount.google.com/apppasswords
3. Create an App Password for "Mail"
4. Copy the 16-character password → `SMTP_PASSWORD`

### 3. Fill in `.env`
```env
FLASK_ENV=development
FLASK_DEBUG=1
SECRET_KEY=your-long-random-secret

SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-gmail@gmail.com
SMTP_PASSWORD=your-16-char-app-password
EMAIL_FROM_NAME=QuickFix
EMAIL_FROM=your-gmail@gmail.com

OTP_EXPIRY_MINUTES=10
JWT_SECRET_KEY=your-long-random-jwt-secret
JWT_ACCESS_TOKEN_EXPIRES_HOURS=24
```

### 4. Install Dependencies
```bash
pip install -r requirements.txt
```

### 5. Run
```bash
python run.py
```
Server starts at: `http://localhost:5000`

---

## 🔌 API Endpoints

Base URL: `http://localhost:5000/api/auth`

### POST `/signup`
Register a new user. Sends OTP to email.
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "9876543210",
  "password": "SecurePass1",
  "role": "user"
}
```
**Response 201:**
```json
{ "success": true, "message": "Account created! A 10-minute OTP has been sent to ...", "data": { "email": "john@example.com" } }
```

---

### POST `/send-otp`
Send or resend an OTP.
```json
{ "email": "john@example.com", "purpose": "verification" }
```
`purpose`: `"verification"` | `"password_reset"`

---

### POST `/verify-otp`
Verify OTP. For `verification` purpose, activates the account and returns a JWT.
```json
{ "email": "john@example.com", "otp": "123456", "purpose": "verification" }
```
**Response 200:**
```json
{
  "success": true,
  "data": {
    "token": "eyJ...",
    "user": { "id": "...", "name": "John Doe", "email": "...", "role": "user", "is_verified": true }
  }
}
```

---

### POST `/login`
Login with email and password.
```json
{ "email": "john@example.com", "password": "SecurePass1" }
```
**Response 200:**
```json
{ "success": true, "data": { "token": "eyJ...", "user": { ... } } }
```

---

### POST `/forgot-password`
Send a password-reset OTP to the email.
```json
{ "email": "john@example.com" }
```
Always returns 200 (prevents email enumeration).

---

### POST `/reset-password`
Verify reset OTP and set a new password.
```json
{ "email": "john@example.com", "otp": "654321", "new_password": "NewPass1" }
```

---

### GET `/me`
Get the current user's profile. Requires JWT.
```
Authorization: Bearer eyJ...
```
**Response 200:**
```json
{ "success": true, "data": { "user": { "id": "...", "name": "...", ... } } }
```

---

## 🔐 Security

| Feature | Implementation |
|---|---|
| Password storage | bcrypt (cost=12) — never stored plain |
| OTP expiry | Configurable via `OTP_EXPIRY_MINUTES` (default 10 min) |
| OTP reuse | Marked `is_used=true` after verification |
| OTP invalidation | Old unused OTPs invalidated when new one is sent |
| JWT | HS256, configurable expiry (default 24h) |
| Email enumeration | `/forgot-password` always returns 200 |
| Database access | Service-role key + RLS enabled (no public access) |
| Secrets | All in `.env`, never in code |

---

## 🗄️ Database Tables (Supabase)

### `users`
| Column | Type | Description |
|---|---|---|
| id | UUID | Primary key |
| name | TEXT | Full name |
| email | TEXT | Unique email |
| phone | TEXT | Phone number |
| password_hash | TEXT | bcrypt hash |
| role | TEXT | `user` or `provider` |
| is_verified | BOOLEAN | Email verified flag |
| created_at | TIMESTAMPTZ | Registration time |
| updated_at | TIMESTAMPTZ | Last update time |

### `otp_codes`
| Column | Type | Description |
|---|---|---|
| id | UUID | Primary key |
| email | TEXT | Target email |
| otp_code | TEXT | 6-digit code |
| purpose | TEXT | `verification` or `password_reset` |
| is_used | BOOLEAN | Used/consumed flag |
| expires_at | TIMESTAMPTZ | Expiry timestamp |
| created_at | TIMESTAMPTZ | Creation time |

---

## 📱 Android Integration

In your Android app, call the backend like:

```
POST http://<your-server-ip>:5000/api/auth/signup
POST http://<your-server-ip>:5000/api/auth/login
POST http://<your-server-ip>:5000/api/auth/send-otp
POST http://<your-server-ip>:5000/api/auth/verify-otp
POST http://<your-server-ip>:5000/api/auth/forgot-password
POST http://<your-server-ip>:5000/api/auth/reset-password
GET  http://<your-server-ip>:5000/api/auth/me
```

For local device testing: `http://10.0.2.2:5000` (Android emulator) or your machine's local IP.

