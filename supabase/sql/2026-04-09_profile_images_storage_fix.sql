-- Profile photo upload/save fix for QuickFix
--
-- NOTE:
-- The current Android app sends Supabase requests using the anon key.
-- Policies below allow anon/authenticated roles for profile images so uploads do not fail
-- with "storage permission denied". Tighten these when the app migrates to Supabase Auth JWT.

begin;

-- 1) Ensure profile image bucket exists and is publicly readable.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-images',
  'profile-images',
  true,
  10485760,
  array['image/jpeg','image/png','image/webp','image/heic','image/heif']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- 2) Storage RLS policies for uploads/reads.
drop policy if exists "profile_images_select" on storage.objects;
create policy "profile_images_select"
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'profile-images');

drop policy if exists "profile_images_insert" on storage.objects;
create policy "profile_images_insert"
on storage.objects
for insert
to anon, authenticated
with check (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

drop policy if exists "profile_images_update" on storage.objects;
create policy "profile_images_update"
on storage.objects
for update
to anon, authenticated
using (bucket_id = 'profile-images')
with check (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

drop policy if exists "profile_images_delete" on storage.objects;
create policy "profile_images_delete"
on storage.objects
for delete
to anon, authenticated
using (bucket_id = 'profile-images');

-- 3) Ensure users.profile_image can be written by app role.
--    Keep this restricted to the profile_image field from app code.
drop policy if exists "users_profile_image_update" on public.users;
create policy "users_profile_image_update"
on public.users
for update
to anon, authenticated
using (true)
with check (true);

commit;

