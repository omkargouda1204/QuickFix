-- ============================================================================
-- CORRECTED DATABASE SETUP - Uses firebase_uid (not provider_id)
-- ============================================================================
-- This SQL fixes the HTTP 400 error (code 42703 - undefined column)
-- Run this in Supabase SQL Editor to FIX the existing tables
-- ============================================================================

-- ============================================================================
-- OPTION 1: ALTER EXISTING TABLES (If tables already exist)
-- ============================================================================
-- If you already ran the previous SQL and have tables with provider_id column,
-- you can rename the column to firebase_uid:

-- ALTER TABLE bookings RENAME COLUMN provider_id TO firebase_uid;
-- ALTER TABLE provider_services RENAME COLUMN provider_id TO firebase_uid;

-- ============================================================================
-- OPTION 2: DROP AND RECREATE (If tables are empty)
-- ============================================================================
-- If tables exist but are empty, drop and recreate with correct schema:

DROP TABLE IF EXISTS bookings CASCADE;
DROP TABLE IF EXISTS provider_services CASCADE;

-- ============================================================================
-- CREATE BOOKINGS TABLE (with firebase_uid)
-- ============================================================================
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Customer Information
    customer_id UUID NOT NULL,
    customer_name TEXT NOT NULL,
    customer_phone TEXT,

    -- Provider Information (uses firebase_uid to match users table)
    firebase_uid UUID NOT NULL,
    provider_name TEXT,

    -- Service Information
    service_id UUID,
    service_name TEXT NOT NULL,
    category TEXT,

    -- Booking Details
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    address TEXT,
    city TEXT,
    price DECIMAL(10, 2) NOT NULL,
    description TEXT,

    -- Status Management
    status TEXT NOT NULL DEFAULT 'pending',

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- Foreign Key Constraints
    CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_provider FOREIGN KEY (firebase_uid) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_bookings_firebase_uid ON bookings(firebase_uid);
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_created_at ON bookings(created_at DESC);

-- ============================================================================
-- CREATE PROVIDER_SERVICES TABLE (with firebase_uid)
-- ============================================================================
CREATE TABLE provider_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Provider Information (uses firebase_uid to match users table)
    firebase_uid UUID NOT NULL,
    provider_name TEXT,

    -- Service Details
    service_name TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,

    -- Availability Status
    is_available BOOLEAN DEFAULT TRUE,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Foreign Key Constraint
    CONSTRAINT fk_provider FOREIGN KEY (firebase_uid) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_provider_services_firebase_uid ON provider_services(firebase_uid);
CREATE INDEX idx_provider_services_category ON provider_services(category);
CREATE INDEX idx_provider_services_is_available ON provider_services(is_available);
CREATE INDEX idx_provider_services_created_at ON provider_services(created_at DESC);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_services ENABLE ROW LEVEL SECURITY;

-- Bookings policies
CREATE POLICY "Providers can view their bookings" ON bookings
    FOR SELECT
    USING (auth.uid() = firebase_uid OR auth.uid() = customer_id);

CREATE POLICY "Customers can view their bookings" ON bookings
    FOR SELECT
    USING (auth.uid() = customer_id);

CREATE POLICY "Customers can create bookings" ON bookings
    FOR INSERT
    WITH CHECK (auth.uid() = customer_id);

CREATE POLICY "Providers can update bookings" ON bookings
    FOR UPDATE
    USING (auth.uid() = firebase_uid)
    WITH CHECK (auth.uid() = firebase_uid);

CREATE POLICY "Anon can read bookings" ON bookings
    FOR SELECT
    USING (true);

-- Provider services policies
CREATE POLICY "Anyone can read services" ON provider_services
    FOR SELECT
    USING (true);

CREATE POLICY "Providers can view their services" ON provider_services
    FOR SELECT
    USING (auth.uid() = firebase_uid);

CREATE POLICY "Providers can create services" ON provider_services
    FOR INSERT
    WITH CHECK (auth.uid() = firebase_uid);

CREATE POLICY "Providers can update services" ON provider_services
    FOR UPDATE
    USING (auth.uid() = firebase_uid)
    WITH CHECK (auth.uid() = firebase_uid);

CREATE POLICY "Providers can delete services" ON provider_services
    FOR DELETE
    USING (auth.uid() = firebase_uid);

CREATE POLICY "Anon can read services" ON provider_services
    FOR SELECT
    USING (true);

-- ============================================================================
-- VERIFICATION
-- ============================================================================
-- Run this to verify columns are correct:
-- SELECT column_name, data_type FROM information_schema.columns
-- WHERE table_name = 'bookings' AND column_name LIKE '%uid%';
--
-- Should show: firebase_uid | uuid

