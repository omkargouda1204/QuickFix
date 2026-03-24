-- ============================================================================
-- QuickFix Admin Banner & Offer Management System - Supabase SQL Setup
-- ============================================================================
-- Date: March 24, 2026
-- Purpose: Complete database schema with indexes, RLS policies, and functions
-- ============================================================================

-- ============================================================================
-- PART 1: CREATE TABLES
-- ============================================================================

-- 1. BANNERS TABLE
CREATE TABLE IF NOT EXISTS banners (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title           VARCHAR(255) NOT NULL,
  image_url       VARCHAR(500),
  redirect_type   VARCHAR(50),           -- 'service', 'category', 'external'
  redirect_id     VARCHAR(255),
  is_active       BOOLEAN DEFAULT true,
  priority        INT DEFAULT 1,
  start_date      DATE DEFAULT CURRENT_DATE,
  expiry_date     DATE,
  status          VARCHAR(20) DEFAULT 'active', -- 'active', 'inactive', 'expired'
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at      TIMESTAMP
);

-- 2. OFFERS TABLE
CREATE TABLE IF NOT EXISTS offers (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  title               VARCHAR(255) NOT NULL,
  description         TEXT,
  offer_code          VARCHAR(50) UNIQUE NOT NULL,
  discount_type       VARCHAR(20) NOT NULL,    -- 'percentage' or 'fixed'
  discount_value      DECIMAL(10, 2) NOT NULL,
  min_booking_amount  DECIMAL(10, 2) DEFAULT 0,
  is_active           BOOLEAN DEFAULT true,
  expiry_date         DATE,
  status              VARCHAR(20) DEFAULT 'active',
  created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at          TIMESTAMP
);

-- ============================================================================
-- PART 2: CREATE INDEXES (For Performance)
-- ============================================================================

-- BANNERS INDEXES
CREATE INDEX idx_banners_is_active ON banners(is_active);
CREATE INDEX idx_banners_created_at ON banners(created_at DESC);
CREATE INDEX idx_banners_expiry_date ON banners(expiry_date);
CREATE INDEX idx_banners_priority ON banners(priority DESC);
CREATE INDEX idx_banners_status ON banners(status);
CREATE INDEX idx_banners_deleted_at ON banners(deleted_at);

-- OFFERS INDEXES
CREATE INDEX idx_offers_is_active ON offers(is_active);
CREATE INDEX idx_offers_offer_code ON offers(offer_code);
CREATE INDEX idx_offers_created_at ON offers(created_at DESC);
CREATE INDEX idx_offers_expiry_date ON offers(expiry_date);
CREATE INDEX idx_offers_status ON offers(status);
CREATE INDEX idx_offers_deleted_at ON offers(deleted_at);

-- ============================================================================
-- PART 3: ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS on both tables
ALTER TABLE banners ENABLE ROW LEVEL SECURITY;
ALTER TABLE offers ENABLE ROW LEVEL SECURITY;

-- BANNERS RLS POLICIES

-- 1. Admin can view all banners (including inactive)
CREATE POLICY "Admin view all banners"
ON banners FOR SELECT
TO authenticated
USING (
  auth.jwt() ->> 'role' = 'admin'
  OR EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 2. Public can view only active, non-expired banners
CREATE POLICY "Public view active banners"
ON banners FOR SELECT
TO anon
USING (
  is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
);

-- 3. Authenticated users can view active banners
CREATE POLICY "Users view active banners"
ON banners FOR SELECT
TO authenticated
USING (
  is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
);

-- 4. Only admins can insert banners
CREATE POLICY "Admin insert banners"
ON banners FOR INSERT
TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 5. Only admins can update banners
CREATE POLICY "Admin update banners"
ON banners FOR UPDATE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 6. Only admins can delete banners
CREATE POLICY "Admin delete banners"
ON banners FOR DELETE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- OFFERS RLS POLICIES

-- 1. Admin can view all offers
CREATE POLICY "Admin view all offers"
ON offers FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 2. Public can view only active, non-expired offers
CREATE POLICY "Public view active offers"
ON offers FOR SELECT
TO anon
USING (
  is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
);

-- 3. Authenticated users can view active offers
CREATE POLICY "Users view active offers"
ON offers FOR SELECT
TO authenticated
USING (
  is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
);

-- 4. Only admins can insert offers
CREATE POLICY "Admin insert offers"
ON offers FOR INSERT
TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 5. Only admins can update offers
CREATE POLICY "Admin update offers"
ON offers FOR UPDATE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- 6. Only admins can delete offers
CREATE POLICY "Admin delete offers"
ON offers FOR DELETE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM users
    WHERE users.id = auth.uid()
    AND users.role = 'admin'
  )
);

