# QuickFix Backend - Quick Start Guide

## 🚀 Quick Start (5 minutes)

### Step 1: Create Database
Open MySQL and run:
```sql
CREATE DATABASE quickfix_db;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Step 2: Install Dependencies
```bash
cd D:\QuickFix\Backend
pip install -r requirements.txt
```

### Step 3: Configure Email (Optional for Testing)
Create `.env` file (copy from `.env.example`):
```env
MAIL_USERNAME=your-gmail@gmail.com
MAIL_PASSWORD=your-app-password
```

### Step 4: Run the Server
```bash
python app.py
```

Server starts at: `http://localhost:5000`

---

## 📝 Test the API

### Register a User (without OTP email)
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

Response:
```json
{
    "status": "success",
    "message": "Registration successful. OTP sent to email.",
    "data": {
        "user_id": "xyz-123",
        "email": "john@example.com",
        "otp_id": "otp-123",
        "expires_in": 300
    }
}
```

### Verify OTP
(Check server logs or database for the generated OTP code during development)

```bash
curl -X POST http://localhost:5000/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "otp_id": "otp-123",
    "otp_code": "123456"
  }'
```

Response:
```json
{
    "status": "success",
    "message": "OTP verified successfully",
    "data": {
        "access_token": "eyJhbGciOiJIUzI1NiIs...",
        "token_type": "Bearer",
        "user": {...}
    }
}
```

### Login
```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

### Get User Profile (with token)
```bash
curl -X GET http://localhost:5000/api/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

## 🔍 Debug OTP Code During Development

To see the generated OTP code in development:

1. Check Flask server console logs
2. Check the `otps` table in MySQL:
```sql
SELECT email, otp_code, expires_at FROM otps ORDER BY created_at DESC LIMIT 1;
```

---

## 📁 Project Structure

```
Backend/
├── app.py                  # Main Flask app
├── config.py              # Configuration settings
├── models.py              # Database models
├── auth_utils.py          # Authentication utilities
├── email_utils.py         # Email sending utilities
├── requirements.txt       # Python dependencies
├── .env.example          # Environment variables template
├── DATABASE_SCHEMA.md    # Database schema documentation
├── API_DOCUMENTATION.md  # Complete API documentation
├── QUICK_START.md        # This file
└── routes/
    ├── auth_routes.py    # Authentication endpoints
    └── user_routes.py    # User endpoints
```

---

## 🔒 Production Checklist

Before deploying to production:

- [ ] Change SECRET_KEY and JWT_SECRET_KEY
- [ ] Set DEBUG = False
- [ ] Use HTTPS (SSL/TLS)
- [ ] Set proper email credentials
- [ ] Configure CORS origins
- [ ] Set up database backups
- [ ] Enable password reset token expiry
- [ ] Add rate limiting
- [ ] Set up error logging
- [ ] Configure firewall rules
- [ ] Use environment variables for sensitive data

---

## 🆘 Troubleshooting

### MySQL Connection Error
```
Error: Access denied for user 'root'@'localhost'
```
Solution: Check MySQL is running and credentials in config.py are correct

### Email Not Sending
```
Error: SMTP connection refused
```
Solution: 
1. Check Gmail credentials in .env
2. Enable Less Secure Apps or use App Password
3. Check internet connection

### OTP Table Issues
```
Error: Table 'quickfix_db.otps' doesn't exist
```
Solution: Run the CREATE TABLE statements above

### Port Already in Use
```
Error: Address already in use
```
Solution: Change port in app.py or kill process on port 5000

---

## 📚 Additional Resources

- Full API Documentation: See `API_DOCUMENTATION.md`
- Database Schema: See `DATABASE_SCHEMA.md`
- Flask Documentation: https://flask.palletsprojects.com/
- SQLAlchemy: https://www.sqlalchemy.org/
- PyJWT: https://pyjwt.readthedocs.io/

---

## 📊 Example Data

### Test User for Development
```
Email: test@example.com
Password: password123
Name: Test User
Phone: 1234567890
```

### Sample OTP
```
OTP Code: Any 6 digits (123456, 654321, etc.)
Expires in: 300 seconds (5 minutes)
```

---

**Backend API is ready!** 🎉

Connect your Android app to these endpoints and start using the registration and authentication system.

