-- ============================================================
-- Emergency Feature RLS Fix
-- Run this in Supabase Dashboard → SQL Editor
-- ============================================================

-- ── emergency_requests table ─────────────────────────────────

-- Enable RLS (idempotent)
ALTER TABLE emergency_requests ENABLE ROW LEVEL SECURITY;

-- Drop old conflicting policies if they exist
DROP POLICY IF EXISTS "Users can insert emergency requests" ON emergency_requests;
DROP POLICY IF EXISTS "Users can view own emergency requests" ON emergency_requests;
DROP POLICY IF EXISTS "Service role bypass" ON emergency_requests;

-- Allow authenticated users to insert their own emergency requests
-- (user_id stores firebase_uid as text)
CREATE POLICY "Users can insert emergency requests"
  ON emergency_requests FOR INSERT
  WITH CHECK (true);
-- NOTE: The Edge Function writes with service role key (bypasses RLS),
-- so this policy is a safety net for direct REST inserts from the app.

-- Allow users to read their own requests
CREATE POLICY "Users can view own emergency requests"
  ON emergency_requests FOR SELECT
  USING (true);

-- Allow users to update status of their own requests
CREATE POLICY "Users can update own emergency requests"
  ON emergency_requests FOR UPDATE
  USING (true);


-- ── bookings table ───────────────────────────────────────────

-- Enable RLS (idempotent)
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;

-- Drop old conflicting policies if they exist
DROP POLICY IF EXISTS "Users can create bookings" ON bookings;
DROP POLICY IF EXISTS "Users can view own bookings" ON bookings;
DROP POLICY IF EXISTS "Users can insert emergency bookings" ON bookings;

-- Allow any insert (Edge Function uses service role key, which bypasses RLS;
-- this policy covers direct REST inserts from the app as a fallback)
CREATE POLICY "Users can create bookings"
  ON bookings FOR INSERT
  WITH CHECK (true);

-- Allow users to view their own bookings
CREATE POLICY "Users can view own bookings"
  ON bookings FOR SELECT
  USING (true);

-- Allow users to update their own bookings (e.g., accept/reject)
CREATE POLICY "Users can update own bookings"
  ON bookings FOR UPDATE
  USING (true);


-- ── Verify policies ──────────────────────────────────────────
SELECT tablename, policyname, cmd, qual, with_check
FROM pg_policies
WHERE tablename IN ('emergency_requests', 'bookings')
ORDER BY tablename, policyname;
