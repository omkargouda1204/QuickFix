-- Supabase Storage policy setup for profile images
--
-- IMPORTANT:
-- QuickFix currently uploads profile images with the Supabase anon key.
-- These policies therefore allow both anon and authenticated client roles so
-- the existing app keeps working. If/when the app migrates to Supabase Auth,
-- you can narrow the policies to authenticated only.

begin;

-- Create the bucket if it does not already exist.
insert into storage.buckets (id, name, public)
values ('profile-images', 'profile-images', true)
on conflict (id) do update
set name = excluded.name,
    public = true;

-- Ensure RLS is enabled on storage.objects.
alter table storage.objects enable row level security;

-- Remove old versions of these policies before re-creating them.
drop policy if exists "profile-images insert" on storage.objects;
drop policy if exists "profile-images select" on storage.objects;
drop policy if exists "profile-images update" on storage.objects;
drop policy if exists "profile-images delete" on storage.objects;

-- Allow image uploads to profile-images/profiles/...
create policy "profile-images insert"
on storage.objects
for insert
to anon, authenticated
with check (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

-- Allow reading object rows for the same bucket/path.
-- Public URLs will still work because the bucket is public.
create policy "profile-images select"
on storage.objects
for select
to anon, authenticated
using (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

-- Allow replacing an existing avatar object if the app reuses paths later.
create policy "profile-images update"
on storage.objects
for update
to anon, authenticated
using (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
)
with check (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

-- Allow cleanup/delete flows for account deletion.
create policy "profile-images delete"
on storage.objects
for delete
to anon, authenticated
using (
  bucket_id = 'profile-images'
  and name like 'profiles/%'
);

commit;

