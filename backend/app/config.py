"""
app/config.py  —  All settings loaded from .env
"""
import os
from dotenv import load_dotenv

# Load .env from the backend root
load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))


class Config:
    # ── Flask ────────────────────────────────────────────────────────
    SECRET_KEY: str = os.getenv("SECRET_KEY", "fallback-secret-change-me")
    DEBUG: bool = os.getenv("FLASK_DEBUG", "0") == "1"

    # ── Supabase ─────────────────────────────────────────────────────
    SUPABASE_URL: str = os.getenv("SUPABASE_URL", "")
    SUPABASE_SERVICE_ROLE_KEY: str = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")

    # ── SMTP ─────────────────────────────────────────────────────────
    SMTP_HOST: str = os.getenv("SMTP_HOST", "smtp.gmail.com")
    SMTP_PORT: int = int(os.getenv("SMTP_PORT", "587"))
    SMTP_USER: str = os.getenv("SMTP_USER", "")
    SMTP_PASSWORD: str = os.getenv("SMTP_PASSWORD", "")
    EMAIL_FROM_NAME: str = os.getenv("EMAIL_FROM_NAME", "QuickFix")
    EMAIL_FROM: str = os.getenv("EMAIL_FROM", os.getenv("SMTP_USER", ""))

    # ── OTP ──────────────────────────────────────────────────────────
    OTP_EXPIRY_MINUTES: int = int(os.getenv("OTP_EXPIRY_MINUTES", "10"))

    # ── JWT ──────────────────────────────────────────────────────────
    JWT_SECRET_KEY: str = os.getenv("JWT_SECRET_KEY", "fallback-jwt-secret-change-me")
    JWT_ACCESS_TOKEN_EXPIRES_HOURS: int = int(
        os.getenv("JWT_ACCESS_TOKEN_EXPIRES_HOURS", "24")
    )

