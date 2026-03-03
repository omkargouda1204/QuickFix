# HTTP 400 Error Fix Guide - Provider Dashboard

## Problem
The following pages show "ERROR: HTTP 400":
- My Bookings (MyBookingsActivity)
- My Services (MyServicesActivity) 
- Earnings (EarningsActivity)

## Root Cause
HTTP 400 (Bad Request) from Supabase PostgREST API typically means:
1. **Column doesn't exist** - The filter is trying to query a column that doesn't exist in the table
2. **Invalid filter syntax** - Though our syntax appears correct based on Supabase documentation
3. **Type mismatch** - The value type doesn't match the column type

## Applied Fixes
We've added detailed error logging that will show the actual error message from Supabase:

### Changes Made:
1. **Added null/empty checks** for `providerId` before making requests
2. **Added detailed logging** in all three activities that shows:
   - The complete URL being requested
   - The full HTTP response body (contains Supabase error details)
3. **Enhanced Toast messages** to display the Supabase error message

## How to Debug

### Step 1: Run the app and navigate to one of the failing pages

### Step 2: Check Android Logcat for detailed logs:
```
D/MyBookingsActivity: loadBookings URL: https://YOUR_PROJECT.supabase.co/rest/v1/bookings?provider_id=eq.YOUR_UID&order=created_at.desc&select=*
D/MyBookingsActivity: loadBookings response: HTTP 400 | Body: {"code":"PGRST116","details":...,"message":"..."}
```

### Step 3: The error message will tell you exactly what's wrong

## Possible Solutions

### Solution A: Column name is different
If the error says "column 'provider_id' does not exist", you need to:
1. Check your Supabase database schema
2. Find the correct column name in the `bookings` table
3. Update these lines in the code:

**MyBookingsActivity.java** (line ~147):
```java
String url = SUPABASE_URL + "/rest/v1/bookings" +
        "?CORRECT_COLUMN_NAME=eq." + providerId +
        ...
```

### Solution B: Table name is different
If the error says "relation 'bookings' does not exist":
1. Check the table name in Supabase
2. Update the URL path `/rest/v1/bookings` to `/rest/v1/YOUR_TABLE_NAME`

### Solution C: RLS Policy blocking access
If error mentions permission denied:
1. Go to Supabase → SQL Editor
2. Check your Row Level Security (RLS) policies
3. Ensure the anon key has SELECT permission for your provider

## Files Modified
- `MyBookingsActivity.java` - Added logging and null checks
- `MyServicesActivity.java` - Added logging and null checks  
- `EarningsActivity.java` - Added logging and null checks

## Next Steps

1. **Run the app** and try accessing the failing pages
2. **Check Logcat** for the detailed error message
3. **Share the error message** to understand the root cause
4. **Update the column/table names** once you identify the issue
5. **Test again** to confirm the fix works

## Example Error Messages & Solutions

### Error: "column 'provider_id' does not exist"
- **Cause**: The bookings table doesn't have a `provider_id` column
- **Fix**: Check Supabase schema, find the correct column name (might be `firebase_uid`, `provider_firebase_uid`, etc.)

### Error: "relation 'bookings' does not exist"  
- **Cause**: The table isn't named `bookings`
- **Fix**: Query the correct table name from Supabase

### Error: "query timeout"
- **Cause**: No index on the filter column causing slow query
- **Fix**: Add database index on `provider_id` column in Supabase

---

**Last Updated**: March 4, 2026

