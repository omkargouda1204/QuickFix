-- ═══════════════════════════════════════════════════════════════════════════
-- COMPLETE EMERGENCY NOTIFICATIONS IMPLEMENTATION
-- ═══════════════════════════════════════════════════════════════════════════
--
-- This file documents the complete flow for emergency notifications:
--
-- 1. User creates emergency request
-- 2. Trigger automatically inserts notification record for provider
-- 3. Provider app fetches unread notifications
-- 4. Red dot shows on notification icon when unread count > 0
-- 5. When provider marks notification as read, red dot disappears
--
-- ═══════════════════════════════════════════════════════════════════════════

-- STEP 1: Ensure notifications table has correct structure
-- (This table should already exist, but verify it has these columns)
--
-- TABLE: notifications
-- ├── id (UUID) - Primary key
-- ├── user_id (TEXT) - Firebase UID of the user receiving notification
-- ├── title (TEXT) - Notification title
-- ├── message (TEXT) - Notification message
-- ├── is_read (BOOLEAN) - Read status (default: false)
-- ├── created_at (TIMESTAMP) - Creation timestamp
-- └── (other fields as needed)

-- ═══════════════════════════════════════════════════════════════════════════

-- STEP 2: Mark notifications as read
--
-- When user/provider reads the notification, send:
--
-- PATCH /rest/v1/notifications?user_id=eq.PROVIDER_UID&is_read=eq.false
-- Headers:
--   apikey: SUPABASE_ANON_KEY
--   Authorization: Bearer SUPABASE_ANON_KEY
--   Content-Type: application/json
--
-- Body:
--   {
--     "is_read": true
--   }
--
-- This updates all unread notifications for that provider to read=true
-- The app will then refetch the unread count, which returns 0, hiding the red dot

-- ═══════════════════════════════════════════════════════════════════════════

-- STEP 3: Android App Flow Summary
--
-- 1. onCreate() → loadUnreadNotificationBadge()
--    - Queries: GET /rest/v1/notifications?is_read=eq.false&user_id=eq.USER_UID
--    - Reads Content-Range header to get total count
--    - Shows red dot if count > 0
--
-- 2. onResume() → loadUnreadNotificationBadge()  (called again to refresh)
--
-- 3. User taps notification icon → Opens NotificationsActivity
--
-- 4. NotificationsActivity fetches and displays unread notifications
--
-- 5. When user marks as read, sends:
--    PATCH /rest/v1/notifications/{id}
--    { "is_read": true }
--
-- 6. App refetches badge count, red dot disappears if count = 0

-- ═══════════════════════════════════════════════════════════════════════════

-- STEP 4: Emergency Request to Notification Flow
--
-- When emergency created (via Edge Function):
-- 1. INSERT INTO emergency_requests (provider_uid, user_id, ...)
-- 2. Trigger fires: send_emergency_notification()
-- 3. INSERT INTO notifications (user_id=provider_uid, is_read=false, ...)
-- 4. Provider's app polls/fetches notifications
-- 5. Notification appears in list with red badge

-- ═══════════════════════════════════════════════════════════════════════════

-- IMPORTANT: RLS Policies required on notifications table
--
-- Insert Policy (for triggers):
--   Allow service_role to insert, deny anon/authenticated
--
-- Select Policy:
--   Allow users to select their own notifications
--   SELECT WHERE user_id = auth.uid()
--
-- UPDATE Policy:
--   Allow users to update is_read for their own notifications
--   UPDATE WHERE user_id = auth.uid()

-- ═══════════════════════════════════════════════════════════════════════════

