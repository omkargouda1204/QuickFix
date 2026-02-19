"""
app/utils/password_utils.py
Bcrypt password hashing and verification.
"""
import bcrypt


def hash_password(plain_password: str) -> str:
    """Hash a plain-text password and return the hash as a UTF-8 string."""
    salt = bcrypt.gensalt(rounds=12)
    hashed = bcrypt.hashpw(plain_password.encode("utf-8"), salt)
    return hashed.decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Return True if plain_password matches the stored hash."""
    try:
        return bcrypt.checkpw(
            plain_password.encode("utf-8"),
            hashed_password.encode("utf-8"),
        )
    except Exception:
        return False

