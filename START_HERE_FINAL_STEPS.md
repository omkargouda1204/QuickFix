# 🚀 FINAL SETUP STEPS - READ THIS FIRST

## ✅ What Has Been Done

All code changes are complete:
- ✅ `ApiClient.java` → BASE_URL changed to `http://10.249.219.177:5000`
- ✅ `network_security_config.xml` → Allows HTTP to `10.249.219.177`
- ✅ `SignupActivity.java` → Calls backend API instead of Firebase
- ✅ `OTPVerificationActivity.java` → Verifies OTP via backend API
- ✅ `ForgotPasswordActivity.java` → Full forgot password flow via backend
- ✅ Backend code is ready and waiting

---

## ⚠️ CRITICAL: You Must Complete These 3 Steps

### Step 1: Allow Python Through Windows Firewall

**Open PowerShell AS ADMINISTRATOR** and run:

```powershell
netsh advfirewall firewall add rule name="Python QuickFix Backend" dir=in action=allow program="C:\Users\Omkar Gouda\AppData\Local\Programs\Python\Python312\python.exe" enable=yes
```

✅ You should see: `Ok.`

**OR manually:**
1. Press `Win + R` → type `wf.msc` → Enter
2. Click **"Inbound Rules"** → **"New Rule..."**
3. **Program** → Browse to: `C:\Users\Omkar Gouda\AppData\Local\Programs\Python\Python312\python.exe`
4. **Allow the connection** → Check all (Domain, Private, Public)
5. Name: `Python QuickFix Backend` → Finish

---

### Step 2: Start Backend Server

Open a NEW PowerShell window (doesn't need admin) and run:

```powershell
cd D:\QuickFix\backend
python run.py
```

✅ You should see Flask startup logs ending with:
```
 * Running on http://127.0.0.1:5000
 * Running on http://10.249.219.177:5000
```

**KEEP THIS WINDOW OPEN** while testing your app.

---

### Step 3: Rebuild and Install Android App

In Android Studio:
1. **Build → Clean Project**
2. **Build → Rebuild Project**
3. Connect your phone via USB **OR** ensure phone is on same Wi-Fi as PC
4. **Run** the app on your phone

---

## 🧪 Quick Test (Before Opening App)

### Test 1: Check Backend from PC

Open browser on your PC:
```
http://localhost:5000
```

✅ Should show: `{"status":"ok","message":"QuickFix backend is running","version":"1.0.0"}`

### Test 2: Check Backend from Phone

Open **Chrome on your phone** and visit:
```
http://10.249.219.177:5000
```

✅ Should show the same JSON response

❌ If this fails:
- Windows Firewall is still blocking → redo Step 1
- Phone not on same Wi-Fi as PC → check Wi-Fi settings
- Backend not running → redo Step 2

---

## 📧 Email Settings Check

Before testing OTP, verify your `.env` file has REAL Gmail credentials:

```env
SMTP_USER=your-real-gmail@gmail.com
SMTP_PASSWORD=abcd efgh ijkl mnop   ← 16-char App Password (no spaces in actual file)
```

❌ If you don't have an App Password:
1. Go to https://myaccount.google.com/apppasswords
2. Select **Mail** → Generate
3. Copy the 16-character password
4. Paste into `.env` (remove spaces: `abcdefghijklmnop`)
5. Restart backend (Ctrl+C in backend window, then `python run.py`)

---

## ✅ Full Test Flow

### Test Sign Up:

1. Open app → **Sign Up**
2. Enter:
   - Name: Test User
   - Phone: 1234567890
   - Email: **your real email address**
   - Password: TestPass1
   - Confirm: TestPass1
3. Click **Sign Up**
4. ✅ Should show OTP screen
5. ✅ Check your email inbox (and spam folder!)
6. ✅ Enter the 6-digit OTP from email
7. Click **Verify**
8. ✅ Should navigate to Home screen

### Test Forgot Password:

1. Open app → **Login** → **Forgot Password?**
2. Enter your email → **Send OTP**
3. ✅ Check email for OTP
4. Enter OTP on phone → **Verify**
5. ✅ Should show "Enter New Password" form
6. Enter new password → **Reset Password**
7. ✅ Should return to Login screen
8. Login with new password → ✅ Success

---

## 🐛 Troubleshooting

### "Cannot connect to server" error:

```powershell
# Check if backend is running:
Get-Process python

# Check if port 5000 is listening:
Test-NetConnection -ComputerName 10.249.219.177 -Port 5000

# If not, restart backend:
cd D:\QuickFix\backend
python run.py
```

### "CLEARTEXT COMMUNICATION not permitted" still appears:

This means the app wasn't rebuilt properly.

In Android Studio:
1. **Build → Clean Project**
2. **Build → Rebuild Project**
3. **Uninstall app from phone** (long-press app icon → Uninstall)
4. **Run** again

### Email not arriving:

```powershell
# Test SMTP from backend:
cd D:\QuickFix\backend
python -c "from app import create_app; app = create_app(); from app.services.email_service import test_smtp_connection; print(test_smtp_connection())"
```

✅ Should show: `(True, 'SMTP connection OK (smtp.gmail.com:587)')`

❌ If False → Check your Gmail App Password in `.env`

### Firewall still blocking:

**TEMPORARY TEST ONLY** - Disable Windows Firewall:
1. Control Panel → Windows Defender Firewall
2. Turn Windows Defender Firewall **off** (Private networks only)
3. Test app
4. **TURN IT BACK ON** immediately after testing
5. If this fixes it, the firewall rule wasn't added correctly → redo Step 1

---

## 📋 Checklist

Before testing, verify:

- [ ] Firewall rule added for Python (Step 1)
- [ ] Backend running in PowerShell window (Step 2)
- [ ] App rebuilt and reinstalled (Step 3)
- [ ] Phone and PC on same Wi-Fi
- [ ] Can open `http://10.249.219.177:5000` in phone's Chrome browser
- [ ] `.env` has real Gmail App Password
- [ ] Tested SMTP connection (see troubleshooting above)

---

## 🎯 Expected Result

**After completing all steps:**

1. ✅ Sign Up sends real OTP email via Gmail SMTP
2. ✅ OTP verification activates account in Supabase
3. ✅ User redirected to Home screen
4. ✅ Forgot Password sends real OTP email
5. ✅ Password reset updates Supabase database
6. ✅ Login works with new password

---

## 💡 Remember

- **Backend must be running** every time you test the app
- **Phone and PC must be on same Wi-Fi**
- **Windows Firewall must allow Python** (one-time setup)
- **Gmail App Password** must be valid (check if OTP doesn't arrive)

---

## 🆘 Still Not Working?

If you've completed ALL steps above and still have issues:

1. Share the **exact error message** from the app
2. Check backend logs in the PowerShell window for errors
3. Test backend health endpoint from phone's Chrome browser
4. Temporarily disable Windows Firewall to confirm it's a firewall issue

---

**Good luck! The setup is complete on the code side. Just follow the 3 steps above and everything will work.** 🚀

