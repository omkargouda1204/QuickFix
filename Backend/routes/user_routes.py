"""
User routes for getting user profile and updating user information
"""

from flask import Blueprint, request, jsonify, current_app
from models import db, User
from auth_utils import token_required
import logging

user_bp = Blueprint('user', __name__)
logger = logging.getLogger(__name__)

@user_bp.route('/profile', methods=['GET'])
@token_required
def get_profile(current_user_id, current_user_email):
    """
    Get current user's profile
    Requires valid token in Authorization header

    Returns:
        JSON response with user profile information
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
            'message': 'Profile retrieved successfully',
            'data': {
                'user': user.to_dict()
            }
        }), 200

    except Exception as e:
        logger.error(f'Get profile error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to retrieve profile'
        }), 500

@user_bp.route('/update-profile', methods=['PUT'])
@token_required
def update_profile(current_user_id, current_user_email):
    """
    Update user's profile information
    Requires valid token in Authorization header

    Request JSON:
        {
            "full_name": "Jane Doe",
            "phone": "9876543210"
        }

    Returns:
        JSON response with updated user information
    """
    try:
        data = request.get_json()

        user = User.query.filter_by(id=current_user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        # Update allowed fields
        if 'full_name' in data and data.get('full_name'):
            user.full_name = data.get('full_name').strip()

        if 'phone' in data and data.get('phone'):
            phone = data.get('phone').strip()
            if len(phone) < 10 or len(phone) > 15:
                return jsonify({
                    'status': 'error',
                    'message': 'Phone number must be between 10-15 digits'
                }), 400
            user.phone = phone

        db.session.commit()

        logger.info(f'User profile updated: {user.email}')

        return jsonify({
            'status': 'success',
            'message': 'Profile updated successfully',
            'data': {
                'user': user.to_dict()
            }
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'Update profile error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to update profile'
        }), 500

@user_bp.route('/change-password', methods=['POST'])
@token_required
def change_password(current_user_id, current_user_email):
    """
    Change user's password
    Requires valid token in Authorization header

    Request JSON:
        {
            "current_password": "oldpassword123",
            "new_password": "newpassword123"
        }

    Returns:
        JSON response with success or error message
    """
    try:
        data = request.get_json()

        # Validate required fields
        if not data.get('current_password') or not data.get('new_password'):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields: current_password, new_password'
            }), 400

        current_password = data.get('current_password')
        new_password = data.get('new_password')

        # Validate new password
        if len(new_password) < 6:
            return jsonify({
                'status': 'error',
                'message': 'New password must be at least 6 characters long'
            }), 400

        user = User.query.filter_by(id=current_user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        # Verify current password
        if not user.check_password(current_password):
            return jsonify({
                'status': 'error',
                'message': 'Current password is incorrect'
            }), 401

        # Update password
        user.set_password(new_password)
        db.session.commit()

        logger.info(f'Password changed for user: {user.email}')

        return jsonify({
            'status': 'success',
            'message': 'Password changed successfully'
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'Change password error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to change password'
        }), 500

@user_bp.route('/delete-account', methods=['DELETE'])
@token_required
def delete_account(current_user_id, current_user_email):
    """
    Delete user account (soft delete - sets is_active to False)
    Requires valid token in Authorization header

    Returns:
        JSON response with success or error message
    """
    try:
        user = User.query.filter_by(id=current_user_id).first()
        if not user:
            return jsonify({
                'status': 'error',
                'message': 'User not found'
            }), 404

        # Soft delete - set is_active to False
        user.is_active = False
        db.session.commit()

        logger.info(f'Account deleted (soft delete): {user.email}')

        return jsonify({
            'status': 'success',
            'message': 'Account deleted successfully'
        }), 200

    except Exception as e:
        db.session.rollback()
        logger.error(f'Delete account error: {str(e)}')
        return jsonify({
            'status': 'error',
            'message': 'Failed to delete account'
        }), 500