-- ============================================================================
-- PART 4: TRIGGERS FOR AUTOMATIC UPDATES
-- ============================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for banners updated_at
CREATE TRIGGER update_banners_updated_at
BEFORE UPDATE ON banners
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Trigger for offers updated_at
CREATE TRIGGER update_offers_updated_at
BEFORE UPDATE ON offers
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- PART 5: VIEWS (Optional - For Complex Queries)
-- ============================================================================

-- View for active banners (commonly used)
CREATE OR REPLACE VIEW active_banners AS
SELECT * FROM banners
WHERE is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
ORDER BY priority DESC, created_at DESC;

-- View for active offers (commonly used)
CREATE OR REPLACE VIEW active_offers AS
SELECT * FROM offers
WHERE is_active = true
  AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
  AND deleted_at IS NULL
ORDER BY created_at DESC;

-- View for expired items (for cleanup)
CREATE OR REPLACE VIEW expired_items AS
SELECT
  'banner' as type,
  id,
  title as name,
  expiry_date,
  created_at
FROM banners
WHERE expiry_date IS NOT NULL
  AND expiry_date < CURRENT_DATE
  AND deleted_at IS NULL

UNION ALL

SELECT
  'offer' as type,
  id,
  title as name,
  expiry_date,
  created_at
FROM offers
WHERE expiry_date IS NOT NULL
  AND expiry_date < CURRENT_DATE
  AND deleted_at IS NULL;

-- ============================================================================
-- PART 6: STORED PROCEDURES / FUNCTIONS
-- ============================================================================

