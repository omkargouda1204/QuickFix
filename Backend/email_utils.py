"""
Email utilities for sending OTP and other emails
Uses Gmail SMTP for sending emails
"""

from flask_mail import Mail, Message
from flask import current_app
import logging

mail = Mail()
logger = logging.getLogger(__name__)

def send_otp_email(email, otp_code, full_name, purpose='registration'):
    """
    Send OTP email to user

    Args:
        email (str): User's email address
        otp_code (str): 6-digit OTP code
        full_name (str): User's full name
        purpose (str): Purpose of OTP ('registration' or 'password_reset')

    Returns:
        bool: True if email sent successfully, False otherwise
    """
    try:
        if purpose == 'registration':
            subject = 'QuickFix - Email Verification OTP'
            body = f"""
Hello {full_name},

Welcome to QuickFix!

To complete your registration, please use the following OTP code:

    {otp_code}

This code will expire in 5 minutes.

If you didn't request this email, please ignore it.

Best regards,
QuickFix Team
            """
        elif purpose == 'password_reset':
            subject = 'QuickFix - Password Reset OTP'
            body = f"""
Hello {full_name},

You requested to reset your password. Please use the following OTP code:

    {otp_code}

This code will expire in 5 minutes.

If you didn't request this email, please ignore it and do not share this code.

Best regards,
QuickFix Team
            """
        else:
            subject = f'QuickFix - Your OTP Code'
            body = f"""
Hello {full_name},

Your OTP code is:

    {otp_code}

This code will expire in 5 minutes.

Best regards,
QuickFix Team
            """

        msg = Message(
            subject=subject,
            recipients=[email],
            body=body
        )

        mail.send(msg)
        logger.info(f'OTP email sent successfully to {email}')
        return True

    except Exception as e:
        logger.error(f'Failed to send OTP email to {email}: {str(e)}')
        return False

def send_welcome_email(email, full_name):
    """Send welcome email after registration"""
    try:
        subject = 'Welcome to QuickFix!'
        body = f"""
Hello {full_name},

Thank you for registering with QuickFix!

Your account has been successfully created. You can now login and start using our services.

Login URL: https://quickfix.example.com/login

Best regards,
QuickFix Team
        """

        msg = Message(
            subject=subject,
            recipients=[email],
            body=body
        )

        mail.send(msg)
        logger.info(f'Welcome email sent to {email}')
        return True

    except Exception as e:
        logger.error(f'Failed to send welcome email to {email}: {str(e)}')
        return False

def send_password_reset_confirmation(email, full_name):
    """Send password reset confirmation email"""
    try:
        subject = 'Password Reset Successful'
        body = f"""
Hello {full_name},

Your password has been successfully reset.

You can now login with your new password.

Login URL: https://quickfix.example.com/login

If you didn't request this change, please contact support immediately.

Best regards,
QuickFix Team
        """

        msg = Message(
            subject=subject,
            recipients=[email],
            body=body
        )

        mail.send(msg)
        logger.info(f'Password reset confirmation email sent to {email}')
        return True

    except Exception as e:
        logger.error(f'Failed to send password reset confirmation email to {email}: {str(e)}')
        return False

