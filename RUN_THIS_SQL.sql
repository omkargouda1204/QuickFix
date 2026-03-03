-- ============================================================================
-- COPY THIS ENTIRE FILE AND RUN IN SUPABASE SQL EDITOR
-- ============================================================================
-- This creates the bookings and provider_services tables with firebase_uid
-- ============================================================================

-- Drop existing tables if they exist (to start fresh)
DROP TABLE IF EXISTS bookings CASCADE;
DROP TABLE IF EXISTS provider_services CASCADE;

-- ============================================================================
-- CREATE BOOKINGS TABLE
-- ============================================================================
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    customer_name TEXT NOT NULL,
    customer_phone TEXT,
    firebase_uid UUID NOT NULL,
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
    completed_at TIMESTAMP WITH TIME ZONE
);

-- ============================================================================
-- CREATE PROVIDER_SERVICES TABLE
-- ============================================================================
CREATE TABLE provider_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid UUID NOT NULL,
    provider_name TEXT,
    service_name TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- CREATE INDEXES
-- ============================================================================
CREATE INDEX idx_bookings_firebase_uid ON bookings(firebase_uid);
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_provider_services_firebase_uid ON provider_services(firebase_uid);
CREATE INDEX idx_provider_services_category ON provider_services(category);

-- ============================================================================
-- ENABLE ROW LEVEL SECURITY
-- ============================================================================
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE provider_services ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- CREATE RLS POLICIES
-- ============================================================================

-- Allow anyone to read bookings (needed for provider dashboard)
CREATE POLICY "allow_read_bookings" ON bookings FOR SELECT USING (true);

-- Allow anyone to read services (needed for browsing)
CREATE POLICY "allow_read_services" ON provider_services FOR SELECT USING (true);

-- Allow anyone to insert bookings (needed for customers)
CREATE POLICY "allow_insert_bookings" ON bookings FOR INSERT WITH CHECK (true);

-- Allow anyone to insert services (needed for providers)
CREATE POLICY "allow_insert_services" ON provider_services FOR INSERT WITH CHECK (true);

-- Allow anyone to update bookings (needed for providers to update status)
CREATE POLICY "allow_update_bookings" ON bookings FOR UPDATE USING (true);

-- Allow anyone to update services (needed for providers)
CREATE POLICY "allow_update_services" ON provider_services FOR UPDATE USING (true);

-- Allow anyone to delete services (needed for providers)
CREATE POLICY "allow_delete_services" ON provider_services FOR DELETE USING (true);

-- ============================================================================
-- DONE! Tables created successfully
-- ============================================================================

