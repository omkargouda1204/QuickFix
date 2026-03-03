-- ============================================================================
-- QuickFix Database Setup - Create Required Tables
-- ============================================================================
-- Run this SQL in Supabase SQL Editor to create the required tables
-- for the provider dashboard features
-- ============================================================================

-- ============================================================================
-- 1. CREATE BOOKINGS TABLE
-- ============================================================================
-- Stores all service booking requests from customers to providers
CREATE TABLE IF NOT EXISTS bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Customer Information
    customer_id UUID NOT NULL,
    customer_name TEXT NOT NULL,
    customer_phone TEXT,

    -- Provider Information
    provider_id UUID NOT NULL,
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
    -- Status values: pending, accepted, completed, cancelled

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- Foreign Key Constraints
    CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_provider FOREIGN KEY (provider_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_bookings_provider_id ON bookings(provider_id);
CREATE INDEX IF NOT EXISTS idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON bookings(created_at DESC);

-- ============================================================================
-- 2. CREATE PROVIDER_SERVICES TABLE
-- ============================================================================
-- Stores services offered by service providers
CREATE TABLE IF NOT EXISTS provider_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Provider Information
    provider_id UUID NOT NULL,
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
    CONSTRAINT fk_provider FOREIGN KEY (provider_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_provider_services_provider_id ON provider_services(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_services_category ON provider_services(category);
CREATE INDEX IF NOT EXISTS idx_provider_services_is_available ON provider_services(is_available);
CREATE INDEX IF NOT EXISTS idx_provider_services_created_at ON provider_services(created_at DESC);

-- ============================================================================
-- 3. ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS on bookings table
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;

-- Providers can see their own bookings (both as provider and customer)
CREATE POLICY "Providers can view their own bookings" ON bookings
    FOR SELECT
    USING (
        auth.uid() = provider_id OR auth.uid() = customer_id
    );

-- Customers can see their own bookings
CREATE POLICY "Customers can view their own bookings" ON bookings
    FOR SELECT
    USING (auth.uid() = customer_id);

-- Customers can create bookings
CREATE POLICY "Customers can create bookings" ON bookings
    FOR INSERT
    WITH CHECK (auth.uid() = customer_id);

-- Providers can update booking status
CREATE POLICY "Providers can update booking status" ON bookings
    FOR UPDATE
    USING (auth.uid() = provider_id)
    WITH CHECK (auth.uid() = provider_id);

-- Allow anon key to read bookings (for provider dashboard)
CREATE POLICY "Anon users can read bookings" ON bookings
    FOR SELECT
    USING (true);

-- Enable RLS on provider_services table
ALTER TABLE provider_services ENABLE ROW LEVEL SECURITY;

-- Anyone can read available services
CREATE POLICY "Anyone can read available services" ON provider_services
    FOR SELECT
    USING (true);

-- Providers can read all their services (available or not)
CREATE POLICY "Providers can view their services" ON provider_services
    FOR SELECT
    USING (auth.uid() = provider_id);

-- Providers can create services
CREATE POLICY "Providers can create services" ON provider_services
    FOR INSERT
    WITH CHECK (auth.uid() = provider_id);

-- Providers can update their services
CREATE POLICY "Providers can update their services" ON provider_services
    FOR UPDATE
    USING (auth.uid() = provider_id)
    WITH CHECK (auth.uid() = provider_id);

-- Providers can delete their services
CREATE POLICY "Providers can delete their services" ON provider_services
    FOR DELETE
    USING (auth.uid() = provider_id);

-- Allow anon key to read services (for browsing)
CREATE POLICY "Anon users can read services" ON provider_services
    FOR SELECT
    USING (true);

-- ============================================================================
-- 4. SAMPLE DATA (Optional - for testing)
-- ============================================================================
-- Uncomment to add test data
/*
INSERT INTO bookings (customer_id, customer_name, customer_phone, provider_id, provider_name,
                      service_name, category, booking_date, booking_time, address, city,
                      price, status, description)
VALUES (
    'customer-uuid-here',
    'John Doe',
    '+91-9876543210',
    'provider-uuid-here',
    'Jane Smith',
    'Plumbing Repair',
    'Plumbing',
    CURRENT_DATE,
    '10:00:00',
    '123 Main Street',
    'New York',
    500.00,
    'pending',
    'Need pipe repair'
);

INSERT INTO provider_services (provider_id, provider_name, service_name, category, description, price, is_available)
VALUES (
    'provider-uuid-here',
    'Jane Smith',
    'Plumbing Repair',
    'Plumbing',
    'Professional plumbing repair services',
    500.00,
    true
);
*/

-- ============================================================================
-- VERIFICATION COMMANDS
-- ============================================================================
-- Run these to verify the tables were created correctly:
-- SELECT * FROM bookings;
-- SELECT * FROM provider_services;
-- SELECT * FROM information_schema.tables WHERE table_schema='public';

