-- =====================================================================================
-- SUPABASE MIGRATION: Business Media Gallery for Provider Profiles
-- =====================================================================================
-- Run this in your Supabase SQL Editor to add multi-media showcase functionality
-- to provider business profiles.
--
-- This creates a new `business_media` table to store multiple images/videos that
-- providers can upload to showcase their work.
-- =====================================================================================

-- Create business_media table
CREATE TABLE IF NOT EXISTS public.business_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id TEXT NOT NULL,  -- Firebase UID of the provider
    media_url TEXT NOT NULL,     -- Supabase Storage public URL
    media_type TEXT DEFAULT 'image' CHECK (media_type IN ('image', 'video')),
    thumbnail_url TEXT,          -- Optional: thumbnail for videos
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create index on provider_id for faster queries
CREATE INDEX IF NOT EXISTS idx_business_media_provider
    ON public.business_media(provider_id);

-- Create index on display_order for sorting
CREATE INDEX IF NOT EXISTS idx_business_media_order
    ON public.business_media(provider_id, display_order);

-- Enable Row Level Security
ALTER TABLE public.business_media ENABLE ROW LEVEL SECURITY;

-- Policy: Anyone can view business media
CREATE POLICY "business_media_select_public"
    ON public.business_media
    FOR SELECT
    USING (true);

-- Policy: Providers can insert their own media
CREATE POLICY "business_media_insert_own"
    ON public.business_media
    FOR INSERT
    WITH CHECK (true);  -- Change to: auth.uid() = provider_id if using Supabase Auth

-- Policy: Providers can update their own media
CREATE POLICY "business_media_update_own"
    ON public.business_media
    FOR UPDATE
    USING (true)  -- Change to: auth.uid() = provider_id if using Supabase Auth
    WITH CHECK (true);

-- Policy: Providers can delete their own media
CREATE POLICY "business_media_delete_own"
    ON public.business_media
    FOR DELETE
    USING (true);  -- Change to: auth.uid() = provider_id if using Supabase Auth

-- Auto-update timestamp trigger
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

-- =====================================================================================
-- STORAGE BUCKET SETUP
-- =====================================================================================
-- Make sure the 'provider-business-images' bucket exists in Supabase Storage.
-- If not, create it via Supabase Dashboard > Storage > Create Bucket
--
-- Bucket name: provider-business-images
-- Public: Yes (so media URLs are publicly accessible)
--
-- You can also create it via SQL:
-- INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
-- VALUES ('provider-business-images', 'provider-business-images', true, 52428800,
--         '{"image/*", "video/*"}')
-- ON CONFLICT (id) DO NOTHING;
-- =====================================================================================

COMMENT ON TABLE public.business_media IS 'Stores showcase images and videos for provider business profiles';
COMMENT ON COLUMN public.business_media.provider_id IS 'Firebase UID of the provider who owns this media';
COMMENT ON COLUMN public.business_media.media_url IS 'Public URL from Supabase Storage';
COMMENT ON COLUMN public.business_media.media_type IS 'Type of media: image or video';
COMMENT ON COLUMN public.business_media.thumbnail_url IS 'Optional thumbnail URL for videos';
COMMENT ON COLUMN public.business_media.display_order IS 'Order in which media appears in gallery (lower = first)';

