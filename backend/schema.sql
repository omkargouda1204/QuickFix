-- ============================================================
--  QuickFix — Supabase Database Schema
--  Run this entire file once in the Supabase SQL Editor:
--  https://supabase.com → Your Project → SQL Editor → New query
-- ============================================================

-- ── 1. USERS TABLE ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT        NOT NULL,
    email         TEXT        NOT NULL UNIQUE,
    phone         TEXT,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'user'
                              CHECK (role IN ('user', 'provider', 'admin')),
    is_verified   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fast email lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON public.users (email);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON public.users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


-- ── 2. OTP CODES TABLE ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.otp_codes (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email      TEXT        NOT NULL,
    otp_code   TEXT        NOT NULL,
    purpose    TEXT        NOT NULL
                           CHECK (purpose IN ('verification', 'password_reset')),
    is_used    BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fast OTP lookups
CREATE INDEX IF NOT EXISTS idx_otp_email_purpose
    ON public.otp_codes (email, purpose, is_used);


-- ── 3. ROW LEVEL SECURITY ────────────────────────────────────
-- The backend uses the service-role key which bypasses RLS.
-- Enable RLS so direct client access is blocked.
ALTER TABLE public.users    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.otp_codes ENABLE ROW LEVEL SECURITY;

-- No public policies — all access must go through the backend.


-- ── 4. OPTIONAL: CLEANUP OLD OTP CODES ──────────────────────
-- Run this periodically (e.g. via Supabase cron) to keep the table small.
-- DELETE FROM public.otp_codes
--     WHERE expires_at < NOW() - INTERVAL '1 day';

