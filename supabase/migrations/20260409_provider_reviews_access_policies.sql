-- Fix 401/403 when app writes provider_reviews through PostgREST (anon key).
-- This migration adds grants, RLS policies, and hardens trigger functions.

-- API role access
grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on table public.provider_reviews to anon, authenticated;

-- RLS policies for app reads/writes
alter table public.provider_reviews enable row level security;

drop policy if exists provider_reviews_select_all on public.provider_reviews;
create policy provider_reviews_select_all
on public.provider_reviews
for select
to anon, authenticated
using (true);

drop policy if exists provider_reviews_insert_all on public.provider_reviews;
create policy provider_reviews_insert_all
on public.provider_reviews
for insert
to anon, authenticated
with check (
    rating between 1 and 5
    and char_length(comment) <= 500
);

drop policy if exists provider_reviews_update_all on public.provider_reviews;
create policy provider_reviews_update_all
on public.provider_reviews
for update
to anon, authenticated
using (true)
with check (
    rating between 1 and 5
    and char_length(comment) <= 500
);

drop policy if exists provider_reviews_delete_all on public.provider_reviews;
create policy provider_reviews_delete_all
on public.provider_reviews
for delete
to anon, authenticated
using (true);

-- Triggered profile rating sync should not depend on anon update rights.
create or replace function public.refresh_provider_profile_rating(p_provider_id text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.provider_profiles pp
    set rating = coalesce(stats.avg_rating, 0),
        review_count = coalesce(stats.review_count, 0)
    from (
        select
            pr.provider_id,
            round(avg(pr.rating)::numeric, 1) as avg_rating,
            count(*)::integer as review_count
        from public.provider_reviews pr
        where pr.provider_id = p_provider_id
        group by pr.provider_id
    ) stats
    where pp.id::text = p_provider_id;

    update public.provider_profiles pp
    set rating = 0,
        review_count = 0
    where pp.id::text = p_provider_id
      and not exists (
        select 1 from public.provider_reviews pr where pr.provider_id = p_provider_id
      );
end;
$$;

create or replace function public.sync_provider_profile_rating_on_review_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'DELETE' then
        perform public.refresh_provider_profile_rating(old.provider_id);
        return old;
    end if;

    perform public.refresh_provider_profile_rating(new.provider_id);

    if tg_op = 'UPDATE' and old.provider_id is distinct from new.provider_id then
        perform public.refresh_provider_profile_rating(old.provider_id);
    end if;

    return new;
end;
$$;
