📋 TECHNICAL REFERENCE - QUICKFIX FIXES IMPLEMENTATION
═══════════════════════════════════════════════════════════════════════════════

This document provides technical details for all implemented fixes.

═══════════════════════════════════════════════════════════════════════════════
FIX 1: REMOVE PROVIDER COUNT FROM ADMIN DASHBOARD
═══════════════════════════════════════════════════════════════════════════════

LOCATION:
  File: app/src/main/res/layout/activity_admin_dashboard.xml
  Lines: 234-266

CHANGE MADE:
  - Added android:visibility="gone" to the Total Providers LinearLayout
  - Provider stat card is now hidden but still in the XML

CODE BEFORE:
  <LinearLayout
      android:layout_width="0dp"
      android:layout_height="wrap_content"
      android:layout_weight="1"
      android:background="@drawable/bg_stat_green"
      ...

CODE AFTER:
  <LinearLayout
      android:layout_width="0dp"
      android:layout_height="wrap_content"
      android:layout_weight="1"
      android:background="@drawable/bg_stat_green"
      ...
      android:visibility="gone">

IMPACT:
  - Admin dashboard now shows only 3 stats: Users, Bookings, Pending Approvals
  - Java code (AdminDashboardActivity) still manages statTotalProviders but it's not visible
  - Can be easily reverted by removing visibility="gone"

═══════════════════════════════════════════════════════════════════════════════
FIX 2: PROVIDER DASHBOARD COUNTS
═══════════════════════════════════════════════════════════════════════════════

LOCATION:
  File: app/src/main/java/com/example/quickfix/ProviderDashboardActivity.java
  Primary method: loadDashboardStats() - Lines 147-234
  New methods: loadBookingsCount, loadEmergencyCount, loadEarnings

KEY IMPROVEMENT:
  Old approach:
    - Fetched ALL bookings for provider (inefficient)
    - Calculated counts from JSON array
    - Used provider_id instead of provider_uid
    
  New approach:
    - Leverages Supabase Prefer: count=exact header
    - Reads Content-Range response header for count
    - Uses provider_uid (Firebase UID) for filtering
    - Efficient single-row queries

METHOD: loadBookingsCount(String providerUid)
─────────────────────────────────────────────
Purpose: Get total count of bookings for provider

API Call:
  GET /rest/v1/bookings?select=id&provider_uid=eq.{UID}&limit=1
  
Headers:
  apikey: SUPABASE_ANON_KEY
  Authorization: Bearer {SUPABASE_ANON_KEY}
  Accept: application/json
  Prefer: count=exact

Response Parsing:
  String range = response.header("Content-Range", "");
  if (range != null && range.contains("/")) {
      String total = range.substring(range.lastIndexOf('/') + 1).trim();
      return Integer.parseInt(total);
  }

Example Return:
  Content-Range: 0-0/42
  → Returns 42 (total bookings count)

METHOD: loadEmergencyCount(String providerUid)
──────────────────────────────────────────────
Purpose: Get total count of emergency requests for provider

API Call:
  GET /rest/v1/emergency_requests?select=id&provider_uid=eq.{UID}&limit=1
  
Headers: (same as above, with Prefer: count=exact)

Logic: (identical to loadBookingsCount)

Example Return:
  Content-Range: 0-0/5
  → Returns 5 (emergency count for this provider)

METHOD: loadEarnings(String providerUid)
────────────────────────────────────────
Purpose: Calculate total earnings from completed bookings

API Call:
  GET /rest/v1/bookings?select=price&provider_uid=eq.{UID}&status=eq.completed
  
Note: This query does NOT use Prefer: count=exact because we need the actual price values

Response Processing:
  JSONArray bookings = getJsonArray(url);
  double total = 0d;
  for (int i = 0; i < bookings.length(); i++) {
      JSONObject b = bookings.getJSONObject(i);
      double price = parseDoubleSafe(b.opt("price"));
      total += price;
  }
  return total;

