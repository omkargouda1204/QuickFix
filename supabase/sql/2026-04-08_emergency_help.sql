-- Emergency Help backend setup for QuickFix
--
-- This script creates emergency request storage, first-accept-wins safety,
-- and automatic mirror rows in bookings.
--
-- NOTE:
-- The current Android app calls PostgREST with the anon key, not Supabase Auth JWTs.
-- Policies below therefore allow anon/authenticated client roles.
-- Tighten them once auth.jwt() identity is available server-side.

begin;

create extension if not exists pgcrypto;

create table if not exists public.emergency_requests (
  id uuid primary key default gen_random_uuid(),
  user_id text not null,
  user_name text,
  phone text,
  category text not null,
  description text,
  address text,
  latitude double precision,
  longitude double precision,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'completed', 'cancelled', 'expired', 'rejected')),
  assigned_provider_id uuid,
  accepted_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_emergency_requests_status_created
  on public.emergency_requests (status, created_at desc);

create index if not exists idx_emergency_requests_user_id
  on public.emergency_requests (user_id);

create index if not exists idx_emergency_requests_category
  on public.emergency_requests (category);

create or replace function public.touch_emergency_requests_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_touch_emergency_requests_updated_at on public.emergency_requests;
create trigger trg_touch_emergency_requests_updated_at
before update on public.emergency_requests
for each row execute function public.touch_emergency_requests_updated_at();

alter table public.emergency_requests enable row level security;

drop policy if exists "emergency_requests insert" on public.emergency_requests;
create policy "emergency_requests insert"
on public.emergency_requests
for insert
to anon, authenticated
with check (status = 'pending');

drop policy if exists "emergency_requests select" on public.emergency_requests;
create policy "emergency_requests select"
on public.emergency_requests
for select
to anon, authenticated
using (true);

drop policy if exists "emergency_requests update" on public.emergency_requests;
create policy "emergency_requests update"
on public.emergency_requests
for update
to anon, authenticated
using (status in ('pending', 'accepted', 'completed', 'cancelled', 'expired', 'rejected'))
with check (status in ('pending', 'accepted', 'completed', 'cancelled', 'expired', 'rejected'));

-- Optional RPC for strict DB-side first-accept-wins.
create or replace function public.rpc_accept_emergency_request(
  p_request_id uuid,
  p_provider_id uuid
)
returns table (
  id uuid,
  status text,
  assigned_provider_id uuid,
  accepted_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
begin
  return query
  update public.emergency_requests er
     set status = 'accepted',
         assigned_provider_id = p_provider_id,
         accepted_at = now()
   where er.id = p_request_id
     and er.status = 'pending'
  returning er.id, er.status, er.assigned_provider_id, er.accepted_at;
end;
$$;

grant execute on function public.rpc_accept_emergency_request(uuid, uuid) to anon, authenticated;

-- Mirror accepted emergency rows into bookings automatically.
create or replace function public.mirror_emergency_to_bookings()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'accepted' and old.status is distinct from new.status then
    insert into public.bookings (
      customer_id,
      provider_id,
      customer_name,
      customer_phone,
      provider_name,
      service_name,
      category,
      address,
      description,
      status,
      is_emergency
    )
    select
      new.user_id,
      coalesce(new.assigned_provider_id::text, ''),
      coalesce(new.user_name, 'Customer'),
      coalesce(new.phone, ''),
      coalesce(pp.business_name, 'Provider'),
      coalesce(new.category, 'Emergency'),
      coalesce(new.category, 'Emergency'),
      coalesce(new.address, ''),
      coalesce(new.description, ''),
      'accepted',
      true
    from public.provider_profiles pp
    where pp.id = new.assigned_provider_id
    on conflict do nothing;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_mirror_emergency_to_bookings on public.emergency_requests;
create trigger trg_mirror_emergency_to_bookings
after update on public.emergency_requests
for each row execute function public.mirror_emergency_to_bookings();

commit;

