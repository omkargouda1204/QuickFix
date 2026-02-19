"""
app/services/email_service.py
Real SMTP email delivery using Gmail (or any SMTP provider).
All credentials come from the .env file — nothing is hard-coded.
"""
import smtplib
import logging
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr
from flask import current_app

logger = logging.getLogger(__name__)


def send_otp_email(to_email: str, otp: str, purpose: str = "verification") -> bool:
    """
    Send a real OTP email via Gmail SMTP (TLS on port 587).

    Args:
        to_email : Recipient email address.
        otp      : 6-digit OTP string.
        purpose  : 'verification' | 'password_reset'

    Returns:
        True on success, False on any failure.
    """
    # Pull config values inside request context
    smtp_host    = current_app.config["SMTP_HOST"]
    smtp_port    = current_app.config["SMTP_PORT"]
    smtp_user    = current_app.config["SMTP_USER"]
    smtp_pass    = current_app.config["SMTP_PASSWORD"]
    from_name    = current_app.config["EMAIL_FROM_NAME"]
    from_email   = current_app.config["EMAIL_FROM"]
    expiry_mins  = current_app.config["OTP_EXPIRY_MINUTES"]   # ← real value from .env

    # Guard: never attempt to send if SMTP creds are missing
    if not smtp_user or not smtp_pass:
        logger.error(
            "SMTP credentials missing — set SMTP_USER and SMTP_PASSWORD in .env"
        )
        return False

    subject, html_body = _build_email_content(otp, purpose, expiry_mins)

    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"]    = formataddr((from_name, from_email))   # "QuickFix <you@gmail.com>"
    msg["To"]      = to_email
    msg["X-Mailer"] = "QuickFix-Backend/1.0"

    # Plain-text fallback (important for spam filters)
    plain = (
        f"Your QuickFix OTP code is: {otp}\n\n"
        f"This code expires in {expiry_mins} minutes.\n"
        "Do not share this code with anyone.\n\n"
        "If you did not request this, please ignore this email."
    )
    msg.attach(MIMEText(plain, "plain", "utf-8"))
    msg.attach(MIMEText(html_body, "html", "utf-8"))

    try:
        with smtplib.SMTP(smtp_host, smtp_port, timeout=20) as server:
            server.ehlo()
            server.starttls()        # Upgrade plain connection → TLS
            server.ehlo()
            server.login(smtp_user, smtp_pass)
            server.sendmail(smtp_user, to_email, msg.as_string())

        logger.info(
            "OTP email sent successfully → %s (purpose=%s)", to_email, purpose
        )
        return True

    except smtplib.SMTPAuthenticationError as exc:
        logger.error(
            "SMTP authentication failed for user '%s'. "
            "Make sure you are using a Gmail App Password "
            "(not your real password). Error: %s",
            smtp_user, exc,
        )
    except smtplib.SMTPRecipientsRefused as exc:
        logger.error("Recipient refused by SMTP server: %s — %s", to_email, exc)
    except smtplib.SMTPSenderRefused as exc:
        logger.error("Sender refused by SMTP server: %s — %s", smtp_user, exc)
    except smtplib.SMTPDataError as exc:
        logger.error("SMTP data error sending to %s: %s", to_email, exc)
    except smtplib.SMTPConnectError as exc:
        logger.error(
            "Could not connect to SMTP server %s:%s — %s", smtp_host, smtp_port, exc
        )
    except smtplib.SMTPException as exc:
        logger.error("Generic SMTP error sending to %s: %s", to_email, exc)
    except OSError as exc:
        logger.error(
            "Network error connecting to SMTP server %s:%s — %s",
            smtp_host, smtp_port, exc,
        )
    except Exception as exc:
        logger.exception("Unexpected error sending OTP email to %s: %s", to_email, exc)

    return False