Example Data:
  [
    {"price": 500},
    {"price": 1200},
    {"price": 300}
  ]
  → Returns 2000.0 (formatted as ₹2,000 in UI)

INTEGRATION IN loadDashboardStats():
  Line 180: totalBookingsCount = loadBookingsCount(uid);
  Line 183: totalEarnings = loadEarnings(uid);
  Line 186: emergencyCount = loadEmergencyCount(uid);

UI UPDATE:
  Line 206: tvStatsBookings.setText(String.valueOf(finalTotal));
  Line 211: tvEmergency.setText(String.valueOf(finalEmergency));
  Line 230: tvStatsEarnings.setText("₹" + formatCurrency(finalEarnings));

═══════════════════════════════════════════════════════════════════════════════
FIX 3: EMERGENCY LIMIT (2 PER MONTH PER USER)
═══════════════════════════════════════════════════════════════════════════════

DATABASE FUNCTION: can_create_emergency(uid text)
──────────────────────────────────────────────────

File: supabase/sql/emergency_limit_function.sql

SQL Code:
  CREATE OR REPLACE FUNCTION can_create_emergency(uid text)
  RETURNS boolean AS $$
  DECLARE 
      total int;
  BEGIN
      SELECT COUNT(*) INTO total
      FROM emergency_requests
      WHERE user_id = uid
      AND created_at >= date_trunc('month', now())
      AND created_at < date_trunc('month', now()) + interval '1 month';
      
      RETURN total < 2;
  END;
  $$ LANGUAGE plpgsql SECURITY DEFINER;

Logic:
  1. Count emergency_requests where:
     - user_id matches provided uid
     - created_at is in current calendar month
  2. If count < 2 → return true (allowed)
  3. If count >= 2 → return false (blocked)

Key Points:
  - Only checks user_id, not provider_id
  - Uses date_trunc('month') for calendar month boundaries
  - SECURITY DEFINER allows anon/authenticated users to call
  - Resets monthly (not 30-day rolling window)

ANDROID INTEGRATION:
───────────────────
File: app/src/main/java/com/example/quickfix/EmergencyHelpActivity.java
Method: canCreateEmergency(String uid) - Lines 525-556
Called From: submitEmergencyRequest() - Line 393

Code:
  if (!canCreateEmergency(currentUserId)) {
      throw new IllegalStateException("Only 2 emergency requests allowed per month");
  }

Example Flow:
  User 1st emergency:
    Count: 0
    0 < 2 → true → Emergency created ✅
    
  User 2nd emergency:
    Count: 1
    1 < 2 → true → Emergency created ✅
    
  User 3rd emergency:
    Count: 2
    2 < 2 → false → Exception thrown ❌
    Toast: "Only 2 emergency requests allowed per month"

═══════════════════════════════════════════════════════════════════════════════
FIX 4: EMERGENCY NOTIFICATION TRIGGER
═══════════════════════════════════════════════════════════════════════════════

DATABASE TRIGGER: send_emergency_notification()
────────────────────────────────────────────────

File: supabase/sql/emergency_notification_trigger.sql

SQL Code:
  CREATE OR REPLACE FUNCTION send_emergency_notification()
  RETURNS trigger AS $$
  BEGIN
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

  CREATE TRIGGER emergency_notify_trigger
  AFTER INSERT ON emergency_requests
  FOR EACH ROW
  EXECUTE FUNCTION send_emergency_notification();

TRIGGER DETAILS:
─────────────────
Event: AFTER INSERT ON emergency_requests
Timing: For each newly created emergency request
Action: Insert notification record for provider

Data Mapping:
  NEW.provider_uid → notifications.user_id
  (provider receives notification at their user_id)

Notification Schema:
  {
    "user_id": "provider_firebase_uid",
    "title": "Emergency Request",
    "message": "You received a new emergency booking",
    "is_read": false,
    "created_at": "2025-01-15T10:30:00Z"
  }

