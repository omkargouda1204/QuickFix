# QuickFix Backend Connection Fix for Real Phone

## ✅ Changes Already Applied

1. **Updated `ApiClient.java`**
   - Changed `BASE_URL` from `http://10.0.2.2:5000` to `http://10.249.219.177:5000`

2. **Updated `network_security_config.xml`**
   - Changed domain from `10.0.2.2` to `10.249.219.177`
   - Allows cleartext HTTP traffic to your PC

3. **Backend is running**
   - Port 5000 is listening on all interfaces (0.0.0.0:5000)

---

## ⚠️ ACTION REQUIRED: Allow Firewall Access

**You need to allow Python through Windows Firewall** (requires Admin privileges):

### Option 1 — Run this command in PowerShell as Administrator:

```powershell
netsh advfirewall firewall add rule name="Python QuickFix Backend" dir=in action=allow program="C:\Users\Omkar Gouda\AppData\Local\Programs\Python\Python312\python.exe" enable=yes
```

### Option 2 — Manual Windows Firewall Setup:

1. Press `Win + R` → type `wf.msc` → press Enter
2. Click **"Inbound Rules"** → **"New Rule..."**
3. Select **"Program"** → Next
4. Browse to: `C:\Users\Omkar Gouda\AppData\Local\Programs\Python\Python312\python.exe`
5. Select **"Allow the connection"** → Next
6. Check all (Domain, Private, Public) → Next
7. Name: `Python QuickFix Backend` → Finish

---

## 📱 After Firewall Fix

1. **Rebuild Android app** (to pick up new `ApiClient.BASE_URL`)
2. **Reinstall on your phone**
3. **Make sure phone and PC are on same Wi-Fi**
4. **Test Sign Up** → real OTP email should arrive
5. **Test Forgot Password** → real OTP email should arrive

---

## 🧪 Test Backend Access from Phone

Once firewall is configured, open Chrome on your phone and visit:
```
http://10.249.219.177:5000
```

You should see:
```json
{"status":"ok","message":"QuickFix backend is running","version":"1.0.0"}
```

If you see this, the backend is accessible from your phone. ✅

---

## 🐛 Troubleshooting

### If connection still fails:

1. **Disable Windows Firewall temporarily** (for testing only):
   - Control Panel → Windows Defender Firewall → Turn Windows Defender Firewall on or off
   - Turn OFF for Private networks
   - Test app
   - **Turn it back ON** after testing

2. **Check if backend is still running:**
   ```powershell
   netstat -an | Select-String "5000"
   ```
   You should see: `TCP    0.0.0.0:5000 ... LISTENING`

3. **Restart backend if needed:**
   ```powershell
   cd D:\QuickFix\backend
   python run.py
   ```

4. **Verify phone is on same Wi-Fi:**
   - Phone Wi-Fi settings → should show same network as PC

---

## 📧 Backend Email Settings

Make sure your `.env` has real Gmail credentials:

```env
SMTP_USER=yourmail@gmail.com
SMTP_PASSWORD=xxxx xxxx xxxx xxxx   ← 16-char App Password
```

Generate App Password: https://myaccount.google.com/apppasswords

---

## 🎯 Summary

- ✅ Android code updated to use `10.249.219.177:5000`
- ✅ Backend running on port 5000
- ⏳ **YOU MUST**: Allow Python through Windows Firewall (see steps above)
- ⏳ **YOU MUST**: Rebuild and reinstall Android app

Once firewall is configured and app is rebuilt, Sign Up and Forgot Password will send real OTP emails via Gmail SMTP.

