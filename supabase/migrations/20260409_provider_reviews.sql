-- Provider review system for completed bookings.
create extension if not exists pgcrypto;

alter table if exists public.provider_profiles
    add column if not exists rating numeric(2,1) not null default 0,
    add column if not exists review_count integer not null default 0;

create table if not exists public.provider_reviews (
    id uuid primary key default gen_random_uuid(),
    booking_id text not null,
    provider_id text not null,
    customer_id text not null,
    customer_name text,
    rating integer not null check (rating between 1 and 5),
    comment text not null default '' check (char_length(comment) <= 500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists provider_reviews_booking_id_key
    on public.provider_reviews (booking_id);

create index if not exists provider_reviews_provider_id_idx
    on public.provider_reviews (provider_id);

create index if not exists provider_reviews_customer_id_idx
    on public.provider_reviews (customer_id);

create or replace function public.set_provider_review_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_provider_reviews_set_updated_at on public.provider_reviews;
create trigger trg_provider_reviews_set_updated_at
before update on public.provider_reviews
for each row execute function public.set_provider_review_updated_at();

create or replace function public.refresh_provider_profile_rating(p_provider_id text)
returns void
language plpgsql
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

drop trigger if exists trg_provider_reviews_sync_profile_rating on public.provider_reviews;
create trigger trg_provider_reviews_sync_profile_rating
after insert or update or delete on public.provider_reviews
for each row execute function public.sync_provider_profile_rating_on_review_change();

