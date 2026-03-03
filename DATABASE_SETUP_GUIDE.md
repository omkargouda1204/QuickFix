# ✅ Supabase Database Setup Guide

## Problem
The app is showing HTTP 404 errors because the required database tables don't exist.

### Error Message:
```
Error: HTTP 404 | {"code":"PGRST205", "details":null, "hint":"Perhaps you meant..."}
```

### Root Cause:
The `bookings` and `provider_services` tables are missing from your Supabase database.

---

## 🔧 Solution: Create Database Tables

### Step 1: Go to Supabase Dashboard

1. Open: https://supabase.com/dashboard
2. Select your **QuickFix** project
3. Go to **SQL Editor** (in left sidebar)

### Step 2: Create New Query

1. Click **New Query** button
2. Give it a name: `Create Bookings & Services Tables`
3. Copy the entire SQL from `SUPABASE_DATABASE_SETUP.sql`
4. Paste into the SQL editor

### Step 3: Run the SQL

1. Click the **Run** button (or press Ctrl+Enter)
2. Wait for it to complete (should take a few seconds)
3. You should see: **"Query successful"** message

### Step 4: Verify Tables Were Created

Run this verification query in a new SQL Editor tab:

```sql
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema='public' 
  AND table_name IN ('bookings', 'provider_services');
```

You should see both tables listed.

---

## 📋 What Gets Created

### 1. **bookings** Table
Stores all service booking requests from customers to providers.

**Columns:**
- `id` - Unique identifier (UUID)
- `customer_id` - Who requested the service
- `customer_name` - Customer's name
- `customer_phone` - Customer's phone
- `provider_id` - Who provides the service
- `provider_name` - Provider's name
- `service_id` - Which service was booked
- `service_name` - Name of the service
- `category` - Service category
- `booking_date` - Date of booking
- `booking_time` - Time of booking
- `address` - Service location address
- `city` - Service location city
- `price` - Cost of service
- `description` - Additional details
- `status` - One of: pending, accepted, completed, cancelled
- `created_at` - When booking was made
- `completed_at` - When service was completed

### 2. **provider_services** Table
Stores services offered by each provider.

**Columns:**
- `id` - Unique identifier (UUID)
- `provider_id` - Which provider offers this
- `provider_name` - Provider's name
- `service_name` - Name of the service
- `category` - Service category
- `description` - Service details
- `price` - Cost of service
- `is_available` - Whether available (true/false)
- `created_at` - When service was added
- `updated_at` - Last update time

---

## 🔐 Security Features (RLS)

The setup also enables **Row Level Security (RLS)** which means:

✅ **Providers** can only see/edit their own bookings and services
✅ **Customers** can only see/edit their own bookings
✅ **Public browsing** allowed for available services
✅ **Anon key** can read services (for app browsing)

---

## ✅ After Setup - Test It

### 1. Rebuild the app
```
Android Studio → Build → Rebuild Project
```

### 2. Run the app again

### 3. Navigate to Provider Dashboard → "My Bookings"
- Should load without errors
- Will show empty list (no bookings yet - that's normal)

### 4. Try "My Services"
- Should load without errors
- Try adding a service

### 5. Try "Earnings"
- Should load without errors
- Will show empty list (no completed bookings yet)

---

## 🆘 Troubleshooting

### Still getting 404?
- [ ] Did the SQL run successfully? (look for "Query successful" message)
- [ ] Did you see the tables in the verification query?
- [ ] Try rebuilding the app with `Build → Rebuild Project`

### Getting permission errors?
- The RLS policies allow anon key access
- If still failing, check that your API key is set correctly in `supabase.properties`

### Getting timeout errors?
- The indexes should improve performance
- If still slow, tables might need more data
- This usually resolves after first few queries

---

## 📝 SQL File Reference

The complete SQL file is in: `SUPABASE_DATABASE_SETUP.sql`

**What it includes:**
1. ✅ CREATE TABLE bookings
2. ✅ CREATE TABLE provider_services
3. ✅ CREATE INDEXES for performance
4. ✅ ENABLE ROW LEVEL SECURITY
5. ✅ CREATE RLS POLICIES for access control
6. ✅ Optional sample data (commented out)

---

## 🎯 Next Steps

After tables are created:

1. ✅ Bookings can be made from the customer home screen
2. ✅ Providers can manage their services in "My Services"
3. ✅ Providers can view incoming bookings in "My Bookings"
4. ✅ Providers can track earnings in "Earnings"

---

## 📞 Still Need Help?

If you're still getting errors after running the SQL:

1. **Check table structure:**
   ```sql
   SELECT * FROM bookings LIMIT 1;
   ```

2. **Check for constraints:**
   ```sql
   SELECT constraint_name FROM information_schema.table_constraints 
   WHERE table_name='bookings';
   ```

3. **Verify RLS policies:**
   Go to Supabase → Authentication → Policies
   Look for policies on `bookings` and `provider_services` tables

---

**Status:** Ready to setup ✅

Once tables are created, all HTTP 404 errors should be resolved!

