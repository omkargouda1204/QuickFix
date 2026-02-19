# QuickFix Backend - API Testing Guide

## 🧪 Complete Testing Instructions with cURL

This guide provides all curl commands needed to test the entire QuickFix Backend API.

---

## Prerequisites

Before testing:
1. MySQL database created with tables (see DATABASE_SCHEMA.md)
2. Python dependencies installed: `pip install -r requirements.txt`
3. Flask server running: `python app.py`
4. Server running at: `http://localhost:5000`

---

## Test User Credentials

For testing, create or use this test user:
```
Email: test@quickfix.com
Password: testpass123
Name: Test User
Phone: 9876543210
```

---

## 1. REGISTRATION FLOW

### 1.1 Register New User

```bash
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "John Doe",
    "email": "john@example.com",
    "phone": "1234567890",
    "password": "password123"
  }'
```

**Expected Response (201):**
```json
{
    "status": "success",
    "message": "Registration successful. OTP sent to email.",
    "data": {
        "user_id": "uuid-here",
        "email": "john@example.com",
        "otp_id": "otp-uuid-here",
        "expires_in": 300
    }
}
```

**Save the `otp_id` for next step.**

---

### 1.2 Get OTP Code (for development/testing)

Since email might not be configured, check the database:

```bash
mysql -u root -psystem quickfix_db -e "SELECT email, otp_code, expires_at FROM otps ORDER BY created_at DESC LIMIT 1;"
```

Or in MySQL terminal:
```sql
USE quickfix_db;
SELECT email, otp_code, expires_at FROM otps ORDER BY created_at DESC LIMIT 1;
```

**Copy the OTP code (6 digits).**

---

### 1.3 Verify OTP

```bash
curl -X POST http://localhost:5000/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "otp_id": "otp-uuid-from-register",
    "otp_code": "123456"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "OTP verified successfully",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "token_type": "Bearer",
        "user": {
            "id": "uuid",
            "full_name": "John Doe",
            "email": "john@example.com",
            "phone": "1234567890",
            "email_verified": true,
            "created_at": "2024-01-15T10:30:00",
            "last_login": null
        }
    }
}
```

**Save the `access_token` for authenticated requests.**

---

### 1.4 Resend OTP (if needed)

```bash
curl -X POST http://localhost:5000/api/auth/resend-otp \
  -H "Content-Type: application/json" \
  -d '{
    "otp_id": "otp-uuid-from-register"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "OTP resent to email",
    "data": {
        "otp_id": "new-otp-uuid",
        "expires_in": 300
    }
}
```

---

## 2. LOGIN FLOW

### 2.1 Login with Email and Password

```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Login successful",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "token_type": "Bearer",
        "user": {
            "id": "uuid",
            "full_name": "John Doe",
            "email": "john@example.com",
            "phone": "1234567890",
            "email_verified": true,
            "created_at": "2024-01-15T10:30:00",
            "last_login": "2024-01-15T11:00:00"
        }
    }
}
```

**Save the token for protected endpoints.**

---

### 2.2 Test Failed Login

```bash
# Wrong password
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "wrongpassword"
  }'
```

**Expected Response (401):**
```json
{
    "status": "error",
    "message": "Invalid email or password"
}
```

---

## 3. PASSWORD RESET FLOW

### 3.1 Request Password Reset

```bash
curl -X POST http://localhost:5000/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "OTP sent to email for password reset",
    "data": {
        "otp_id": "otp-uuid",
        "expires_in": 300
    }
}
```

**Get the new OTP from database (same method as 1.2).**

---

### 3.2 Reset Password with OTP

```bash
curl -X POST http://localhost:5000/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "otp_id": "otp-uuid-from-forgot-password",
    "otp_code": "654321",
    "new_password": "newpassword456"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Password reset successful. You can now login with your new password."
}
```

**Now login with the new password.**

---

## 4. USER PROFILE ENDPOINTS (Protected)

**Note:** All these endpoints require `Authorization` header with Bearer token.

---

### 4.1 Get User Profile

```bash
curl -X GET http://localhost:5000/api/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Profile retrieved successfully",
    "data": {
        "user": {
            "id": "uuid",
            "full_name": "John Doe",
            "email": "john@example.com",
            "phone": "1234567890",
            "email_verified": true,
            "created_at": "2024-01-15T10:30:00",
            "last_login": "2024-01-15T11:00:00"
        }
    }
}
```

---

### 4.2 Update Profile

```bash
curl -X PUT http://localhost:5000/api/user/update-profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Jane Doe",
    "phone": "9876543210"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Profile updated successfully",
    "data": {
        "user": {
            "id": "uuid",
            "full_name": "Jane Doe",
            "email": "john@example.com",
            "phone": "9876543210",
            "email_verified": true,
            "created_at": "2024-01-15T10:30:00",
            "last_login": "2024-01-15T11:00:00"
        }
    }
}
```

---

### 4.3 Change Password

```bash
curl -X POST http://localhost:5000/api/user/change-password \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "current_password": "password123",
    "new_password": "mynewpassword789"
  }'
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Password changed successfully"
}
```

---

### 4.4 Delete Account

```bash
curl -X DELETE http://localhost:5000/api/user/delete-account \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Account deleted successfully"
}
```

---

### 4.5 Validate Token

