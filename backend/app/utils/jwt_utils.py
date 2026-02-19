"""
app/utils/jwt_utils.py
JWT creation and decoding helpers.
"""
import jwt
import logging
from datetime import datetime, timezone, timedelta
from flask import current_app

logger = logging.getLogger(__name__)


def create_access_token(user_id: str, email: str, role: str) -> str:
    """Create a signed JWT access token."""
    expires_hours: int = current_app.config["JWT_ACCESS_TOKEN_EXPIRES_HOURS"]
    payload = {
        "sub":   user_id,
        "email": email,
        "role":  role,
        "iat":   datetime.now(timezone.utc),
        "exp":   datetime.now(timezone.utc) + timedelta(hours=expires_hours),
    }
    return jwt.encode(
        payload,
        current_app.config["JWT_SECRET_KEY"],
        algorithm="HS256",
    )


def decode_access_token(token: str) -> dict | None:
    """
    Decode and verify a JWT.
    Returns the payload dict on success, None on any error.
    """
    try:
        return jwt.decode(
            token,
            current_app.config["JWT_SECRET_KEY"],
            algorithms=["HS256"],
        )
    except jwt.ExpiredSignatureError:
        logger.warning("JWT expired")
    except jwt.InvalidTokenError as exc:
        logger.warning("Invalid JWT: %s", exc)
    return None

