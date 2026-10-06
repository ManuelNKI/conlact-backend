-- ============================================================
-- CONLAC-T - Compatibilidad de Auth para PostgreSQL local
-- Supabase ya proporciona estos objetos. Docker local los necesita
-- antes de crear las políticas de Storage en 03_storage_buckets.sql.
-- Este script no instala los servicios HTTP de Supabase.
-- ============================================================

create schema if not exists auth;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated;
    end if;
end $$;

create or replace function auth.uid()
returns uuid
language sql stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