def test_smtp_connection() -> tuple[bool, str]:
    """
    Test SMTP credentials without sending an email.
    Useful for a /health endpoint or startup check.
    Returns (ok: bool, message: str).
    """
    smtp_host = current_app.config["SMTP_HOST"]
    smtp_port = current_app.config["SMTP_PORT"]
    smtp_user = current_app.config["SMTP_USER"]
    smtp_pass = current_app.config["SMTP_PASSWORD"]

    if not smtp_user or not smtp_pass:
        return False, "SMTP_USER / SMTP_PASSWORD not set in .env"

    try:
        with smtplib.SMTP(smtp_host, smtp_port, timeout=10) as server:
            server.ehlo()
            server.starttls()
            server.ehlo()
            server.login(smtp_user, smtp_pass)
        return True, f"SMTP connection OK ({smtp_host}:{smtp_port})"
    except smtplib.SMTPAuthenticationError:
        return False, "SMTP authentication failed — check App Password"
    except Exception as exc:
        return False, f"SMTP connection failed: {exc}"


# ── Private helpers ───────────────────────────────────────────────────────────

def _build_email_content(otp: str, purpose: str, expiry_mins: int) -> tuple[str, str]:
    """Return (subject, html_body) for the given purpose."""
    if purpose == "password_reset":
        subject = "QuickFix — Password Reset OTP"
        action  = "reset your password"
    else:
        subject = "QuickFix — Email Verification OTP"
        action  = "verify your email address"

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1.0">
  <title>{subject}</title>
</head>
<body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
         style="background-color:#f4f7fb;padding:40px 16px;">
    <tr>
      <td align="center">
        <table role="presentation" width="100%" style="max-width:500px;"
               cellpadding="0" cellspacing="0"
               style="background:#ffffff;border-radius:12px;
                      box-shadow:0 2px 12px rgba(0,0,0,0.10);overflow:hidden;">

          <!-- ── Header ── -->
          <tr>
            <td align="center"
                style="background:linear-gradient(135deg,#1565C0 0%,#002171 100%);
                       padding:32px 24px;">
              <h1 style="margin:0;color:#ffffff;font-size:28px;
                         font-weight:bold;letter-spacing:2px;
                         font-family:Arial,sans-serif;">
                QuickFix
              </h1>
              <p style="margin:8px 0 0;color:#90CAF9;font-size:13px;
                        font-family:Arial,sans-serif;">
                Your Service Solution
              </p>
            </td>
          </tr>

          <!-- ── Body ── -->
          <tr>
            <td style="padding:36px 32px;">
              <p style="margin:0 0 8px;color:#333333;font-size:16px;
                        font-family:Arial,sans-serif;">
                Hello,
              </p>
              <p style="margin:0 0 28px;color:#555555;font-size:15px;
                        line-height:1.7;font-family:Arial,sans-serif;">
                Use the one-time code below to <strong>{action}</strong>.
                This code expires in <strong>{expiry_mins} minutes</strong>.
              </p>

              <!-- ── OTP Box ── -->
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                     style="margin:0 0 28px;">
                <tr>
                  <td align="center">
                    <div style="display:inline-block;background:#EEF4FF;
                                border:2px dashed #1565C0;border-radius:10px;
                                padding:20px 48px;">
                      <span style="font-size:42px;font-weight:bold;
                                   letter-spacing:12px;color:#1565C0;
                                   font-family:'Courier New',monospace;">
                        {otp}
                      </span>
                    </div>
                  </td>
                </tr>
              </table>

              <p style="margin:0 0 6px;color:#888888;font-size:13px;
                        font-family:Arial,sans-serif;">
                ⚠️ <strong>Never share this code.</strong>
                QuickFix will never ask you for your OTP.
              </p>
              <p style="margin:0;color:#aaaaaa;font-size:12px;
                        font-family:Arial,sans-serif;">
                If you did not request this, please ignore this email.
              </p>
            </td>
          </tr>

          <!-- ── Footer ── -->
          <tr>
            <td align="center"
                style="background:#f9f9f9;padding:16px 24px;
                       border-top:1px solid #eeeeee;">
              <p style="margin:0;color:#bbbbbb;font-size:11px;
                        font-family:Arial,sans-serif;">
                © 2025 QuickFix. All rights reserved.
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>"""

    return subject, html

