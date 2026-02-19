"""
app/routes/auth_routes.py
All authentication API endpoints for QuickFix.

POST /api/auth/signup            — Register new user → sends real OTP email
POST /api/auth/send-otp          — Send / resend OTP
POST /api/auth/verify-otp        — Verify OTP → activates account or confirms reset
POST /api/auth/login             — Login with email + password → returns JWT
POST /api/auth/forgot-password   — Send password-reset OTP
POST /api/auth/reset-password    — Verify reset OTP + set new password
GET  /api/auth/me                — Get current user profile  (Bearer JWT required)
GET  /api/auth/health            — SMTP + Supabase connectivity check
"""
import logging
from flask import Blueprint, request, current_app

from ..models       import user_model
from ..services     import otp_service, email_service
from ..utils.password_utils import hash_password, verify_password
from ..utils.jwt_utils      import create_access_token, decode_access_token
from ..utils.validators     import is_valid_email, is_valid_phone, is_valid_password
from ..utils.response_utils import success, error

auth_bp = Blueprint("auth", __name__)
logger  = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# 1. SIGNUP
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/signup", methods=["POST"])
def signup():
    """
    Register a new user and send a verification OTP to their email.

    Body (JSON):
        name       string  required
        email      string  required
        phone      string  optional
        password   string  required  (min 8 chars, upper + lower + digit)
        role       string  optional  "user" | "provider"  (default: "user")

    Flow:
        1. Validate all fields.
        2. Check email not already registered.
        3. Hash password with bcrypt.
        4. Create user row (is_verified = false).
        5. Generate + store OTP in Supabase.
        6. Send real email via Gmail SMTP.
        7. If email fails → delete the user row so they can retry signup.
    """
    data = request.get_json(silent=True) or {}

    name     = (data.get("name")     or "").strip()
    email    = (data.get("email")    or "").strip().lower()
    phone    = (data.get("phone")    or "").strip()
    password = (data.get("password") or "").strip()
    role     = (data.get("role")     or "user").strip().lower()

    # ── Field validation ─────────────────────────────────────────────
    field_errors: dict = {}

    if not name or len(name) < 2:
        field_errors["name"] = "Name must be at least 2 characters."

    if not email:
        field_errors["email"] = "Email is required."
    elif not is_valid_email(email):
        field_errors["email"] = "Enter a valid email address."

    if phone and not is_valid_phone(phone):
        field_errors["phone"] = "Enter a valid phone number (10–15 digits)."

    pwd_ok, pwd_msg = is_valid_password(password)
    if not pwd_ok:
        field_errors["password"] = pwd_msg

    if role not in ("user", "provider"):
        field_errors["role"] = "role must be 'user' or 'provider'."

    if field_errors:
        return error("Validation failed.", 422, field_errors)

    # ── Duplicate email check ─────────────────────────────────────────
    if user_model.email_exists(email):
        # If they registered but never verified, let them resend OTP
        existing = user_model.get_user_by_email(email)
        if existing and not existing.get("is_verified"):
            # Resend a fresh OTP to help them complete verification
            try:
                otp  = otp_service.create_and_store_otp(email, "verification")
                sent = email_service.send_otp_email(email, otp, "verification")
            except Exception:
                sent = False

            if sent:
                expiry = current_app.config["OTP_EXPIRY_MINUTES"]
                return success(
                    {"email": email, "resent": True},
                    f"Account already exists but is not verified. "
                    f"A new OTP has been sent to {email}.",
                    200,
                )
        return error(
            "An account with this email already exists. Please log in.", 409
        )

    # ── Create user (unverified) ──────────────────────────────────────
    password_hash = hash_password(password)
    user = user_model.create_user(name, email, phone, password_hash, role)
    if not user:
        return error("Registration failed. Please try again later.", 500)

    # ── Generate + store OTP ──────────────────────────────────────────
    try:
        otp = otp_service.create_and_store_otp(email, "verification")
    except RuntimeError as exc:
        # OTP storage failed — roll back user row
        user_model.delete_user_by_email(email)
        logger.error("OTP storage failed for new user %s: %s", email, exc)
        return error("Registration failed while generating OTP. Please try again.", 500)

    # ── Send OTP email (real SMTP) ────────────────────────────────────
    sent = email_service.send_otp_email(email, otp, "verification")
    if not sent:
        # Roll back the user row so they can retry signup cleanly
        user_model.delete_user_by_email(email)
        logger.error("OTP email delivery failed for %s — user row rolled back", email)
        return error(
            "Registration failed: could not send verification email. "
            "Please check your email address and try again.",
            500,
        )

    expiry = current_app.config["OTP_EXPIRY_MINUTES"]
    return success(
        {"email": email},
        f"Account created! A {expiry}-minute verification OTP has been sent to {email}. "
        f"Please check your inbox (and spam folder).",
        201,
    )


