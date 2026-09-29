-- ============================================================
-- CONLAC-T - MVP Tienda Web
-- [BE-14] Gestión de URLs de Fotos de Asociaciones
-- Entidad: association_images
-- ============================================================

begin;

-- 1. Tipo enumerado para tipos de fotos de asociaciones
create type public.association_image_type as enum (
    'facility',
    'producer',
    'seal'
);

-- 2. Tabla association_images
create table public.association_images (
    id uuid primary key default gen_random_uuid(),

    association_id uuid not null
        references public.associations(id)
        on delete cascade,

    image_type public.association_image_type not null,

    url text not null,

    alt_text text,

    sort_order integer not null default 0,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint association_images_sort_order_ck
        check (sort_order >= 0),

    constraint association_images_url_length_ck
        check (length(url) <= 2048),

    constraint association_images_unique_url
        unique (association_id, url)
);

-- 3. Índices
create index idx_association_images_association
    on public.association_images(association_id);

create index idx_association_images_gallery
    on public.association_images(
        association_id,
        image_type,
        sort_order
    );

-- 4. Trigger updated_at reutilizando public.set_updated_at()
create trigger trg_association_images_updated_at
    before update on public.association_images
    for each row
    execute function public.set_updated_at();

-- 5. Row Level Security (RLS)
alter table public.association_images
    enable row level security;

-- Política de lectura pública: Visible únicamente para fotos de asociaciones publicadas
create policy "Public read published association images"
    on public.association_images
    for select
    using (
        exists (
            select 1 from public.associations a
            where a.id = association_images.association_id
              and a.is_published = true
        )
    );

-- Política de administración total: Inserción, actualización y borrado solo para admins activos
create policy "Admins have full access to association images"
    on public.association_images
    for all
    to authenticated
    using (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.role = 'admin'
              and p.is_active = true
        )
    )
    with check (
        exists (
            select 1 from public.profiles p
            where p.id = auth.uid()
              and p.role = 'admin'
              and p.is_active = true
        )
    );

commit;
