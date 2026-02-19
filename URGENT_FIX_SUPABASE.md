# 🚨 IMMEDIATE ACTION REQUIRED

## Your Error:
```
ERROR: Invalid API key
- get_user_by_email failed
- email_exists check failed  
- create_user failed
```

## Root Cause:
Your `.env` file uses the **wrong Supabase key**.

---

# ✅ 2-STEP FIX (5 minutes)

## STEP 1: Get the Correct Supabase Key

### 1.1 Open Supabase API Settings
Click this link (or go manually):
**https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/settings/api**

### 1.2 Find "Project API keys" Section
Scroll down. You'll see two keys:

**DON'T USE THIS ONE:**
```
anon / public
sb_publishable_ItRhFA... ← You have this now (WRONG)
```

**USE THIS ONE:**
```
service_role  
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJl... 
↑ Very long (500+ chars), starts with "eyJ"
```

### 1.3 Copy the Service Role Key
1. Click **"Reveal"** next to `service_role`
2. Click **"Copy"** 
3. ⚠️ Copy the ENTIRE key (it's very long!)

---

## STEP 2: Update Your .env File

### 2.1 Open File
Open in any text editor:
```
D:\QuickFix\backend\.env
```

### 2.2 Replace Line 12
**BEFORE:**
```env
SUPABASE_SERVICE_ROLE_KEY=sb_publishable_ItRhFA3FRjef6cVFKR2cAQ_HsTVzvN3
```

**AFTER:**
```env
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3M...PASTE_YOUR_FULL_KEY_HERE
```

⚠️ Make sure you paste the COMPLETE key (no line breaks!)

### 2.3 Save the File
Ctrl+S to save.

---

## STEP 3: Restart Backend

In your backend PowerShell window:

1. **Press Ctrl+C** to stop the server
2. Run again:
```powershell
cd D:\QuickFix\backend
python run.py
```

---

## STEP 4: Test in App

1. Open QuickFix app on your phone
2. Sign up with a new email
3. ✅ Should work now!

---

# 📋 OPTIONAL: Run Database Schema (If Tables Don't Exist)

If after fixing the key you get "table does not exist" errors:

1. Go to: https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/editor
2. Click **SQL Editor** (left sidebar)
3. Click **"+ New query"**
4. Open `D:\QuickFix\backend\schema.sql` in Notepad
5. Copy ALL the content
6. Paste into Supabase SQL Editor
7. Click **"Run"**
8. ✅ Should see: "Success. No rows returned"

This creates the `users` and `otp_codes` tables.

---

# 🧪 Quick Verification Test

After updating `.env` and restarting backend, run this in PowerShell:

```powershell
cd D:\QuickFix\backend
python -c "from supabase import create_client; import os; from dotenv import load_dotenv; load_dotenv(); client = create_client(os.getenv('SUPABASE_URL'), os.getenv('SUPABASE_SERVICE_ROLE_KEY')); print('Testing...'); result = client.table('users').select('id').limit(1).execute(); print('SUCCESS! Supabase is connected.')"
```

✅ **Should print:** `SUCCESS! Supabase is connected.`

❌ **If error:** 
- "Invalid API key" → You didn't paste the full service_role key
- "relation 'users' does not exist" → Run schema.sql (see above)

---

# 📊 Summary

| Issue | Fix |
|---|---|
| Using `sb_publishable_...` key | Use `eyJ...` service_role key |
| "Invalid API key" errors | Replace key in `.env` + restart |
| Tables don't exist | Run `schema.sql` in Supabase SQL Editor |

---

**After fixing, all signup/login/OTP features will work!** 🚀

Direct link to get your key:
**https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/settings/api**

