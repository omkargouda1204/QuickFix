-- Account Deletion RLS Policies for QuickFix
--
-- This script ensures proper RLS policies are in place to allow authenticated users
-- and the app's anon key to delete their own data during account deletion.
--
-- NOTE:
-- The current Android app uses the anon key for all PostgREST calls, not Supabase Auth JWTs.
-- These policies allow both anon and authenticated roles to delete records they own.
-- Once auth.jwt() identity is available, tighten the anon policies.

begin;

-- ════════════════════════════════════════════════════════════════════════════
-- USERS TABLE — Allow deletion by firebase_uid match or id match
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "users_delete_by_firebase_uid" on public.users;
create policy "users_delete_by_firebase_uid"
on public.users
for delete
to anon, authenticated
using (true);  -- NOTE: Currently allows any deletion via filter query (firebase_uid=eq.XXX)
               -- In production with JWT, restrict to auth.uid() or auth.jwt()->>'sub'

-- ════════════════════════════════════════════════════════════════════════════
-- PROVIDER_PROFILES TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "provider_profiles_delete" on public.provider_profiles;
create policy "provider_profiles_delete"
on public.provider_profiles
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- USER_FCM_TOKENS TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "user_fcm_tokens_delete" on public.user_fcm_tokens;
create policy "user_fcm_tokens_delete"
on public.user_fcm_tokens
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching user in production

-- ════════════════════════════════════════════════════════════════════════════
-- SERVICES TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "services_delete" on public.services;
create policy "services_delete"
on public.services
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- PROVIDER_APPLICATIONS TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "provider_applications_delete" on public.provider_applications;
create policy "provider_applications_delete"
on public.provider_applications
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- EMERGENCY_REQUESTS TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "emergency_requests_delete" on public.emergency_requests;
create policy "emergency_requests_delete"
on public.emergency_requests
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching user/provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- NOTIFICATIONS TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "notifications_delete" on public.notifications;
create policy "notifications_delete"
on public.notifications
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching receiver in production

-- ════════════════════════════════════════════════════════════════════════════
-- BOOKINGS TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "bookings_delete" on public.bookings;
create policy "bookings_delete"
on public.bookings
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching user/provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- BUSINESS_MEDIA TABLE — Allow deletion
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "business_media_delete" on public.business_media;
create policy "business_media_delete"
on public.business_media
for delete
to anon, authenticated
using (true);  -- NOTE: Restrict to matching provider in production

-- ════════════════════════════════════════════════════════════════════════════
-- STORAGE: PROFILE IMAGES — Ensure delete policy exists
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "profile_images_delete" on storage.objects;
create policy "profile_images_delete"
on storage.objects
for delete
to anon, authenticated
using (bucket_id = 'profile-images');

-- ════════════════════════════════════════════════════════════════════════════
-- STORAGE: PROVIDER BUSINESS IMAGES — Ensure delete policy exists
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "provider_business_images_delete" on storage.objects;
create policy "provider_business_images_delete"
on storage.objects
for delete
to anon, authenticated
using (bucket_id = 'provider-business-images');

-- ════════════════════════════════════════════════════════════════════════════
-- STORAGE: PROVIDER ID PROOFS — Ensure delete policy exists
-- ════════════════════════════════════════════════════════════════════════════
drop policy if exists "provider_id_proofs_delete" on storage.objects;
create policy "provider_id_proofs_delete"
on storage.objects
for delete
to anon, authenticated
using (bucket_id = 'provider-id-proofs');

commit;

