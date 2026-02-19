"""
Authentication routes for user registration, login, and password reset
"""

from flask import Blueprint, request, jsonify, current_app
from models import db, User, OTP
from email_utils import send_otp_email, send_welcome_email, send_password_reset_confirmation
from auth_utils import generate_otp, create_access_token, verify_token, token_required
from datetime import datetime, timedelta
import logging
import re

auth_bp = Blueprint('auth', __name__)
logger = logging.getLogger(__name__)

# Email validation regex
EMAIL_REGEX = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'

def validate_email(email):
    """Validate email format"""
    return re.match(EMAIL_REGEX, email) is not None

def validate_password(password):
    """
    Validate password strength
    Requirements: At least 6 characters
    """
    return len(password) >= 6

@auth_bp.route('/register', methods=['POST'])
def register():
    """
    Register a new user with email and password

    Request JSON:
        {
            "full_name": "John Doe",
            "email": "john@example.com",
            "phone": "1234567890",
            "password": "password123"
        }

    Returns:
        JSON response with OTP details or error message
    """
    try:
        data = request.get_json()

        # Validate required fields
        if not all(['full_name', 'email', 'phone', 'password']) or not all(
            data.get(field) for field in ['full_name', 'email', 'phone', 'password']
        ):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields: full_name, email, phone, password'
            }), 400

        full_name = data.get('full_name').strip()
        email = data.get('email').strip().lower()
        phone = data.get('phone').strip()
        password = data.get('password')

        # Validate email format
        if not validate_email(email):
            return jsonify({
                'status': 'error',
                'message': 'Invalid email format'
            }), 400

        # Validate password strength
        if not validate_password(password):
            return jsonify({
                'status': 'error',
                'message': 'Password must be at least 6 characters long'
            }), 400

        # Validate phone number
        if len(phone) < 10 or len(phone) > 15:
            return jsonify({
                'status': 'error',
                'message': 'Phone number must be between 10-15 digits'
            }), 400

        # Check if user already exists
        existing_user = User.query.filter_by(email=email).first()
        if existing_user:
            return jsonify({
                'status': 'error',
                'message': 'Email already registered. Please login or use a different email.'
            }), 409

        # Create new user
        user = User(
            full_name=full_name,
            email=email,
            phone=phone
        )
        user.set_password(password)

        db.session.add(user)
        db.session.commit()

        logger.info(f'New user registered: {email}')

        # Generate and send OTP
        otp_code = generate_otp(current_app.config['OTP_LENGTH'])
        otp_expiry = datetime.utcnow() + timedelta(seconds=current_app.config['OTP_EXPIRY_TIME'])

        otp = OTP(
            user_id=user.id,
            email=email,
            otp_code=otp_code,
            purpose='registration',
            expires_at=otp_expiry
        )

        db.session.add(otp)
        db.session.commit()

        # Send OTP email
        email_sent = send_otp_email(email, otp_code, full_name, purpose='registration')

        logger.info(f'OTP generated and sent to {email}')

        return jsonify({
            'status': 'success',
            'message': 'Registration successful. OTP sent to email.',
            'data': {
                'user_id': user.id,
                'email': email,
                'otp_id': otp.id,
                'expires_in': current_app.config['OTP_EXPIRY_TIME']
            }
        }), 201

    except Exception as e:
        db.session.rollback()
        logger.error(f'Registration error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Registration failed. Please try again.'
        }), 500

