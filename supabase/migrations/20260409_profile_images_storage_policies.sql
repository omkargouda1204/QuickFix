-- Fix profile image upload permission errors for anon-key client uploads.
-- App uploads to bucket: profile-images with path: profiles/{uid}/{uid}_{timestamp}.jpg

-- Ensure bucket exists and is public for direct display URLs.
insert into storage.buckets (id, name, public)
values ('profile-images', 'profile-images', true)
on conflict (id) do update set public = excluded.public;

-- Make sure client roles can use storage schema/tables.
grant usage on schema storage to anon, authenticated;
grant select, insert, update on storage.objects to anon, authenticated;

-- Read policy (required to display profile images).
drop policy if exists profile_images_select on storage.objects;
create policy profile_images_select
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'profile-images');

-- Insert policy (required for new uploads from app).
drop policy if exists profile_images_insert on storage.objects;
create policy profile_images_insert
on storage.objects
for insert
to anon, authenticated
with check (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = 'profiles'
);

-- Update policy (required because app sends x-upsert=true).
drop policy if exists profile_images_update on storage.objects;
create policy profile_images_update
on storage.objects
for update
to anon, authenticated
using (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = 'profiles'
)
with check (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = 'profiles'
);