FLOW DIAGRAM:
──────────────
User creates emergency request
  ↓
POST /rest/v1/emergency_requests (via Edge Function)
  ↓
INSERT INTO emergency_requests (provider_uid = "prov_123", ...)
  ↓
trigger: emergency_notify_trigger fires
  ↓
send_emergency_notification() executes
  ↓
INSERT INTO notifications (user_id = "prov_123", is_read = false, ...)
  ↓
Provider app fetches unread notifications
  ↓
GET /rest/v1/notifications?user_id=eq.prov_123&is_read=eq.false
  ↓
Notifications array returned to app
  ↓
Badge/red dot updated in UI

KEY FEATURES:
─────────────
- ON CONFLICT DO NOTHING: Prevents duplicate notifications if function runs twice
- SECURITY DEFINER: Trigger runs with elevated permissions (can insert to notifications)
- Automatic: No manual notification code needed in application
- Instant: Notification created immediately when emergency created
- RLS Safe: Uses user_id to ensure provider can only see their notifications

═══════════════════════════════════════════════════════════════════════════════
FIX 5: RED DOT LOGIC (SHOW ONLY IF UNREAD)
═══════════════════════════════════════════════════════════════════════════════

LOCATION:
  File: app/src/main/java/com/example/quickfix/MainActivity.java
  Method: loadUnreadNotificationBadge() - Lines 525-565
  Called: onCreate() (line 194) and onResume() (line 522)

API CALL:
──────────
GET /rest/v1/notifications?select=id&user_id=eq.{USER_ID}&is_read=eq.false&limit=1

Headers:
  apikey: SUPABASE_ANON_KEY
  Authorization: Bearer {SUPABASE_ANON_KEY}
  Accept: application/json
  Prefer: count=exact

Expected Response:
  HTTP/1.1 200 OK
  Content-Range: 0-0/3
  [
    {"id": "notif_1"}
  ]
  
  Means: 0-based index 0 to 0, out of 3 total
  → Total unread notifications: 3

CODE:
──────
String range = response.header("Content-Range", "");
if (range != null && range.contains("/")) {
    String countStr = range.substring(range.lastIndexOf('/') + 1).trim();
    count = Integer.parseInt(countStr);
}

final int finalCount = Math.max(0, count);
runOnUiThread(() -> {
    if (notificationBadge != null) {
        notificationBadge.setVisibility(finalCount > 0 ? View.VISIBLE : View.GONE);
    }
});

LOGIC:
──────
If count > 0:
  ✓ Red dot visible (notificationBadge.setVisibility(View.VISIBLE))
  
If count = 0:
  ✗ Red dot hidden (notificationBadge.setVisibility(View.GONE))

FLOW:
──────
1. User opens app
   → onCreate() called
   → loadUnreadNotificationBadge() called
   → Query unread count
   → Show/hide red dot based on count

2. User navigates away and back
   → onResume() called
   → loadUnreadNotificationBadge() called again
   → Count updated
   → Red dot state updated

3. User taps notification icon
   → Opens NotificationsActivity
   → Displays unread notifications

4. User marks notification as read
   → PATCH /rest/v1/notifications?user_id=eq.{UID}&is_read=eq.false
   → {"is_read": true}
   → Notification marked as read in DB

5. User navigates back to MainActivity
   → onResume() called
   → loadUnreadNotificationBadge() called
   → Count = 0 (no unread)
   → Red dot disappears

SIMILAR IMPLEMENTATION:
───────────────────────
AdminDashboardActivity.loadNotificationBadges() - Lines 752-773
  - Same logic but also for admin section badges
  - notificationBadge: unread notifications count
  - providerSectionBadge: unread provider notifications
  - userSectionBadge: unread user notifications

═══════════════════════════════════════════════════════════════════════════════
DATABASE SCHEMA CHANGES REQUIRED
═══════════════════════════════════════════════════════════════════════════════