# ─────────────────────────────────────────────────────────────────────────────
# 2. SEND / RESEND OTP
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/send-otp", methods=["POST"])
def send_otp():
    """
    Send or resend an OTP for any supported purpose.

    Body (JSON):
        email    string  required
        purpose  string  required  "verification" | "password_reset"
    """
    data    = request.get_json(silent=True) or {}
    email   = (data.get("email")   or "").strip().lower()
    purpose = (data.get("purpose") or "").strip()

    if not email or not is_valid_email(email):
        return error("A valid email address is required.", 422)

    if purpose not in ("verification", "password_reset"):
        return error("purpose must be 'verification' or 'password_reset'.", 422)

    # For verification: user must exist (even if unverified)
    # For password_reset: user must exist and be verified
    user = user_model.get_user_by_email(email)

    if purpose == "verification":
        if not user:
            return error(
                "No account found for this email. Please sign up first.", 404
            )
        if user.get("is_verified"):
            return error("This email is already verified. Please log in.", 400)

    elif purpose == "password_reset":
        # Don't reveal whether email is registered — security best practice
        if not user:
            return success(
                message=f"If {email} is registered, a password-reset OTP has been sent."
            )
        if not user.get("is_verified"):
            return error(
                "Your email is not verified yet. "
                "Please complete email verification first.", 403
            )

    # Generate, store, and send OTP
    try:
        otp = otp_service.create_and_store_otp(email, purpose)
    except RuntimeError as exc:
        return error(str(exc), 500)

    sent = email_service.send_otp_email(email, otp, purpose)
    if not sent:
        return error(
            "Failed to send OTP email. "
            "Please check your email address and try again.", 500
        )

    expiry = current_app.config["OTP_EXPIRY_MINUTES"]
    return success(
        message=f"OTP sent to {email}. It is valid for {expiry} minutes. "
                f"Check your inbox and spam folder."
    )


# ─────────────────────────────────────────────────────────────────────────────
# 3. VERIFY OTP
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/verify-otp", methods=["POST"])
def verify_otp():
    """
    Verify an OTP code.

    Body (JSON):
        email    string  required
        otp      string  required  (6 digits)
        purpose  string  required  "verification" | "password_reset"

    On success for 'verification':
        - Marks account as verified (active).
        - Returns a JWT so the user is immediately logged in.

    On success for 'password_reset':
        - Confirms OTP is valid.
        - Client must then call /reset-password with the same email + otp.
    """
    data    = request.get_json(silent=True) or {}
    email   = (data.get("email")   or "").strip().lower()
    otp     = (data.get("otp")     or "").strip()
    purpose = (data.get("purpose") or "").strip()

    if not email:
        return error("email is required.", 422)
    if not otp or not otp.isdigit() or len(otp) != 6:
        return error("OTP must be a 6-digit number.", 422)
    if purpose not in ("verification", "password_reset"):
        return error("purpose must be 'verification' or 'password_reset'.", 422)

    ok, msg = otp_service.verify_otp(email, otp, purpose)
    if not ok:
        return error(msg, 400)

    # ── Account activation ────────────────────────────────────────────
    if purpose == "verification":
        marked = user_model.mark_user_verified(email)
        if not marked:
            return error("Could not activate account. Please contact support.", 500)

        user = user_model.get_user_by_email(email)
        if not user:
            return error("User not found after verification.", 500)

        token = create_access_token(user["id"], user["email"], user["role"])
        return success(
            {
                "token": token,
                "user":  _public_user(user),
            },
            "Email verified successfully! You are now logged in.",
        )

    # ── Password-reset OTP confirmed ──────────────────────────────────
    # Return email so the client can pass it straight to /reset-password
    return success(
        {"email": email},
        "OTP verified. You may now reset your password.",
    )


# ─────────────────────────────────────────────────────────────────────────────
# 4. LOGIN
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/login", methods=["POST"])
def login():
    """
    Login with email + password.

    Body (JSON):
        email     string  required
        password  string  required
    """
    data     = request.get_json(silent=True) or {}
    email    = (data.get("email")    or "").strip().lower()
    password = (data.get("password") or "").strip()

    if not email or not password:
        return error("Email and password are required.", 422)

    if not is_valid_email(email):
        return error("Enter a valid email address.", 422)

    user = user_model.get_user_by_email(email)

    # Use a generic message — never reveal whether the email exists
    auth_fail = "Invalid email or password."

    if not user:
        return error(auth_fail, 401)

    if not verify_password(password, user["password_hash"]):
        return error(auth_fail, 401)

    if not user.get("is_verified"):
        return error(
            "Your email is not verified. "
            "Please verify your account using the OTP sent to your email.",
            403,
        )

    token = create_access_token(user["id"], user["email"], user["role"])
    return success(
        {
            "token": token,
            "user":  _public_user(user),
        },
        "Login successful.",
    )


