-- Testimonios: perfil, calificación y moderación administrativa.
-- Se preservan los testimonios que ya eran visibles en el contrato anterior.
begin;

alter table public.testimonials
    add column if not exists avatar_url text,
    add column if not exists rating integer,
    add column if not exists is_approved boolean,
    add column if not exists is_featured boolean not null default false,
    add column if not exists is_archived boolean not null default false;

update public.testimonials
    set is_approved = is_authorized and is_published
    where is_approved is null;

alter table public.testimonials
    alter column is_approved set default false,
    alter column is_approved set not null;

do $$
begin
    if not exists (select 1 from pg_constraint
                   where conrelid = 'public.testimonials'::regclass
                     and conname = 'testimonials_rating_ck') then
        alter table public.testimonials add constraint testimonials_rating_ck
            check (rating is null or rating between 1 and 5);
    end if;
end $$;

create index if not exists idx_testimonials_public
    on public.testimonials(created_at desc, id)
    where is_authorized and is_published and is_approved and not is_archived;

commit;
