# HTTP 404 Error - Quick Fix

## Error Message You're Seeing:
```
Error: HTTP 404 | {"code":"PGRST205", "details":null,"hint":"Perhaps you meant..."}
```

## What This Means:
**The `bookings` or `provider_services` table does not exist in your Supabase database.**

## ⚡ Quick Fix (3 Minutes):

### Step 1: Open Supabase SQL Editor
- Go to https://supabase.com/dashboard
- Select your QuickFix project
- Click **SQL Editor** (left sidebar)
- Click **New Query**

### Step 2: Copy & Paste This SQL

```sql
-- Create bookings table
CREATE TABLE IF NOT EXISTS bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    customer_name TEXT NOT NULL,
    customer_phone TEXT,
    provider_id UUID NOT NULL,
    provider_name TEXT,
    service_id UUID,
    service_name TEXT NOT NULL,
    category TEXT,
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    address TEXT,
    city TEXT,
    price DECIMAL(10, 2) NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_provider FOREIGN KEY (provider_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create provider_services table
CREATE TABLE IF NOT EXISTS provider_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id UUID NOT NULL,
    provider_name TEXT,
    service_name TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_provider FOREIGN KEY (provider_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_bookings_provider_id ON bookings(provider_id);
CREATE INDEX IF NOT EXISTS idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_provider_services_provider_id ON provider_services(provider_id);

-- Enable RLS
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_services ENABLE ROW LEVEL SECURITY;

-- Add RLS policies
CREATE POLICY "Anon can read" ON bookings FOR SELECT USING (true);
CREATE POLICY "Anon can read" ON provider_services FOR SELECT USING (true);
```

### Step 3: Click **Run**
- Wait for "Query successful" message
- Takes about 5-10 seconds

### Step 4: Rebuild & Run App
```
Android Studio → Build → Rebuild Project
```

Then run the app again.

---

## ✅ Verify It Worked

Run this in SQL Editor to confirm:

```sql
SELECT COUNT(*) as bookings_table_exists FROM information_schema.tables 
WHERE table_name='bookings';
```

Should return: `1`

---

## Still Getting 404?

### Check #1: Did the SQL run?
- Look for green "Query successful" message in Supabase
- If there's an error, check the error message

### Check #2: Are the tables there?
```sql
SELECT table_name FROM information_schema.tables 
WHERE table_schema='public' AND table_name IN ('bookings', 'provider_services');
```

Should show both tables.

### Check #3: Rebuild the app
```
Android Studio → Build → Clean Project
Android Studio → Build → Rebuild Project
```

### Check #4: Clear app data
- Android Studio → Run → Run 'app'
- If app starts, go to: Settings → Apps → QuickFix → Storage → Clear Data
- Restart app

---

## For More Details:

See: `DATABASE_SETUP_GUIDE.md` (detailed guide with explanations)

---

**Expected Result:** ✅ All HTTP 404 errors fixed!

