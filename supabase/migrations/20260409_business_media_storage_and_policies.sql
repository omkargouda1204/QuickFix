-- Fix provider dashboard showcase uploads stuck as pending.
-- App uses anon key and uploads business media to storage bucket: Quickfix
-- Then inserts row into public.business_media.

-- Ensure Quickfix bucket exists and is public (media preview uses public URLs).
insert into storage.buckets (id, name, public)
values ('Quickfix', 'Quickfix', true)
on conflict (id) do update set public = excluded.public;

-- Storage access for anon/authenticated roles used by app.
grant usage on schema storage to anon, authenticated;
grant select, insert, update on storage.objects to anon, authenticated;

-- Storage RLS policies for Quickfix bucket.
drop policy if exists quickfix_media_select on storage.objects;
create policy quickfix_media_select
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'Quickfix');

drop policy if exists quickfix_media_insert on storage.objects;
create policy quickfix_media_insert
on storage.objects
for insert
to anon, authenticated
with check (bucket_id = 'Quickfix');

drop policy if exists quickfix_media_update on storage.objects;
create policy quickfix_media_update
on storage.objects
for update
to anon, authenticated
using (bucket_id = 'Quickfix')
with check (bucket_id = 'Quickfix');

-- business_media table API access (needed after upload to persist media row).
grant usage on schema public to anon, authenticated;
grant select, insert, delete on table public.business_media to anon, authenticated;

alter table if exists public.business_media enable row level security;

drop policy if exists business_media_select_all on public.business_media;
create policy business_media_select_all
on public.business_media
for select
to anon, authenticated
using (true);

drop policy if exists business_media_insert_all on public.business_media;
create policy business_media_insert_all
on public.business_media
for insert
to anon, authenticated
with check (true);

drop policy if exists business_media_delete_all on public.business_media;
create policy business_media_delete_all
on public.business_media
for delete
to anon, authenticated
using (true);