# ─────────────────────────────────────────────────────────────────────────────
# 5. FORGOT PASSWORD
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/forgot-password", methods=["POST"])
def forgot_password():
    """
    Send a password-reset OTP to a registered email.

    Body (JSON):
        email  string  required

    Always returns HTTP 200 to prevent email enumeration.
    """
    data  = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()

    if not email or not is_valid_email(email):
        return error("A valid email address is required.", 422)

    # Generic success response (same whether email exists or not)
    generic_ok = success(
        message=(
            f"If {email} is registered and verified, "
            f"a password-reset OTP has been sent."
        )
    )

    user = user_model.get_user_by_email(email)
    if not user or not user.get("is_verified"):
        return generic_ok   # Silent — don't reveal anything

    try:
        otp = otp_service.create_and_store_otp(email, "password_reset")
    except RuntimeError as exc:
        logger.error("OTP creation failed for forgot-password %s: %s", email, exc)
        return generic_ok

    sent = email_service.send_otp_email(email, otp, "password_reset")
    if not sent:
        logger.error("Password-reset OTP email failed for %s", email)

    return generic_ok


# ─────────────────────────────────────────────────────────────────────────────
# 6. RESET PASSWORD
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/reset-password", methods=["POST"])
def reset_password():
    """
    Verify a password-reset OTP and set a new password.

    Body (JSON):
        email         string  required
        otp           string  required  (6 digits)
        new_password  string  required
    """
    data         = request.get_json(silent=True) or {}
    email        = (data.get("email")        or "").strip().lower()
    otp          = (data.get("otp")          or "").strip()
    new_password = (data.get("new_password") or "").strip()

    if not email or not otp or not new_password:
        return error("email, otp, and new_password are all required.", 422)

    if not otp.isdigit() or len(otp) != 6:
        return error("OTP must be a 6-digit number.", 422)

    pwd_ok, pwd_msg = is_valid_password(new_password)
    if not pwd_ok:
        return error(pwd_msg, 422)

    # Check OTP was recently verified (already marked as used by /verify-otp)
    # Allow recently-used OTPs (within last 5 minutes) for password reset
    ok, msg = otp_service.check_recent_otp(email, otp, "password_reset", minutes=5)
    if not ok:
        return error(msg, 400)

    # Update password in Supabase
    new_hash = hash_password(new_password)
    updated  = user_model.update_password(email, new_hash)
    if not updated:
        return error("Failed to update password. Please try again.", 500)

    logger.info("Password reset successfully for %s", email)
    return success(message="Password has been reset. You can now log in.")


# ─────────────────────────────────────────────────────────────────────────────
# 7. GET CURRENT USER  (JWT protected)
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/me", methods=["GET"])
def me():
    """
    Return the authenticated user's profile.
    Header:  Authorization: Bearer <token>
    """
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return error("Authorization header is missing or invalid.", 401)

    token   = auth_header.split(" ", 1)[1].strip()
    payload = decode_access_token(token)

    if not payload:
        return error("Token is invalid or has expired. Please log in again.", 401)

    user = user_model.get_user_by_id(payload["sub"])
    if not user:
        return error("User account not found.", 404)

    return success({"user": user})


# ─────────────────────────────────────────────────────────────────────────────
# 8. HEALTH CHECK
# ─────────────────────────────────────────────────────────────────────────────
@auth_bp.route("/health", methods=["GET"])
def health():
    """
    Check SMTP connectivity. Useful for verifying .env credentials.
    GET /api/auth/health
    """
    smtp_ok, smtp_msg = email_service.test_smtp_connection()
    status = 200 if smtp_ok else 500
    return success(
        {
            "smtp": {
                "ok":      smtp_ok,
                "message": smtp_msg,
                "host":    current_app.config["SMTP_HOST"],
                "port":    current_app.config["SMTP_PORT"],
                "user":    current_app.config["SMTP_USER"] or "(not set)",
            }
        },
        "Health check complete.",
        status,
    )


# ─────────────────────────────────────────────────────────────────────────────
# Private helpers
# ─────────────────────────────────────────────────────────────────────────────
def _public_user(user: dict) -> dict:
    """Return only safe-to-expose fields (no password_hash)."""
    return {
        "id":          user.get("id"),
        "name":        user.get("name"),
        "email":       user.get("email"),
        "phone":       user.get("phone"),
        "role":        user.get("role"),
        "is_verified": user.get("is_verified"),
        "created_at":  user.get("created_at"),
    }

