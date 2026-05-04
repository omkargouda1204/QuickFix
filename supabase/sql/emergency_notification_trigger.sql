-- ═══════════════════════════════════════════════════════════════════════════
-- Emergency Request Notification Trigger
-- ═══════════════════════════════════════════════════════════════════════════
-- When a new emergency request is created, automatically insert a notification
-- record for the provider. This ensures providers get instant notifications.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION send_emergency_notification()
RETURNS trigger AS $$
BEGIN
    -- Insert notification record for the provider
    INSERT INTO notifications (user_id, title, message, is_read, created_at)
    VALUES (
        NEW.provider_uid,
        'Emergency Request',
        'You received a new emergency booking',
        false,
        now()
    )
    ON CONFLICT DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop existing trigger if it exists (allows re-running this script)
DROP TRIGGER IF EXISTS emergency_notify_trigger ON emergency_requests;

-- Create the trigger that fires AFTER each INSERT on emergency_requests
CREATE TRIGGER emergency_notify_trigger
AFTER INSERT ON emergency_requests
FOR EACH ROW
EXECUTE FUNCTION send_emergency_notification();

-- ═══════════════════════════════════════════════════════════════════════════
-- How it works:
-- 1. User creates an emergency request via the app
-- 2. Record is inserted into emergency_requests table with provider_uid set
-- 3. This trigger fires automatically
-- 4. A notification is inserted with:
--    - user_id = provider_uid (so the provider receives it)
--    - title = "Emergency Request"
--    - message = "You received a new emergency booking"
--    - is_read = false (unread, shows red dot)
-- 5. Provider's app fetches unread notifications and shows badge
-- ═══════════════════════════════════════════════════════════════════════════