TABLE: bookings
├── EXISTING columns
├── provider_id (UUID) - existing foreign key
├── NEW: provider_uid (TEXT) - Firebase UID of provider
└── INDEX: idx_bookings_provider_uid on provider_uid

TABLE: emergency_requests
├── EXISTING columns
├── provider_id (UUID) - existing, may be NULL
├── user_id (TEXT) - Firebase UID of user (requester)
├── NEW: provider_uid (TEXT) - Firebase UID of assigned provider
└── INDEX: idx_emergency_requests_provider_uid on provider_uid

QUERY EXAMPLES:
───────────────
Get all bookings for provider:
  SELECT * FROM bookings WHERE provider_uid = 'user_123';

Get completed bookings value:
  SELECT SUM(price) FROM bookings 
  WHERE provider_uid = 'user_123' 
  AND status = 'completed';

Count emergency requests for provider:
  SELECT COUNT(*) FROM emergency_requests 
  WHERE provider_uid = 'user_123';

Get unread notifications for user:
  SELECT COUNT(*) FROM notifications 
  WHERE user_id = 'user_123' 
  AND is_read = false;

═══════════════════════════════════════════════════════════════════════════════
TESTING QUERIES (Use Supabase SQL Editor)
═══════════════════════════════════════════════════════════════════════════════

Test emergency limit function:
  SELECT can_create_emergency('user_123');
  
  Expected output: true (if less than 2 emergency requests this month)

Test notification trigger manually:
  INSERT INTO emergency_requests (id, user_id, provider_uid, category, description)
  VALUES ('test_123', 'user_456', 'provider_789', 'Electrician', 'Test emergency');
  
  SELECT * FROM notifications WHERE user_id = 'provider_789';
  
  Expected: New notification record created automatically

Verify count=exact header works:
  Call API with Prefer: count=exact header
  Response should include Content-Range header
  Format: Content-Range: 0-{limit-1}/{total}

═══════════════════════════════════════════════════════════════════════════════
PERFORMANCE NOTES
═══════════════════════════════════════════════════════════════════════════════

Optimization: Prefer: count=exact
─────────────────────────────────
- Does NOT fetch full rows
- Only returns count in Content-Range header
- Limit 1 ensures minimal data transfer
- Much faster than fetching all rows and counting

Example timing:
  
  Before Fix 2 (fetch all):
    - If provider has 1000 bookings
    - Query fetches all 1000 rows
    - App counts in code
    - Time: ~500ms
    
  After Fix 2 (count=exact):
    - Uses PostgreSQL count
    - Returns only Content-Range header
    - Returns 0-0/1000
    - Time: ~50ms

Indexes:
─────────
  CREATE INDEX idx_bookings_provider_uid ON bookings(provider_uid);
  CREATE INDEX idx_emergency_requests_provider_uid ON emergency_requests(provider_uid);
  
  - These indexes make WHERE provider_uid = 'X' queries very fast
  - Should be created during migration

═══════════════════════════════════════════════════════════════════════════════
SECURITY CONSIDERATIONS
═══════════════════════════════════════════════════════════════════════════════

RLS (Row Level Security):
─────────────────────────
- notifications table should have RLS policy:
  SELECT WHERE user_id = auth.uid()
  UPDATE is_read WHERE user_id = auth.uid()
  
  This prevents users from seeing other users' notifications

Trigger Function:
──────────────────
- SECURITY DEFINER: Allows trigger to insert notifications even if user can't
- This is necessary because trigger needs to insert for provider
- Limited to INSERT ... ON CONFLICT DO NOTHING (safe)

API Keys:
─────────
- Queries use SUPABASE_ANON_KEY
- Is okay for selecting notifications (user_id filtered by auth.uid())
- Service role key used in Edge Functions (elevated permissions)

═══════════════════════════════════════════════════════════════════════════════