-- Function to get banner statistics
CREATE OR REPLACE FUNCTION get_banner_stats()
RETURNS TABLE(
  total_banners BIGINT,
  active_banners BIGINT,
  expired_banners BIGINT,
  upcoming_banners BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    COUNT(*)::BIGINT as total,
    COALESCE(SUM(CASE WHEN is_active = true THEN 1 ELSE 0 END), 0)::BIGINT as active,
    COALESCE(SUM(CASE WHEN expiry_date < CURRENT_DATE THEN 1 ELSE 0 END), 0)::BIGINT as expired,
    COALESCE(SUM(CASE WHEN start_date > CURRENT_DATE THEN 1 ELSE 0 END), 0)::BIGINT as upcoming
  FROM banners
  WHERE deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

-- Function to get offer statistics
CREATE OR REPLACE FUNCTION get_offer_stats()
RETURNS TABLE(
  total_offers BIGINT,
  active_offers BIGINT,
  expired_offers BIGINT,
  total_discount_value NUMERIC
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    COUNT(*)::BIGINT as total,
    COALESCE(SUM(CASE WHEN is_active = true THEN 1 ELSE 0 END), 0)::BIGINT as active,
    COALESCE(SUM(CASE WHEN expiry_date < CURRENT_DATE THEN 1 ELSE 0 END), 0)::BIGINT as expired,
    COALESCE(SUM(discount_value), 0)::NUMERIC as total_discount
  FROM offers
  WHERE deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

-- Function to auto-disable expired items
CREATE OR REPLACE FUNCTION auto_disable_expired_items()
RETURNS void AS $$
BEGIN
  -- Disable expired banners
  UPDATE banners
  SET is_active = false, status = 'expired'
  WHERE expiry_date IS NOT NULL
    AND expiry_date < CURRENT_DATE
    AND is_active = true
    AND deleted_at IS NULL;

  -- Disable expired offers
  UPDATE offers
  SET is_active = false, status = 'expired'
  WHERE expiry_date IS NOT NULL
    AND expiry_date < CURRENT_DATE
    AND is_active = true
    AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- PART 7: SAMPLE DATA (For Testing)
-- ============================================================================

-- Insert sample banners
INSERT INTO banners (title, image_url, redirect_type, redirect_id, is_active, priority, start_date, expiry_date, status)
VALUES
  ('Summer Sale 2026', 'banners/summer_sale.jpg', 'category', 'plumbing', true, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 'active'),
  ('Spring Promo', 'banners/spring_promo.jpg', 'service', 'home-cleaning', true, 2, CURRENT_DATE, CURRENT_DATE + INTERVAL '15 days', 'active'),
  ('AC Installation Offer', 'banners/ac_offer.jpg', 'category', 'ac', false, 3, CURRENT_DATE, CURRENT_DATE + INTERVAL '45 days', 'inactive');

-- Insert sample offers
INSERT INTO offers (title, description, offer_code, discount_type, discount_value, min_booking_amount, is_active, expiry_date, status)
VALUES
  ('Summer Discount', 'Get 20% off on all summer services', 'SUMMER20', 'percentage', 20.00, 500.00, true, CURRENT_DATE + INTERVAL '30 days', 'active'),
  ('Spring Special', 'Save ₹100 on your booking', 'SPRING100', 'fixed', 100.00, 300.00, true, CURRENT_DATE + INTERVAL '15 days', 'active'),
  ('First Time User', 'New users get 15% discount', 'WELCOME15', 'percentage', 15.00, 0.00, true, CURRENT_DATE + INTERVAL '90 days', 'active');

-- ============================================================================
-- PART 8: MAINTENANCE QUERIES
-- ============================================================================

-- Check table sizes
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename IN ('banners', 'offers')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check for orphaned records (deleted_at IS NOT NULL)
SELECT 'banners' as table_name, COUNT(*) as deleted_count FROM banners WHERE deleted_at IS NOT NULL
UNION
SELECT 'offers' as table_name, COUNT(*) as deleted_count FROM offers WHERE deleted_at IS NOT NULL;

-- Find duplicates by offer_code
SELECT offer_code, COUNT(*) as count FROM offers GROUP BY offer_code HAVING COUNT(*) > 1;

-- ============================================================================
-- PART 9: STORAGE BUCKET CONFIGURATION
-- ============================================================================

/*
Supabase Storage Setup:

1. Create bucket:
   - Name: banner-images
   - Private: Yes
   - File size limit: 5MB

2. Upload policy configuration (RLS):
   - Admins can upload images
   - Admins can delete images

3. File naming convention:
   - /banners/{banner-id}/{timestamp}-{filename}
   - Example: /banners/550e8400-e29b-41d4-a716-446655440000/1711270800000-summer_sale.jpg

4. Public URL format:
   - https://{project}.supabase.co/storage/v1/object/public/banner-images/{path}
*/

-- ============================================================================
-- PART 10: USEFUL QUERIES FOR ADMIN
-- ============================================================================

-- Get all banners with expiry status
SELECT
  id,
  title,
  is_active,
  priority,
  CASE
    WHEN expiry_date IS NULL THEN 'No expiry'
    WHEN expiry_date < CURRENT_DATE THEN 'EXPIRED'
    WHEN expiry_date <= CURRENT_DATE + INTERVAL '7 days' THEN 'Expiring soon'
    ELSE 'Active'
  END as status
FROM banners
WHERE deleted_at IS NULL
ORDER BY created_at DESC;

-- Get all offers with discount details
SELECT
  id,
  title,
  offer_code,
  discount_type,
  discount_value,
  CASE discount_type
    WHEN 'percentage' THEN CONCAT(discount_value, '% off')
    WHEN 'fixed' THEN CONCAT('₹', discount_value, ' off')
  END as discount_display,
  min_booking_amount,
  is_active,
  expiry_date
FROM offers
WHERE deleted_at IS NULL
ORDER BY created_at DESC;

-- Get statistics
SELECT
  'Banners' as item,
  (SELECT COUNT(*) FROM banners WHERE deleted_at IS NULL) as total,
  (SELECT COUNT(*) FROM banners WHERE is_active = true AND deleted_at IS NULL) as active,
  (SELECT COUNT(*) FROM banners WHERE expiry_date < CURRENT_DATE AND deleted_at IS NULL) as expired

UNION ALL

SELECT
  'Offers' as item,
  (SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL) as total,
  (SELECT COUNT(*) FROM offers WHERE is_active = true AND deleted_at IS NULL) as active,
  (SELECT COUNT(*) FROM offers WHERE expiry_date < CURRENT_DATE AND deleted_at IS NULL) as expired;

-- ============================================================================
-- EXECUTION NOTES:
-- ============================================================================

/*
BEFORE RUNNING THIS SCRIPT:

1. Connect to your Supabase project SQL editor
2. Replace placeholders if any
3. Run script in sections (Parts 1-6 are essential, Parts 7-10 are optional)
4. Verify tables created: SELECT * FROM information_schema.tables WHERE table_schema='public'
5. Test RLS policies with test user accounts
6. Configure storage bucket manually via Supabase dashboard

AFTER RUNNING:

1. Create Storage bucket "banner-images" with max 5MB size
2. Set up RLS policies for storage bucket
3. Test CREATE/READ/UPDATE/DELETE operations
4. Monitor database logs for errors
5. Schedule auto_disable_expired_items() daily or weekly

BACKUP IMPORTANT DATA REGULARLY!
*/