@auth_bp.route('/verify-otp', methods=['POST'])
def verify_otp():
    """
    Verify OTP for registration or password reset

    Request JSON:
        {
            "otp_id": "otp_id_from_register_response",
            "otp_code": "123456"
        }

    Returns:
        JSON response with access token or error message
    """
    try:
        data = request.get_json()

        # Validate required fields
        if not data.get('otp_id') or not data.get('otp_code'):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields: otp_id, otp_code'
            }), 400

        otp_id = data.get('otp_id')
        otp_code = data.get('otp_code').strip()

        # Find OTP record
        otp = OTP.query.filter_by(id=otp_id).first()
        if not otp:
            return jsonify({
                'status': 'error',
                'message': 'Invalid OTP ID'
            }), 404

        # Check if OTP is expired
        if otp.is_expired():
            return jsonify({
                'status': 'error',
                'message': 'OTP has expired. Please request a new OTP.'
            }), 400

        # Check attempts
        if otp.attempt_count >= otp.max_attempts:
            return jsonify({
                'status': 'error',
                'message': 'Maximum OTP verification attempts exceeded. Please request a new OTP.'
            }), 400

        # Verify OTP code
        if not otp.verify(otp_code):
            db.session.commit()
            return jsonify({
                'status': 'error',
                'message': f'Invalid OTP code. {otp.max_attempts - otp.attempt_count} attempts remaining.'
            }), 400

        db.session.commit()

        # Get user
        user = User.query.filter_by(id=otp.user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        # Update user based on OTP purpose
        if otp.purpose == 'registration':
            user.email_verified = True
            db.session.commit()

            # Send welcome email
            send_welcome_email(user.email, user.full_name)

            logger.info(f'User email verified: {user.email}')

        elif otp.purpose == 'password_reset':
            logger.info(f'OTP verified for password reset: {user.email}')

        # Create access token
        access_token = create_access_token(user.id, user.email)

        return jsonify({
            'status': 'success',
            'message': 'OTP verified successfully',
            'data': {
                'access_token': access_token,
                'token_type': 'Bearer',
                'user': user.to_dict()
            }
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'OTP verification error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'OTP verification failed. Please try again.'
        }), 500

@auth_bp.route('/resend-otp', methods=['POST'])
def resend_otp():
    """
    Resend OTP to user's email

    Request JSON:
        {
            "otp_id": "otp_id_from_register_response"
        }

    Returns:
        JSON response with new OTP expiry time
    """
    try:
        data = request.get_json()

        if not data.get('otp_id'):
            return jsonify({
                'status': 'error',
                'message': 'Missing required field: otp_id'
            }), 400

        otp_id = data.get('otp_id')

        # Find OTP record
        otp = OTP.query.filter_by(id=otp_id).first()
        if not otp:
            return jsonify({
                'status': 'error',
                'message': 'Invalid OTP ID'
            }), 404

        # Check if already verified
        if otp.is_verified:
            return jsonify({
                'status': 'error',
                'message': 'OTP already verified'
            }), 400

        # Delete old OTP and create new one
        user = User.query.filter_by(id=otp.user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        db.session.delete(otp)
        db.session.commit()

        # Generate new OTP
        new_otp_code = generate_otp(current_app.config['OTP_LENGTH'])
        new_otp_expiry = datetime.utcnow() + timedelta(seconds=current_app.config['OTP_EXPIRY_TIME'])

        new_otp = OTP(
            user_id=user.id,
            email=user.email,
            otp_code=new_otp_code,
            purpose=otp.purpose,
            expires_at=new_otp_expiry
        )

        db.session.add(new_otp)
        db.session.commit()

        # Send OTP email
        send_otp_email(user.email, new_otp_code, user.full_name, purpose=otp.purpose)

        logger.info(f'OTP resent to {user.email}')

        return jsonify({
            'status': 'success',
            'message': 'OTP resent to email',
            'data': {
                'otp_id': new_otp.id,
                'expires_in': current_app.config['OTP_EXPIRY_TIME']
            }
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'OTP resend error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to resend OTP. Please try again.'
        }), 500

@auth_bp.route('/login', methods=['POST'])
def login():
    """
    Login with email and password

    Request JSON:
        {
            "email": "user@example.com",
            "password": "password123"
        }

    Returns:
        JSON response with access token or error message
    """
    try:
        data = request.get_json()

        # Validate required fields
        if not data.get('email') or not data.get('password'):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields: email, password'
            }), 400

        email = data.get('email').strip().lower()
        password = data.get('password')

        # Find user by email
        user = User.query.filter_by(email=email).first()
        if not user or not user.check_password(password):
            return jsonify({
                'status': 'error',
                'message': 'Invalid email or password'
            }), 401

        # Check if user is active
        if not user.is_active:
            return jsonify({
                'status': 'error',
                'message': 'Account is inactive. Please contact support.'
            }), 403

        # Update last login
        user.last_login = datetime.utcnow()
        db.session.commit()

        # Create access token
        access_token = create_access_token(user.id, user.email)

        logger.info(f'User logged in: {email}')

        return jsonify({
            'status': 'success',
            'message': 'Login successful',
            'data': {
                'access_token': access_token,
                'token_type': 'Bearer',
                'user': user.to_dict()
            }
        }), 200

    except Exception as e:
        logger.error(f'Login error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Login failed. Please try again.'
        }), 500

