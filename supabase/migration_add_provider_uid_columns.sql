-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Add provider_uid column to bookings and emergency_requests tables
-- ═══════════════════════════════════════════════════════════════════════════
-- This migration adds the provider_uid column to track which provider handled
-- each booking and emergency request. This is essential for provider dashboard
-- to correctly filter data by provider.
-- ═══════════════════════════════════════════════════════════════════════════

-- Add provider_uid to bookings table
ALTER TABLE bookings
ADD COLUMN provider_uid text;

-- Add comment explaining the column
COMMENT ON COLUMN bookings.provider_uid IS 'Firebase UID of the provider who accepted/handled this booking';

-- Add index for faster filtering
CREATE INDEX IF NOT EXISTS idx_bookings_provider_uid ON bookings(provider_uid);

-- ═══════════════════════════════════════════════════════════════════════════

-- Add provider_uid to emergency_requests table
ALTER TABLE emergency_requests
ADD COLUMN provider_uid text;

-- Add comment explaining the column
COMMENT ON COLUMN emergency_requests.provider_uid IS 'Firebase UID of the provider who responded to this emergency request';

-- Add index for faster filtering
CREATE INDEX IF NOT EXISTS idx_emergency_requests_provider_uid ON emergency_requests(provider_uid);

-- ═══════════════════════════════════════════════════════════════════════════
-- Note: Existing data will have NULL for provider_uid. New records will have
-- the provider_uid populated when created. If you need to backfill existing
-- data, you can use the provider_id column and join with provider_profiles
-- to find the firebase_uid.
-- ═══════════════════════════════════════════════════════════════════════════

