"""
app/utils/validators.py
Input validation helpers.
"""
import re


_EMAIL_RE = re.compile(r"^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$")
_PHONE_RE = re.compile(r"^\+?[0-9]{10,15}$")


def is_valid_email(email: str) -> bool:
    return bool(_EMAIL_RE.match(email.strip()))


def is_valid_phone(phone: str) -> bool:
    digits = re.sub(r"\D", "", phone)
    return 10 <= len(digits) <= 15


def is_valid_password(password: str) -> tuple[bool, str]:
    """
    Password rules:
      - At least 8 characters
      - At least one uppercase letter
      - At least one lowercase letter
      - At least one digit
    Returns (valid: bool, message: str).
    """
    if len(password) < 8:
        return False, "Password must be at least 8 characters."
    if not re.search(r"[A-Z]", password):
        return False, "Password must contain at least one uppercase letter."
    if not re.search(r"[a-z]", password):
        return False, "Password must contain at least one lowercase letter."
    if not re.search(r"\d", password):
        return False, "Password must contain at least one digit."
    return True, "OK"

