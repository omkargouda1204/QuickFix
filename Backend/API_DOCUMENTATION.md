# QuickFix Backend API - Setup & Usage Guide

## Installation

### 1. Prerequisites
- Python 3.8+
- MySQL Server running locally
- pip (Python package manager)

### 2. Create Database
Run these SQL commands in MySQL:

```sql
CREATE DATABASE IF NOT EXISTS quickfix_db;
USE quickfix_db;

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(120) UNIQUE NOT NULL,
    phone VARCHAR(15) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login DATETIME,
    INDEX idx_email (email),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE otps (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    email VARCHAR(120) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    attempt_count INT DEFAULT 0,
    max_attempts INT DEFAULT 5,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_email (email),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3. Install Python Packages
```bash
cd D:\QuickFix\Backend
pip install -r requirements.txt
```

### 4. Configure Gmail SMTP
To send emails via Gmail:

1. Enable 2-Factor Authentication on your Google Account
2. Generate an App Password:
   - Go to https://myaccount.google.com/apppasswords
   - Select Mail and Windows PC
   - Copy the generated password

3. Create `.env` file in the Backend folder:
```env
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
SECRET_KEY=your-secret-key
JWT_SECRET_KEY=your-jwt-secret-key
```

### 5. Run the Application
```bash
cd D:\QuickFix\Backend
python app.py
```

The server will start at: http://localhost:5000

---

## API Endpoints

### Base URL
```
http://localhost:5000/api
```

### Authentication Endpoints

#### 1. User Registration
**POST** `/auth/register`

Request body:
```json
{
    "full_name": "John Doe",
    "email": "john@example.com",
    "phone": "1234567890",
    "password": "password123"
}
```

Response (Success):
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

#### 2. Verify OTP
**POST** `/auth/verify-otp`

Request body:
```json
{
    "otp_id": "otp-uuid-from-register",
    "otp_code": "123456"
}
```

Response (Success):
```json
{
    "status": "success",
    "message": "OTP verified successfully",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIs...",
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

#### 3. Resend OTP
**POST** `/auth/resend-otp`

Request body:
```json
{
    "otp_id": "otp-uuid-from-register"
}
```

Response (Success):
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

#### 4. Login
**POST** `/auth/login`

Request body:
```json
{
    "email": "john@example.com",
    "password": "password123"
}
```

Response (Success):
```json
{
    "status": "success",
    "message": "Login successful",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIs...",
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

#### 5. Forgot Password
**POST** `/auth/forgot-password`

Request body:
```json
{
    "email": "john@example.com"
}
```

Response (Success):
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

#### 6. Reset Password
**POST** `/auth/reset-password`

Request body:
```json
{
    "otp_id": "otp-uuid-from-forgot-password",
    "otp_code": "123456",
    "new_password": "newpassword123"
}
```

Response (Success):
```json
{
    "status": "success",
    "message": "Password reset successful. You can now login with your new password."
}
```

#### 7. Validate Token
**POST** `/auth/validate-token`

Headers:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Response (Success):
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

### User Endpoints

#### 1. Get User Profile
**GET** `/user/profile`

Headers:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Response (Success):
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

#### 2. Update Profile
**PUT** `/user/update-profile`

Headers:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Request body:
```json
{
    "full_name": "Jane Doe",
    "phone": "9876543210"
}
```

Response (Success):
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

#### 3. Change Password
**POST** `/user/change-password`

Headers:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Request body:
```json
{
    "current_password": "password123",
    "new_password": "newpassword456"
}
```

Response (Success):
```json
{
    "status": "success",
    "message": "Password changed successfully"
}
```

#### 4. Delete Account
**DELETE** `/user/delete-account`

Headers:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Response (Success):
```json
{
    "status": "success",
    "message": "Account deleted successfully"
}
```

---

## Android Integration Example

### 1. User Registration
```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("http://10.0.2.2:5000/api/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService = retrofit.create(ApiService::class.java)

val registrationData = mapOf(
    "full_name" to "John Doe",
    "email" to "john@example.com",
    "phone" to "1234567890",
    "password" to "password123"
)

apiService.register(registrationData).enqueue(object : Callback<RegistrationResponse> {
    override fun onResponse(call: Call<RegistrationResponse>, response: Response<RegistrationResponse>) {
        if (response.isSuccessful) {
            val otpId = response.body()?.data?.otp_id
            val email = response.body()?.data?.email
            // Navigate to OTP verification screen with otpId
        }
    }
    
    override fun onFailure(call: Call<RegistrationResponse>, t: Throwable) {
        // Handle error
    }
})
```

### 2. OTP Verification
```kotlin
val verificationData = mapOf(
    "otp_id" to otpId,
    "otp_code" to "123456"
)

apiService.verifyOtp(verificationData).enqueue(object : Callback<OtpResponse> {
    override fun onResponse(call: Call<OtpResponse>, response: Response<OtpResponse>) {
        if (response.isSuccessful) {
            val accessToken = response.body()?.data?.access_token
            // Save token and navigate to home
            SharedPreferences.saveToken(accessToken)
        }
    }
    
    override fun onFailure(call: Call<OtpResponse>, t: Throwable) {
        // Handle error
    }
})
```

### 3. Login
```kotlin
val loginData = mapOf(
    "email" to "john@example.com",
    "password" to "password123"
)

apiService.login(loginData).enqueue(object : Callback<LoginResponse> {
    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
        if (response.isSuccessful) {
            val accessToken = response.body()?.data?.access_token
            SharedPreferences.saveToken(accessToken)
            navigateToHome()
        } else if (response.code() == 401) {
            showError("Invalid email or password")
        }
    }
    
    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
        showError("Login failed: " + t.message)
    }
})
```

### 4. Getting User Profile with Token
```kotlin
val authHeader = "Bearer " + SharedPreferences.getToken()

apiService.getProfile(authHeader).enqueue(object : Callback<ProfileResponse> {
    override fun onResponse(call: Call<ProfileResponse>, response: Response<ProfileResponse>) {
        if (response.isSuccessful) {
            val user = response.body()?.data?.user
            updateUI(user)
        }
    }
    
    override fun onFailure(call: Call<ProfileResponse>, t: Throwable) {
        // Handle error
    }
})
```

---

## Security Features

1. **Password Hashing**: Uses Werkzeug's bcrypt-based hashing
2. **OTP Expiry**: OTPs expire after 5 minutes
3. **Attempt Limiting**: Maximum 5 OTP verification attempts
4. **JWT Tokens**: Secure token-based authentication
5. **HTTPS Ready**: Can be configured with SSL/TLS
6. **Email Verification**: Required for account activation

## Error Responses

All errors follow this format:

```json
{
    "status": "error",
    "message": "Error description"
}
```

Common HTTP Status Codes:
- 200: Success
- 201: Created
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 409: Conflict (e.g., email already exists)
- 500: Internal Server Error

## Configuration

Edit `config.py` to customize:
- OTP_EXPIRY_TIME: Default 300 seconds (5 minutes)
- OTP_LENGTH: Default 6 digits
- JWT_ACCESS_TOKEN_EXPIRES: Default 24 hours
- Database connection string
- Email configuration

