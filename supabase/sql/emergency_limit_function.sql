-- ═══════════════════════════════════════════════════════════════════════════
-- Emergency Request Limit Function
-- ═══════════════════════════════════════════════════════════════════════════
-- This function checks if a user can create an emergency request.
-- Users are limited to 2 emergency requests per calendar month.
-- Providers have unlimited emergency requests.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION can_create_emergency(uid text)
RETURNS boolean AS $$
DECLARE
    total int;
BEGIN
    -- Count emergency requests created by this user in the current month
    SELECT COUNT(*) INTO total
    FROM emergency_requests
    WHERE user_id = uid
    AND created_at >= date_trunc('month', now())
    AND created_at < date_trunc('month', now()) + interval '1 month';

    -- Return true if user has less than 2 emergency requests this month
    RETURN total < 2;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute permission to anon and authenticated users
GRANT EXECUTE ON FUNCTION can_create_emergency(text) TO anon, authenticated;

-- ═══════════════════════════════════════════════════════════════════════════
-- Usage from Android app:
--
-- POST https://your-supabase-url/rest/v1/rpc/can_create_emergency
-- Headers:
--   apikey: SUPABASE_ANON_KEY
--   Authorization: Bearer SUPABASE_ANON_KEY
--   Content-Type: application/json
--
-- Body:
--   {
--     "uid": "USER_FIREBASE_UID"
--   }
--
-- Response:
--   true  → User can create emergency request
--   false → User has reached their 2 per month limit
-- ═══════════════════════════════════════════════════════════════════════════


