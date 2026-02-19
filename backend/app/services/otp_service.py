"""
app/services/otp_service.py
Generate, store, verify, and expire OTPs in Supabase.
Uses secrets module for cryptographically secure OTP generation.
"""
import secrets
import logging
from datetime import datetime, timezone, timedelta
from flask import current_app
from .supabase_client import supabase

logger = logging.getLogger(__name__)
TABLE  = "otp_codes"


def generate_otp(length: int = 6) -> str:
    """
    Return a cryptographically secure zero-padded numeric OTP.
    Uses secrets.randbelow — safe for security-sensitive use.
    """
    return str(secrets.randbelow(10 ** length)).zfill(length)


def create_and_store_otp(email: str, purpose: str) -> str:
    """
    Generate a new OTP, invalidate all previous unused OTPs for this
    email + purpose combination, persist the new one, and return the
    OTP string so the caller can email it.
    """
    otp            = generate_otp()
    expiry_minutes = current_app.config["OTP_EXPIRY_MINUTES"]
    expires_at     = (
        datetime.now(timezone.utc) + timedelta(minutes=expiry_minutes)
    ).isoformat()

    # Invalidate any existing unused OTPs for this email + purpose
    try:
        supabase.table(TABLE).update({"is_used": True}).match(
            {"email": email, "purpose": purpose, "is_used": False}
        ).execute()
    except Exception as exc:
        # Non-fatal — old OTPs will just be ignored because expires_at check
        logger.warning("Could not invalidate old OTPs for %s: %s", email, exc)

    # Store the new OTP
    try:
        supabase.table(TABLE).insert({
            "email":      email,
            "otp_code":   otp,
            "purpose":    purpose,
            "is_used":    False,
            "expires_at": expires_at,
        }).execute()
    except Exception as exc:
        logger.error("Failed to store OTP for %s: %s", email, exc)
        raise RuntimeError("Could not store OTP. Please try again.") from exc

    logger.info(
        "OTP created for %s (purpose=%s, expires_at=%s)", email, purpose, expires_at
    )
    return otp


def verify_otp(email: str, otp_code: str, purpose: str) -> tuple[bool, str]:
    """
    Verify the supplied OTP code.

    - Looks for the most recent unused, non-expired OTP for email+purpose.
    - On match: marks the row as used and returns (True, message).
    - On expired: returns (False, expiry message).
    - On mismatch / not found: returns (False, invalid message).
    """
    now = datetime.now(timezone.utc).isoformat()

    try:
        # Find the most recent valid (unused + not expired) OTP
        valid_result = (
            supabase.table(TABLE)
            .select("*")
            .eq("email",   email)
            .eq("purpose", purpose)
            .eq("is_used", False)
            .gt("expires_at", now)
            .order("created_at", desc=True)
            .limit(1)
            .execute()
        )

        valid_rows = valid_result.data or []

        if not valid_rows:
            # Check if there is an *expired* unused OTP to give a better message
            expired_result = (
                supabase.table(TABLE)
                .select("id")
                .eq("email",   email)
                .eq("purpose", purpose)
                .eq("is_used", False)
                .lte("expires_at", now)
                .limit(1)
                .execute()
            )
            if expired_result.data:
                return False, "OTP has expired. Please request a new one."
            return False, "Invalid OTP. Please check and try again."

        row = valid_rows[0]

        # Constant-time comparison to prevent timing attacks
        if not secrets.compare_digest(row["otp_code"], otp_code):
            return False, "Invalid OTP. Please check and try again."

        # Mark OTP as used so it cannot be reused
        supabase.table(TABLE).update({"is_used": True}).eq("id", row["id"]).execute()

        logger.info("OTP verified for %s (purpose=%s)", email, purpose)
        return True, "OTP verified successfully."

    except Exception as exc:
        logger.error("Error verifying OTP for %s: %s", email, exc)
        return False, "OTP verification failed. Please try again."


def check_recent_otp(email: str, otp_code: str, purpose: str, minutes: int = 5) -> tuple[bool, str]:
    """
    Check if an OTP was recently verified (already marked as used).

    This is used for /reset-password which receives an OTP that was already
    verified by /verify-otp. We don't want to reject it just because is_used=True.

    Args:
        email:    Email address
        otp_code: The 6-digit OTP code
        purpose:  "verification" | "password_reset"
        minutes:  How many minutes back to check (default 5)

    Returns:
        (True, message) if OTP was verified recently
        (False, message) if OTP not found or too old
    """
    now = datetime.now(timezone.utc)
    cutoff = (now - timedelta(minutes=minutes)).isoformat()

    try:
        # Find recently-used OTP (marked as used within last N minutes)
        result = (
            supabase.table(TABLE)
            .select("*")
            .eq("email",   email)
            .eq("purpose", purpose)
            .eq("otp_code", otp_code)
            .eq("is_used", True)
            .gte("created_at", cutoff)    # created within last N minutes
            .order("created_at", desc=True)
            .limit(1)
            .execute()
        )

        rows = result.data or []

        if rows:
            logger.info("Recent OTP confirmed for %s (purpose=%s, used within %d min)",
                       email, purpose, minutes)
            return True, "OTP confirmed (recently verified)."

        # OTP not found or too old — maybe they're trying to reuse an old one
        return False, "Invalid or expired OTP. Please verify OTP first."

    except Exception as exc:
        logger.error("Error checking recent OTP for %s: %s", email, exc)
        return False, "OTP verification failed. Please try again."