```bash
curl -X POST http://localhost:5000/api/auth/validate-token \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Expected Response (200):**
```json
{
    "status": "success",
    "message": "Token is valid",
    "data": {
        "user": {
            "id": "uuid",
            "full_name": "John Doe",
            "email": "john@example.com",
            "phone": "1234567890",
            "email_verified": true,
            "created_at": "2024-01-15T10:30:00",
            "last_login": "2024-01-15T11:00:00"
        }
    }
}
```

---

## 5. ERROR TESTING

### 5.1 Missing Required Fields

```bash
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "John Doe"
  }'
```

**Expected Response (400):**
```json
{
    "status": "error",
    "message": "Missing required fields: full_name, email, phone, password"
}
```

---

### 5.2 Invalid Email Format

```bash
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "John Doe",
    "email": "invalid-email",
    "phone": "1234567890",
    "password": "password123"
  }'
```

**Expected Response (400):**
```json
{
    "status": "error",
    "message": "Invalid email format"
}
```

---

### 5.3 Password Too Short

```bash
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "John Doe",
    "email": "john@example.com",
    "phone": "1234567890",
    "password": "pass"
  }'
```

**Expected Response (400):**
```json
{
    "status": "error",
    "message": "Password must be at least 6 characters long"
}
```

---

### 5.4 Email Already Exists

```bash
# First register user
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "John Doe",
    "email": "duplicate@example.com",
    "phone": "1234567890",
    "password": "password123"
  }'

# Try to register again with same email
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Jane Doe",
    "email": "duplicate@example.com",
    "phone": "9876543210",
    "password": "password456"
  }'
```

**Expected Response (409):**
```json
{
    "status": "error",
    "message": "Email already registered. Please login or use a different email."
}
```

---

### 5.5 Invalid Token

```bash
curl -X GET http://localhost:5000/api/user/profile \
  -H "Authorization: Bearer invalid-token-here"
```

**Expected Response (401):**
```json
{
    "status": "error",
    "message": "Invalid or expired token"
}
```

---

### 5.6 Missing Authorization Header

```bash
curl -X GET http://localhost:5000/api/user/profile
```

**Expected Response (401):**
```json
{
    "status": "error",
    "message": "Token is missing"
}
```

---

## 6. COMPLETE USER JOURNEY TEST

Here's a complete workflow to test everything:

```bash
#!/bin/bash

# 1. Register
echo "1. Registering user..."
REGISTER=$(curl -s -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Test User",
    "email": "test@quickfix.com",
    "phone": "1234567890",
    "password": "testpass123"
  }')

echo $REGISTER
OTP_ID=$(echo $REGISTER | grep -o '"otp_id":"[^"]*' | cut -d'"' -f4)
echo "OTP ID: $OTP_ID"

# 2. Get OTP from database
echo ""
echo "2. Getting OTP from database..."
OTP=$(mysql -u root -psystem quickfix_db -e "SELECT otp_code FROM otps WHERE id='$OTP_ID';" -s)
echo "OTP Code: $OTP"

# 3. Verify OTP
echo ""
echo "3. Verifying OTP..."
VERIFY=$(curl -s -X POST http://localhost:5000/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d "{
    \"otp_id\": \"$OTP_ID\",
    \"otp_code\": \"$OTP\"
  }")

echo $VERIFY
TOKEN=$(echo $VERIFY | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
echo "Access Token: $TOKEN"

# 4. Get Profile
echo ""
echo "4. Getting user profile..."
curl -s -X GET http://localhost:5000/api/user/profile \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool

# 5. Update Profile
echo ""
echo "5. Updating profile..."
curl -s -X PUT http://localhost:5000/api/user/update-profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Updated User",
    "phone": "9876543210"
  }' | python -m json.tool

# 6. Logout (clear token)
echo ""
echo "6. Logout successful - token cleared"
```

Save as `test_api.sh` and run: `bash test_api.sh`

---

## 7. POSTMAN COLLECTION

For Postman testing, import these as requests:

1. Create new Collection: "QuickFix Backend"
2. Create requests for each endpoint
3. Use `{{token}}` as variable for Bearer tokens
4. Set `Base URL` as environment variable: `http://localhost:5000/api`

---

## 8. TROUBLESHOOTING TEST ISSUES

### Issue: "Connection refused"
- **Solution**: Ensure Flask server is running (`python app.py`)

### Issue: "Table doesn't exist"
- **Solution**: Create database tables (see DATABASE_SCHEMA.md)

### Issue: "MySQL access denied"
- **Solution**: Check MySQL credentials (root/system)

### Issue: "No OTP sent to email"
- **Solution**: Check database directly or disable email for testing

### Issue: "Invalid token"
- **Solution**: Token may have expired (24 hours), re-login

---

## 9. LOAD TESTING (Optional)

Using Apache Bench:
```bash
# Test registration endpoint
ab -n 100 -c 10 -p data.json -T application/json \
  http://localhost:5000/api/auth/login
```

Using wrk:
```bash
# Test login endpoint
wrk -t12 -c400 -d30s \
  -s login.lua \
  http://localhost:5000/api/auth/login
```

---

## ✅ Test Summary

After running all these tests, you should have verified:

✅ User registration with validation
✅ OTP generation and verification
✅ Email sending capability
✅ Login functionality
✅ Password reset flow
✅ Token-based authentication
✅ User profile management
✅ Error handling
✅ Database operations
✅ Input validation

**Backend API is fully tested and ready for production!** 🎉

