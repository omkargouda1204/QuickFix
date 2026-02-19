"""
Authentication utilities for OTP generation and JWT token management
"""

import random
import string
from datetime import datetime, timedelta
from functools import wraps
from flask import request, jsonify, current_app
import jwt
import logging

logger = logging.getLogger(__name__)

def generate_otp(length=6):
    """
    Generate a random OTP of specified length

    Args:
        length (int): Length of OTP (default: 6)

    Returns:
        str: Random OTP code
    """
    return ''.join(random.choices(string.digits, k=length))

def create_access_token(user_id, user_email, expires_in=None):
    """
    Create a JWT access token

    Args:
        user_id (str): User's unique ID
        user_email (str): User's email
        expires_in (timedelta): Token expiration time

    Returns:
        str: Encoded JWT token
    """
    if expires_in is None:
        expires_in = timedelta(hours=24)

    payload = {
        'user_id': user_id,
        'email': user_email,
        'iat': datetime.utcnow(),
        'exp': datetime.utcnow() + expires_in
    }

    token = jwt.encode(
        payload,
        current_app.config['JWT_SECRET_KEY'],
        algorithm='HS256'
    )

    return token

def verify_token(token):
    """
    Verify and decode JWT token

    Args:
        token (str): JWT token to verify

    Returns:
        dict: Decoded token payload if valid, None if invalid
    """
    try:
        payload = jwt.decode(
            token,
            current_app.config['JWT_SECRET_KEY'],
            algorithms=['HS256']
        )
        return payload
    except jwt.ExpiredSignatureError:
        logger.warning('Token has expired')
        return None
    except jwt.InvalidTokenError as e:
        logger.warning(f'Invalid token: {str(e)}')
        return None

def token_required(f):
    """
    Decorator to require valid JWT token in request headers

    Usage:
        @app.route('/protected')
        @token_required
        def protected_route():
            return 'Protected content'
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None

        # Check for token in headers
        if 'Authorization' in request.headers:
            auth_header = request.headers['Authorization']
            try:
                token = auth_header.split(" ")[1]  # Bearer <token>
            except IndexError:
                return jsonify({'message': 'Invalid token format'}), 401

        if not token:
            return jsonify({'message': 'Token is missing'}), 401

        payload = verify_token(token)
        if not payload:
            return jsonify({'message': 'Invalid or expired token'}), 401

        # Store user info in kwargs for use in route
        kwargs['current_user_id'] = payload.get('user_id')
        kwargs['current_user_email'] = payload.get('email')

        return f(*args, **kwargs)

    return decorated

def generate_reset_token(email, expires_in=3600):
    """
    Generate a password reset token (alternative to OTP)

    Args:
        email (str): User's email
        expires_in (int): Token expiration time in seconds

    Returns:
        str: Encoded reset token
    """
    payload = {
        'email': email,
        'type': 'password_reset',
        'iat': datetime.utcnow(),
        'exp': datetime.utcnow() + timedelta(seconds=expires_in)
    }

    token = jwt.encode(
        payload,
        current_app.config['JWT_SECRET_KEY'],
        algorithm='HS256'
    )

    return token

def verify_reset_token(token):
    """
    Verify password reset token

    Args:
        token (str): Reset token to verify

    Returns:
        str: Email from token if valid, None if invalid
    """
    try:
        payload = jwt.decode(
            token,
            current_app.config['JWT_SECRET_KEY'],
            algorithms=['HS256']
        )

        if payload.get('type') != 'password_reset':
            return None

        return payload.get('email')
    except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
        return None