@auth_bp.route('/forgot-password', methods=['POST'])
def forgot_password():
    """
    Request password reset by email

    Request JSON:
        {
            "email": "user@example.com"
        }

    Returns:
        JSON response with OTP details
    """
    try:
        data = request.get_json()

        if not data.get('email'):
            return jsonify({
                'status': 'error',
                'message': 'Missing required field: email'
            }), 400

        email = data.get('email').strip().lower()

        # Find user by email
        user = User.query.filter_by(email=email).first()
        if not user:
            # Don't reveal if email exists (security best practice)
            return jsonify({
                'status': 'success',
                'message': 'If email exists, OTP will be sent to reset password'
            }), 200

        # Generate OTP for password reset
        otp_code = generate_otp(current_app.config['OTP_LENGTH'])
        otp_expiry = datetime.utcnow() + timedelta(seconds=current_app.config['OTP_EXPIRY_TIME'])

        # Delete any existing password reset OTPs
        existing_otps = OTP.query.filter_by(
            user_id=user.id,
            purpose='password_reset',
            is_verified=False
        ).all()
        for otp in existing_otps:
            db.session.delete(otp)

        db.session.commit()

        # Create new OTP
        otp = OTP(
            user_id=user.id,
            email=email,
            otp_code=otp_code,
            purpose='password_reset',
            expires_at=otp_expiry
        )

        db.session.add(otp)
        db.session.commit()

        # Send OTP email
        send_otp_email(email, otp_code, user.full_name, purpose='password_reset')

        logger.info(f'Password reset OTP sent to {email}')

        return jsonify({
            'status': 'success',
            'message': 'OTP sent to email for password reset',
            'data': {
                'otp_id': otp.id,
                'expires_in': current_app.config['OTP_EXPIRY_TIME']
            }
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'Forgot password error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to process password reset request'
        }), 500

@auth_bp.route('/reset-password', methods=['POST'])
def reset_password():
    """
    Reset password using OTP

    Request JSON:
        {
            "otp_id": "otp_id_from_forgot_password_response",
            "otp_code": "123456",
            "new_password": "newpassword123"
        }

    Returns:
        JSON response with success or error message
    """
    try:
        data = request.get_json()

        # Validate required fields
        if not all(['otp_id', 'otp_code', 'new_password']) or not all(
            data.get(field) for field in ['otp_id', 'otp_code', 'new_password']
        ):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields: otp_id, otp_code, new_password'
            }), 400

        otp_id = data.get('otp_id')
        otp_code = data.get('otp_code').strip()
        new_password = data.get('new_password')

        # Validate new password
        if not validate_password(new_password):
            return jsonify({
                'status': 'error',
                'message': 'New password must be at least 6 characters long'
            }), 400

        # Find OTP record
        otp = OTP.query.filter_by(id=otp_id, purpose='password_reset').first()
        if not otp:
            return jsonify({
                'status': 'error',
                'message': 'Invalid OTP ID'
            }), 404

        # Check if OTP is expired
        if otp.is_expired():
            return jsonify({
                'status': 'error',
                'message': 'OTP has expired. Please request a new OTP.'
            }), 400

        # Verify OTP code
        if not otp.verify(otp_code):
            db.session.commit()
            return jsonify({
                'status': 'error',
                'message': f'Invalid OTP code. {otp.max_attempts - otp.attempt_count} attempts remaining.'
            }), 400

        # Get user
        user = User.query.filter_by(id=otp.user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        # Update password
        user.set_password(new_password)
        db.session.commit()

        # Send confirmation email
        send_password_reset_confirmation(user.email, user.full_name)

        logger.info(f'Password reset successful for {user.email}')

        return jsonify({
            'status': 'success',
            'message': 'Password reset successful. You can now login with your new password.'
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'Password reset error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Password reset failed. Please try again.'
        }), 500

@auth_bp.route('/validate-token', methods=['POST'])
@token_required
def validate_token(current_user_id, current_user_email):
    """
    Validate if provided token is valid
    Requires valid token in Authorization header

    Returns:
        JSON response with user info if token is valid
    """
    try:
        user = User.query.filter_by(id=current_user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        return jsonify({
            'status': 'success',
            'message': 'Token is valid',
            'data': {
                'user': user.to_dict()
            }
        }), 200

    except Exception as e:
        logger.error(f'Token validation error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Token validation failed'
        }), 500

