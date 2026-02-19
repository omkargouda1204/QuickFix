"""
app/models/user_model.py
All Supabase DB operations for the `users` table.
Full error handling — never raises unhandled exceptions to routes.
"""
import logging
from .db import db

logger = logging.getLogger(__name__)
TABLE  = "users"


def create_user(name: str, email: str, phone: str,
                password_hash: str, role: str = "user") -> dict | None:
    """Insert a new user row and return it. Returns None on failure."""
    try:
        result = db.table(TABLE).insert({
            "name":          name,
            "email":         email.lower().strip(),
            "phone":         phone or "",
            "password_hash": password_hash,
            "role":          role,
            "is_verified":   False,
        }).execute()
        rows = result.data or []
        return rows[0] if rows else None
    except Exception as exc:
        logger.error("create_user failed for %s: %s", email, exc)
        return None


def get_user_by_email(email: str) -> dict | None:
    """Fetch a user by email. Returns None if not found or on error."""
    try:
        result = (
            db.table(TABLE)
            .select("*")
            .eq("email", email.lower().strip())
            .limit(1)
            .execute()
        )
        rows = result.data or []
        return rows[0] if rows else None
    except Exception as exc:
        logger.error("get_user_by_email failed for %s: %s", email, exc)
        return None


def get_user_by_id(user_id: str) -> dict | None:
    """Fetch a user by UUID. Returns only public-safe fields."""
    try:
        result = (
            db.table(TABLE)
            .select("id, name, email, phone, role, is_verified, created_at")
            .eq("id", user_id)
            .limit(1)
            .execute()
        )
        rows = result.data or []
        return rows[0] if rows else None
    except Exception as exc:
        logger.error("get_user_by_id failed for %s: %s", user_id, exc)
        return None


def mark_user_verified(email: str) -> bool:
    """Set is_verified=True for the given email. Returns True on success."""
    try:
        result = (
            db.table(TABLE)
            .update({"is_verified": True})
            .eq("email", email.lower().strip())
            .execute()
        )
        return bool(result.data)
    except Exception as exc:
        logger.error("mark_user_verified failed for %s: %s", email, exc)
        return False


def update_password(email: str, new_password_hash: str) -> bool:
    """Update the password_hash for the given email. Returns True on success."""
    try:
        result = (
            db.table(TABLE)
            .update({"password_hash": new_password_hash})
            .eq("email", email.lower().strip())
            .execute()
        )
        return bool(result.data)
    except Exception as exc:
        logger.error("update_password failed for %s: %s", email, exc)
        return False


def email_exists(email: str) -> bool:
    """Return True if the email is already registered."""
    try:
        result = (
            db.table(TABLE)
            .select("id")
            .eq("email", email.lower().strip())
            .limit(1)
            .execute()
        )
        return bool(result.data)
    except Exception as exc:
        logger.error("email_exists check failed for %s: %s", email, exc)
        return False


def delete_user_by_email(email: str) -> bool:
    """
    Delete an unverified user row.
    Used to clean up if OTP email fails right after account creation.
    """
    try:
        result = (
            db.table(TABLE)
            .delete()
            .eq("email", email.lower().strip())
            .eq("is_verified", False)
            .execute()
        )
        return bool(result.data)
    except Exception as exc:
        logger.error("delete_user_by_email failed for %s: %s", email, exc)
        return False

