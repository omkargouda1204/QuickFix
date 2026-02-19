# 🔴 CRITICAL FIX REQUIRED: Wrong Supabase Key

## Problem Detected

Your `.env` file has:
```
SUPABASE_SERVICE_ROLE_KEY=sb_publishable_ItRhFA3FRjef6cVFKR2cAQ_HsTVzvN3
```

This is the **ANON/PUBLIC KEY** — it has restricted access and causes "Invalid API key" errors.

---

## ✅ Solution: Get the Service Role Key

### Step 1: Go to Supabase Dashboard

Open: https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/settings/api

(Or manually: Supabase Dashboard → Your Project → Settings → API)

### Step 2: Find the Service Role Key

Scroll down to **Project API keys** section.

You will see TWO keys:

```
┌─────────────────────────────────────────────────┐
│  anon / public                                  │
│  sb_publishable_ItRhFA3FR... ← DON'T USE THIS  │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│  service_role                                   │
│  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc... │
│  ← USE THIS ONE (very long, starts with eyJ)   │
└─────────────────────────────────────────────────┘
```

### Step 3: Copy the FULL Service Role Key

1. Click **"Reveal"** next to `service_role`
2. Click **"Copy"** to copy the ENTIRE key
   - It's very long (500+ characters)
   - Starts with `eyJ...`
   - It's a JWT token

### Step 4: Update Your `.env` File

Open: `D:\QuickFix\backend\.env`

Replace line 12:
```diff
- SUPABASE_SERVICE_ROLE_KEY=sb_publishable_ItRhFA3FRjef6cVFKR2cAQ_HsTVzvN3
+ SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkZHp5Y3F4dGdyeXJweW1pdmZkIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTY0NDg2NzYwMCwiZXhwIjoxOTYwNDQzNjAwfQ.YOUR_ACTUAL_SERVICE_ROLE_KEY_HERE
```

(Replace the entire value with YOUR actual service_role key from Supabase)

### Step 5: Restart Backend

```powershell
# Stop current backend (Ctrl+C in the backend window)

# Restart
cd D:\QuickFix\backend
python run.py
```

### Step 6: Test Again

Sign up with a new email → Should work! ✅

---

## Quick Verification

After updating `.env`, run this to verify:

```powershell
cd D:\QuickFix\backend
python -c "from supabase import create_client; import os; from dotenv import load_dotenv; load_dotenv(); client = create_client(os.getenv('SUPABASE_URL'), os.getenv('SUPABASE_SERVICE_ROLE_KEY')); result = client.table('users').select('id').limit(1).execute(); print('SUCCESS: Supabase connected!')"
```

✅ Should print: `SUCCESS: Supabase connected!`

❌ If error: Double-check you copied the FULL service_role key

---

## Why This Matters

| Key Type | Access Level | Used For |
|---|---|---|
| `anon` (public) | Restricted by RLS | Frontend apps (browser/mobile) |
| `service_role` | **FULL ACCESS** | Backend servers (bypasses RLS) |

Your backend needs **full database access** to create users, store OTPs, etc.
The anon key is blocked by Row-Level Security policies.

---

## After Fixing

All these errors will be gone:
```
❌ get_user_by_email failed: Invalid API key
❌ email_exists check failed: Invalid API key  
❌ create_user failed: Invalid API key
```

✅ Will become:
```
✅ User created successfully
✅ OTP stored in database
✅ Email sent via SMTP
```

---

**Go to Supabase now and get your service_role key!**

https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/settings/api

