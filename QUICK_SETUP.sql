-- =====================================================
-- QUICK SETUP - Copy and paste this into Supabase SQL Editor
-- =====================================================

-- 1. Create the table
CREATE TABLE IF NOT EXISTS public.business_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id TEXT NOT NULL,
    media_url TEXT NOT NULL,
    media_type TEXT DEFAULT 'image' CHECK (media_type IN ('image', 'video')),
    thumbnail_url TEXT,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Add indexes
CREATE INDEX IF NOT EXISTS idx_business_media_provider ON public.business_media(provider_id);
CREATE INDEX IF NOT EXISTS idx_business_media_order ON public.business_media(provider_id, display_order);

-- 3. Enable RLS
ALTER TABLE public.business_media ENABLE ROW LEVEL SECURITY;

-- 4. Add RLS policies
CREATE POLICY "business_media_select_public" ON public.business_media FOR SELECT USING (true);
CREATE POLICY "business_media_insert_own" ON public.business_media FOR INSERT WITH CHECK (true);
CREATE POLICY "business_media_update_own" ON public.business_media FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "business_media_delete_own" ON public.business_media FOR DELETE USING (true);

-- 5. Add auto-update trigger
CREATE OR REPLACE FUNCTION update_business_media_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER business_media_updated_at
    BEFORE UPDATE ON public.business_media
    FOR EACH ROW
    EXECUTE FUNCTION update_business_media_timestamp();

-- 6. Verify storage bucket exists (optional - run if bucket doesn't exist)
-- INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
-- VALUES ('provider-business-images', 'provider-business-images', true, 52428800, '{"image/*", "video/*"}')
-- ON CONFLICT (id) DO NOTHING;

-- DONE! Test by running:
SELECT * FROM business_media LIMIT 1;

