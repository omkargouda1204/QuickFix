# Quick Troubleshooting Checklist - HTTP 400 Errors

## For Provider Dashboard Pages (My Bookings, My Services, Earnings)

### Immediate Actions:

1. **Run the app and go to Provider Dashboard**
2. **Tap on "My Bookings" (or My Services / Earnings)**
3. **Open Android Studio Logcat** (View → Tool Windows → Logcat)
4. **Filter by "MyBookingsActivity"** to see detailed logs
5. **Look for a log line like:**
   ```
   D/MyBookingsActivity: loadBookings response: HTTP 400 | Body: {"code":"...","details":"...","message":"..."}
   ```

### What the Error Message Will Tell You:

**If you see:** `"column 'provider_id' does not exist"`
- **Action:** Check Supabase table schema. The correct column name might be different.
- **Fix:** Update the query in the Java code with correct column name

**If you see:** `"relation 'bookings' does not exist"` 
- **Action:** The bookings table doesn't exist or has a different name
- **Fix:** Update the table name in the REST API URL

**If you see:** `"permission denied"` or `"403"`
- **Action:** Row Level Security policy is blocking access
- **Fix:** Update RLS policies in Supabase to allow anon key access

**If you see:** `"query timeout"`
- **Action:** Query is running too slow, likely missing index
- **Fix:** Add database index on the provider_id column in Supabase

---

## Key Changes Made to Your Code:

### MyBookingsActivity.java
- ✅ Added null check for providerId
- ✅ Added detailed URL logging
- ✅ Added response body logging (contains error details)
- ✅ Enhanced error message in Toast

### MyServicesActivity.java
- ✅ Added null check for providerId
- ✅ Added detailed URL logging  
- ✅ Added response body logging
- ✅ Enhanced error message in Toast

### EarningsActivity.java
- ✅ Added null check for providerId
- ✅ Added detailed URL logging
- ✅ Added response body logging
- ✅ Enhanced error message in Toast

---

## Debugging Steps:

### Step 1: Identify the Error Message
- Run app → Go to My Bookings → Check Logcat for error message from Supabase

### Step 2: Check Supabase Database Schema
- Go to Supabase dashboard → SQL Editor
- Run: `SELECT * FROM bookings LIMIT 1;` to see actual column names
- Compare with the code: are we filtering on the right column?

### Step 3: Verify Table Exists
- Run: `SELECT table_name FROM information_schema.tables WHERE table_schema='public';`
- Confirm `bookings` and `provider_services` tables exist

### Step 4: Check RLS Policies
- Go to Supabase → Authentication → Policies
- Ensure anon key can SELECT from bookings table
- For your provider_id, make sure the policy allows it

### Step 5: Update Code If Needed
If the column name is different (e.g., `firebase_uid` instead of `provider_id`):

**MyBookingsActivity.java** (around line 151):
```java
String url = SUPABASE_URL + "/rest/v1/bookings" +
        "?ACTUAL_COLUMN_NAME=eq." + providerId +  // <-- Change this
        "&order=created_at.desc" +
        "&select=*";
```

Do the same for MyServicesActivity.java and EarningsActivity.java

---

## Files to Reference:
- 📄 `HTTP_400_DEBUG_GUIDE.md` - Detailed debugging guide
- 📄 `MyBookingsActivity.java` - Lines 140-200
- 📄 `MyServicesActivity.java` - Lines 130-160
- 📄 `EarningsActivity.java` - Lines 114-150

---

**Status:** Enhanced error logging is now in place. Run the app and check Logcat for the detailed error message to identify the root cause.

