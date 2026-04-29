-- Emergency feature fixes (QuickFix)
-- Run this in Supabase SQL editor.

create extension if not exists pgcrypto;

alter table if exists public.emergency_requests
    add column if not exists user_firebase_uid text,
    add column if not exists assigned_provider_name text,
    add column if not exists assigned_provider_phone text,
    add column if not exists assigned_provider_service text,
    add column if not exists assigned_provider_city text,
    add column if not exists assigned_provider_area text;

create index if not exists idx_provider_profiles_active_approved
    on public.provider_profiles (status, is_approved);

create index if not exists idx_provider_profiles_firebase_uid
    on public.provider_profiles (firebase_uid);

create index if not exists idx_emergency_requests_status
    on public.emergency_requests (status);

create index if not exists idx_emergency_requests_user_firebase_uid
    on public.emergency_requests (user_firebase_uid);

create index if not exists idx_bookings_emergency_request_id
    on public.bookings (emergency_request_id);

create index if not exists idx_bookings_provider_id
    on public.bookings (provider_id);

create or replace function public.find_emergency_nearby_providers(
    p_category text,
    p_lat double precision,
    p_lng double precision,
    p_radius_km double precision default 25
)
returns table (
    provider_id text,
    firebase_uid text,
    category text,
    city text,
    area text,
    distance_km double precision
)
language sql
stable
as $$
    with providers as (
        select
            pp.id::text as provider_id,
            pp.firebase_uid,
            pp.category,
            pp.city,
            pp.area,
            coalesce(pp.latitude, pp.lat) as p_lat,
            coalesce(pp.longitude, pp.lng) as p_lng
        from public.provider_profiles pp
        where coalesce(pp.status, '') = 'active'
          and coalesce(pp.is_approved, false) = true
          and pp.firebase_uid is not null
          and coalesce(pp.latitude, pp.lat) is not null
          and coalesce(pp.longitude, pp.lng) is not null
    ),
    scored as (
        select
            p.provider_id,
            p.firebase_uid,
            p.category,
            p.city,
            p.area,
            (
                6371.0 * acos(
                    least(
                        1.0,
                        greatest(
                            -1.0,
                            cos(radians(p_lat)) * cos(radians(p.p_lat))
                            * cos(radians(p.p_lng) - radians(p_lng))
                            + sin(radians(p_lat)) * sin(radians(p.p_lat))
                        )
                    )
                )
            ) as distance_km
        from providers p
        where p_lat is not null
          and p_lng is not null
    )
    select
        s.provider_id,
        s.firebase_uid,
        s.category,
        s.city,
        s.area,
        s.distance_km
    from scored s
    where (
            p_category is null
            or btrim(p_category) = ''
            or lower(coalesce(s.category, '')) = lower(btrim(p_category))
            or lower(coalesce(s.category, '')) like '%' || lower(btrim(p_category)) || '%'
            or lower(btrim(p_category)) like '%' || lower(coalesce(s.category, '')) || '%'
        )
      and s.distance_km <= coalesce(p_radius_km, 25)
    order by s.distance_km asc;
$$;

create or replace function public.cancel_emergency_request_atomic(
    p_request_id text
)
returns table (
    cancelled boolean,
    message text
)
language plpgsql
security definer
as $$
declare
    v_rows integer;
begin
    update public.emergency_requests er
    set status = 'cancelled'
    where er.id::text = p_request_id
      and coalesce(er.status, 'pending') = 'pending';

    get diagnostics v_rows = row_count;

    if v_rows = 0 then
        return query select false, 'already_assigned_or_closed'::text;
        return;
    end if;

    update public.bookings b
    set status = 'cancelled'
    where b.emergency_request_id::text = p_request_id
      and coalesce(b.status, 'pending') = 'pending';

    return query select true, 'cancelled'::text;
end;
$$;

create or replace function public.accept_emergency_request_atomic(
    p_request_id text,
    p_provider_id text,
    p_provider_firebase_uid text,
    p_provider_name text,
    p_provider_phone text,
    p_provider_service text,
    p_provider_city text,
    p_provider_area text
)
returns table (
    accepted boolean,
    message text
)
language plpgsql
security definer
as $$
declare
    v_assigned_id text;
    v_rows integer;
begin
    v_assigned_id := coalesce(nullif(p_provider_id, ''), nullif(p_provider_firebase_uid, ''));

    update public.emergency_requests er
    set
        status = 'accepted',
        assigned_provider_id = v_assigned_id,
        accepted_at = now(),
        assigned_provider_name = coalesce(nullif(p_provider_name, ''), er.assigned_provider_name),
        assigned_provider_phone = coalesce(nullif(p_provider_phone, ''), er.assigned_provider_phone),
        assigned_provider_service = coalesce(nullif(p_provider_service, ''), er.assigned_provider_service),
        assigned_provider_city = coalesce(nullif(p_provider_city, ''), er.assigned_provider_city),
        assigned_provider_area = coalesce(nullif(p_provider_area, ''), er.assigned_provider_area)
    where er.id::text = p_request_id
      and coalesce(er.status, 'pending') = 'pending';

    get diagnostics v_rows = row_count;

    if v_rows = 0 then
        return query select false, 'already_assigned_or_closed'::text;
        return;
    end if;

    update public.bookings b
    set
        status = 'accepted',
        provider_id = v_assigned_id,
        provider_name = coalesce(nullif(p_provider_name, ''), b.provider_name),
        provider_phone = coalesce(nullif(p_provider_phone, ''), b.provider_phone)
    where b.emergency_request_id::text = p_request_id
      and coalesce(b.status, 'pending') = 'pending'
      and (
            b.provider_id::text = coalesce(p_provider_id, '')
            or b.provider_id::text = coalesce(p_provider_firebase_uid, '')
          );

    update public.bookings b
    set status = 'rejected'
    where b.emergency_request_id::text = p_request_id
      and coalesce(b.status, 'pending') = 'pending'
      and not (
            b.provider_id::text = coalesce(p_provider_id, '')
            or b.provider_id::text = coalesce(p_provider_firebase_uid, '')
          );

    return query select true, 'accepted'::text;
end;
$$;

grant execute on function public.find_emergency_nearby_providers(text, double precision, double precision, double precision)
to anon, authenticated, service_role;

grant execute on function public.accept_emergency_request_atomic(text, text, text, text, text, text, text, text)
to anon, authenticated, service_role;

grant execute on function public.cancel_emergency_request_atomic(text)
to anon, authenticated, service_role;
