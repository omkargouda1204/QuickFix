"""
Database Models for QuickFix Backend
Defines User and OTP models with all required fields
"""

from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime
import uuid

db = SQLAlchemy()

class User(db.Model):
    """User model for storing user information"""
    __tablename__ = 'users'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    full_name = db.Column(db.String(120), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False, index=True)
    phone = db.Column(db.String(15), nullable=False)
    password_hash = db.Column(db.String(255), nullable=False)
    is_active = db.Column(db.Boolean, default=True)
    email_verified = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    last_login = db.Column(db.DateTime)

    # Relationship
    otps = db.relationship('OTP', backref='user', lazy=True, cascade='all, delete-orphan')

    def __repr__(self):
        return f'<User {self.email}>'

    def set_password(self, password):
        """Hash and set password"""
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        """Check if provided password matches hash"""
        return check_password_hash(self.password_hash, password)

    def to_dict(self):
        """Convert user to dictionary"""
        return {
            'id': self.id,
            'full_name': self.full_name,
            'email': self.email,
            'phone': self.phone,
            'email_verified': self.email_verified,
            'created_at': self.created_at.isoformat(),
            'last_login': self.last_login.isoformat() if self.last_login else None
        }

class OTP(db.Model):
    """OTP model for storing OTP records"""
    __tablename__ = 'otps'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = db.Column(db.String(36), db.ForeignKey('users.id'), nullable=False, index=True)
    email = db.Column(db.String(120), nullable=False, index=True)
    otp_code = db.Column(db.String(10), nullable=False)
    purpose = db.Column(db.String(50), nullable=False)  # 'registration', 'password_reset'
    is_verified = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow, index=True)
    expires_at = db.Column(db.DateTime, nullable=False, index=True)
    attempt_count = db.Column(db.Integer, default=0)
    max_attempts = db.Column(db.Integer, default=5)

    def __repr__(self):
        return f'<OTP {self.email} ({self.purpose})>'

    def is_expired(self):
        """Check if OTP is expired"""
        return datetime.utcnow() > self.expires_at

    def is_valid(self):
        """Check if OTP is valid (not expired and not max attempts reached)"""
        return not self.is_expired() and self.attempt_count < self.max_attempts

    def verify(self, code):
        """Verify OTP code"""
        self.attempt_count += 1
        if self.otp_code == code and self.is_valid():
            self.is_verified = True
            return True
        return False

    def to_dict(self):
        """Convert OTP to dictionary"""
        return {
            'id': self.id,
            'email': self.email,
            'purpose': self.purpose,
            'is_verified': self.is_verified,
            'created_at': self.created_at.isoformat(),
            'expires_at': self.expires_at.isoformat()
        }

