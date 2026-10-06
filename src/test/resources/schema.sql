-- ============================================================
-- CONLAC-T - MVP Tienda Web
-- Semana 1: Modelo de datos y esquema SQL base
-- PostgreSQL / Supabase
-- Fecha: 2026-09-04
--
-- Objetivo:
-- Dejar definida la estructura relacional completa del MVP.
-- En Semana 1 se valida el modelo, diccionario y entorno.
-- Las políticas RLS operativas, Storage, CRUD, pagos y lógica
-- transaccional se completarán en los sprints posteriores.
-- ============================================================
begin;

-- ------------------------------------------------------------
-- 0. Esquema y tabla de autenticación (Compatibilidad Local / Supabase)
-- ------------------------------------------------------------
create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key
);

-- ------------------------------------------------------------
-- 1. Tipos enumerados
-- ------------------------------------------------------------
create type public.user_role as enum (
    'admin'
);
create type public.product_status as enum (
    'draft',
    'published',
    'hidden'
);
create type public.order_status as enum (
    'pending',
    'paid',
    'preparing',
    'shipped',
    'completed',
    'cancelled'
);
create type public.payment_method as enum (
    'bank_transfer',
    'payphone'
);
create type public.payment_status as enum (
    'pending',
    'verified',
    'approved',
    'rejected',
    'expired',
    'refunded'
);
create type public.delivery_method as enum (
    'pickup',
    'delivery'
);
create type public.reservation_status as enum (
    'active',
    'released',
    'consumed'
);

-- ------------------------------------------------------------
-- 2. Función reutilizable para updated_at
-- ------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- ------------------------------------------------------------
-- 3. Perfiles administrativos vinculados a Supabase Auth
-- NO insertar manualmente en auth.users.
-- ------------------------------------------------------------
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null,
    role public.user_role not null default 'admin',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_profiles_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 4. Asociaciones
-- ------------------------------------------------------------
create table public.associations (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique,
    name text not null unique,
    short_description text,
    history text,
    location_text text,
    latitude numeric(9,6),
    longitude numeric(9,6),
    arcsa_registration text,
    agrocalidad_registration text,
    sanitary_seal_text text,
    video_url text,
    instagram_url text,
    tiktok_url text,
    facebook_url text,
    whatsapp text,
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint associations_latitude_ck
        check (latitude is null or latitude between -90 and 90),
    constraint associations_longitude_ck
        check (longitude is null or latitude between -180 and 180)
);

create trigger trg_associations_updated_at
    before update on public.associations
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 5. Categorías
-- ------------------------------------------------------------
create table public.categories (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    slug text not null unique,
    description text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_categories_updated_at
    before update on public.categories
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 6. Productos
-- ------------------------------------------------------------
create table public.products (
    id uuid primary key default gen_random_uuid(),
    association_id uuid references public.associations(id) on delete restrict,
    category_id uuid references public.categories(id) on delete set null,
    slug text not null unique,
    name text not null,
    cheese_type text,
    short_description text,
    description text,
    origin_text text,
    conservation text,
    status public.product_status not null default 'draft',
    is_featured boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_products_association_id on public.products(association_id);
create index idx_products_category_id on public.products(category_id);
create index idx_products_status on public.products(status);

create trigger trg_products_updated_at
    before update on public.products
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 7. Presentaciones / variaciones e inventario central
-- ------------------------------------------------------------
create table public.product_variants (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete cascade,
    sku text not null unique,
    presentation_name text not null,
    weight_grams integer,
    price numeric(10,2) not null,
    stock integer not null default 0,
    low_stock_threshold integer not null default 5,
    is_active boolean not null default true,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint product_variants_weight_ck
        check (weight_grams is null or weight_grams > 0),
    constraint product_variants_price_ck
        check (price >= 0),
    constraint product_variants_stock_ck
        check (stock >= 0),
    constraint product_variants_threshold_ck
        check (low_stock_threshold >= 0)
);

create index idx_product_variants_product_id
    on public.product_variants(product_id);

create trigger trg_product_variants_updated_at
    before update on public.product_variants
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 8. Imágenes de productos
-- storage_path se enlaza a Supabase Storage
-- ------------------------------------------------------------
create table public.product_images (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete cascade,
    storage_path text not null,
    alt_text text,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    constraint product_images_sort_order_ck check (sort_order >= 0)
);

create index idx_product_images_product_id
    on public.product_images(product_id);

-- ------------------------------------------------------------
-- 9. Testimonios
-- ------------------------------------------------------------
create table public.testimonials (
    id uuid primary key default gen_random_uuid(),
    author_name text not null,
    author_type text,
    quote text not null,
    avatar_url text,
    rating integer constraint testimonials_rating_ck check (rating between 1 and 5),
    is_approved boolean not null default false,
    is_featured boolean not null default false,
    is_archived boolean not null default false,
    is_authorized boolean not null default false,
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_testimonials_updated_at
    before update on public.testimonials
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 10. Recetas
-- ------------------------------------------------------------
create table public.recipes (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique,
    title text not null,
    short_description text,
    prep_minutes integer,
    servings integer,
    ingredients jsonb not null default '[]'::jsonb,
    steps jsonb not null default '[]'::jsonb,
    image_storage_path text,
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint recipes_prep_minutes_ck
        check (prep_minutes is null or prep_minutes > 0),
    constraint recipes_servings_ck
        check (servings is null or servings > 0),
    constraint recipes_ingredients_array_ck
        check (jsonb_typeof(ingredients) = 'array'),
    constraint recipes_steps_array_ck
        check (jsonb_typeof(steps) = 'array')
);

create trigger trg_recipes_updated_at
    before update on public.recipes
    for each row execute function public.set_updated_at();

create table public.recipe_products (
    recipe_id uuid not null references public.recipes(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete restrict,
    is_recommended boolean not null default true,
    primary key (recipe_id, product_id)
);

-- ------------------------------------------------------------
-- 11. Atractivos / contenido turístico
-- ------------------------------------------------------------
create table public.tourist_attractions (
    id uuid primary key default gen_random_uuid(),
    association_id uuid references public.associations(id) on delete set null,
    name text not null,
    attraction_type text,
    description text,
    access_conditions text,
    latitude numeric(9,6),
    longitude numeric(9,6),
    requires_confirmation boolean not null default true,
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tourist_attractions_latitude_ck
        check (latitude is null or latitude between -90 and 90),
    constraint tourist_attractions_longitude_ck
        check (longitude is null or latitude between -180 and 180)
);

create index idx_tourist_attractions_association_id
    on public.tourist_attractions(association_id);

create trigger trg_tourist_attractions_updated_at
    before update on public.tourist_attractions
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 12. Zonas de entrega
-- ------------------------------------------------------------
create table public.shipping_zones (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    description text,
    delivery_fee numeric(10,2) not null default 0,
    delivery_days_text text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint shipping_zones_fee_ck check (delivery_fee >= 0)
);

create trigger trg_shipping_zones_updated_at
    before update on public.shipping_zones
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 13. Pedidos
-- ------------------------------------------------------------
create table public.orders (
    id uuid primary key default gen_random_uuid(),
    order_number bigint generated always as identity unique,
    customer_name text not null,
    customer_tax_id text not null,
    customer_phone text not null,
    customer_email text,
    billing_address text,
    delivery_method public.delivery_method not null,
    shipping_zone_id uuid references public.shipping_zones(id) on delete restrict,
    shipping_address text,
    subtotal numeric(12,2) not null,
    shipping_cost numeric(12,2) not null default 0,
    total numeric(12,2) not null,
    payment_method public.payment_method not null,
    payment_status public.payment_status not null default 'pending',
    status public.order_status not null default 'pending',
    expires_at timestamptz,
    administrative_notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint orders_subtotal_ck check (subtotal >= 0),
    constraint orders_shipping_cost_ck check (shipping_cost >= 0),
    constraint orders_total_ck check (total >= 0),
    constraint orders_total_consistency_ck
        check (total = subtotal + shipping_cost),
    constraint orders_delivery_data_ck check (
        (delivery_method = 'pickup')
        or
        (delivery_method = 'delivery'
            and shipping_zone_id is not null
            and shipping_address is not null
            and length(trim(shipping_address)) > 0)
    )
);

create index idx_orders_status on public.orders(status);
create index idx_orders_payment_status on public.orders(payment_status);
create index idx_orders_created_at on public.orders(created_at desc);

create trigger trg_orders_updated_at
    before update on public.orders
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 14. Detalle de pedidos
-- Se guarda snapshot de nombre/presentación/precio.
-- ------------------------------------------------------------
create table public.order_items (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id) on delete cascade,
    product_variant_id uuid not null references public.product_variants(id) on delete restrict,
    product_name_snapshot text not null,
    presentation_snapshot text not null,
    sku_snapshot text not null,
    unit_price numeric(10,2) not null,
    quantity integer not null,
    line_total numeric(12,2) generated always as (unit_price * quantity) stored,
    created_at timestamptz not null default now(),
    constraint order_items_unit_price_ck check (unit_price >= 0),
    constraint order_items_quantity_ck check (quantity > 0)
);

create index idx_order_items_order_id on public.order_items(order_id);
create index idx_order_items_variant_id on public.order_items(product_variant_id);

-- ------------------------------------------------------------
-- 15. Pagos
-- ------------------------------------------------------------
create table public.payments (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id) on delete restrict,
    method public.payment_method not null,
    status public.payment_status not null default 'pending',
    amount numeric(12,2) not null,
    provider_transaction_id text unique,
    idempotency_key text unique,
    bank_reference text,
    transfer_proof_storage_path text,
    provider_payload jsonb,
    paid_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint payments_amount_ck check (amount >= 0)
);

create index idx_payments_order_id on public.payments(order_id);
create index idx_payments_status on public.payments(status);

create trigger trg_payments_updated_at
    before update on public.payments
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 16. Reservas temporales de inventario
-- ------------------------------------------------------------
create table public.inventory_reservations (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id) on delete cascade,
    product_variant_id uuid not null references public.product_variants(id) on delete restrict,
    quantity integer not null,
    status public.reservation_status not null default 'active',
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint inventory_reservations_quantity_ck check (quantity > 0),
    unique (order_id, product_variant_id)
);

create index idx_inventory_reservations_variant_status
    on public.inventory_reservations(product_variant_id, status);
create index idx_inventory_reservations_expires_at
    on public.inventory_reservations(expires_at);

create trigger trg_inventory_reservations_updated_at
    before update on public.inventory_reservations
    for each row execute function public.set_updated_at();

-- ------------------------------------------------------------
-- 17. Mensajes de contacto
-- ------------------------------------------------------------
create table public.contact_messages (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    email text not null,
    phone text,
    subject text not null,
    message text not null,
    privacy_accepted boolean not null default false,
    is_resolved boolean not null default false,
    created_at timestamptz not null default now()
);

create index idx_contact_messages_created_at
    on public.contact_messages(created_at desc);

-- ------------------------------------------------------------
-- 18. Auditoría básica
-- ------------------------------------------------------------
create table public.audit_log (
    id bigint generated always as identity primary key,
    actor_user_id uuid references auth.users(id) on delete set null,
    entity_name text not null,
    entity_id text,
    action text not null,
    old_data jsonb,
    new_data jsonb,
    created_at timestamptz not null default now()
);

create index idx_audit_log_entity
    on public.audit_log(entity_name, entity_id);
create index idx_audit_log_created_at
    on public.audit_log(created_at desc);

-- ------------------------------------------------------------
-- 19. RLS: seguro por defecto
-- ------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.associations enable row level security;
alter table public.categories enable row level security;
alter table public.products enable row level security;
alter table public.product_variants enable row level security;
alter table public.product_images enable row level security;
alter table public.testimonials enable row level security;
alter table public.recipes enable row level security;
alter table public.recipe_products enable row level security;
alter table public.tourist_attractions enable row level security;
alter table public.shipping_zones enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.payments enable row level security;
alter table public.inventory_reservations enable row level security;
alter table public.contact_messages enable row level security;
alter table public.audit_log enable row level security;

-- ------------------------------------------------------------
-- 20. Supabase Storage: Buckets y Objetos
-- ------------------------------------------------------------
create schema if not exists storage;

create table if not exists storage.buckets (
    id text not null primary key,
    name text not null,
    owner uuid references auth.users,
    created_at timestamptz default now(),
    updated_at timestamptz default now(),
    public boolean default false,
    avif_autodetection boolean default false,
    file_size_limit bigint,
    allowed_mime_types text[]
);

create table if not exists storage.objects (
    id uuid not null default gen_random_uuid() primary key,
    bucket_id text references storage.buckets(id),
    name text,
    owner uuid references auth.users,
    created_at timestamptz default now(),
    updated_at timestamptz default now(),
    last_accessed_at timestamptz default now(),
    metadata jsonb,
    path_tokens text[] generated always as (string_to_array(name, '/')) stored
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('product-images', 'product-images', true, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
    ('recipe-images', 'recipe-images', true, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
    ('payment-proofs', 'payment-proofs', false, 10485760, array['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- ------------------------------------------------------------
-- 21. Fotos de Asociaciones [BE-14]
-- ------------------------------------------------------------
create type public.association_image_type as enum (
    'facility',
    'producer',
    'seal'
);

create table public.association_images (
    id uuid primary key default gen_random_uuid(),
    association_id uuid not null references public.associations(id) on delete cascade,
    image_type public.association_image_type not null,
    url text not null,
    alt_text text,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint association_images_sort_order_ck check (sort_order >= 0),
    constraint association_images_url_length_ck check (length(url) <= 2048),
    constraint association_images_alt_text_ck check (alt_text is null or (length(trim(alt_text)) > 0 and length(alt_text) <= 255)),
    constraint association_images_unique_url unique (association_id, url)
);

create index idx_association_images_association on public.association_images(association_id);
create index idx_association_images_gallery on public.association_images(association_id, sort_order, created_at, id);

create trigger trg_association_images_updated_at
    before update on public.association_images
    for each row
    execute function public.set_updated_at();

alter table public.association_images enable row level security;

commit;

-- ============================================================
-- CONLAC-T - Consorcio de Lácteos de Tungurahua (Pilahuín)
-- Semana 2: Script seed.sql con Datos Maestros [BE-07]
-- ============================================================

begin;

-- ============================================================
-- 1. ASOCIACIONES
-- ============================================================
insert into public.associations (
    id, slug, name, short_description, history, location_text,
    latitude, longitude, arcsa_registration, agrocalidad_registration,
    sanitary_seal_text, video_url, instagram_url, tiktok_url, facebook_url,
    whatsapp, is_published
) values
(
    'a0000000-0000-0000-0000-000000000001',
    'asociacion-el-lindero',
    'Asociación El Lindero',
    'Productores de quesos artesanales de altura en el sector El Lindero a 3.600 msnm.',
    'Asociación pionera con tradición quesera artesanal heredada del proceso suizo de Queseras Bolívar. Procesan leche fresca de altura con altos estándares de calidad.',
    'Sector El Lindero, Parroquia Pilahuín, Cantón Ambato, Tungurahua (3.600 msnm)',
    -1.298500, -78.712300,
    'ARCSA-2023-LIND-001', 'AGRO-TUN-AC-014',
    'Certificación Sanitaria ARCSA y Buenas Prácticas AGROCALIDAD',
    'https://www.youtube.com/watch?v=placeholder_lindero',
    'https://instagram.com/conlact_lindero',
    'https://tiktok.com/@conlact_lindero',
    'https://facebook.com/conlactlindero',
    '+593987654321',
    true
),
(
    'a0000000-0000-0000-0000-000000000002',
    'asociacion-mulanleo',
    'Asociación Mulanleo',
    'Quesería comunitaria especializada en queso de hoja tradicional y derivados lácteos.',
    'Comunidad organizada de pequeños ganaderos dedicados a la recolección diaria de leche andina y elaboración tradicional de quesos con sabor autóctono.',
    'Comunidad Mulanleo, Parroquia Pilahuín, Cantón Ambato, Tungurahua',
    -1.289100, -78.725400,
    'ARCSA-2023-MULA-002', 'AGRO-TUN-AC-015',
    'Registro Sanitario Vigente ARCSA',
    'https://www.youtube.com/watch?v=placeholder_mulanleo',
    'https://instagram.com/conlact_mulanleo',
    'https://tiktok.com/@conlact_mulanleo',
    'https://facebook.com/conlactmulanleo',
    '+593987654322',
    true
),
(
    'a0000000-0000-0000-0000-000000000003',
    'asociacion-apukanlla',
    'Asociación Apukanlla',
    'Tradición láctea andina enfocada en quesos frescos y madurados de pastoreo libre.',
    'Productores familiares comprometidos con la economía comunitaria y la conservación de técnicas tradicionales de cuajado y moldeado artesanal.',
    'Sector Apukanlla, Parroquia Pilahuín, Cantón Ambato, Tungurahua',
    -1.305000, -78.701100,
    'ARCSA-2023-APUK-003', 'AGRO-TUN-AC-016',
    'Registro Sanitario Vigente ARCSA',
    'https://www.youtube.com/watch?v=placeholder_apukanlla',
    'https://instagram.com/conlact_apukanlla',
    'https://tiktok.com/@conlact_apukanlla',
    'https://facebook.com/conlactapukanlla',
    '+593987654323',
    true
)
on conflict (slug) do nothing;

-- ============================================================
-- 2. CATEGORÍAS
-- ============================================================
insert into public.categories (id, name, slug, description, is_active) values
(
    'c0000000-0000-0000-0000-000000000001',
    'Quesos Frescos',
    'quesos-frescos',
    'Elaborados diariamente con leche pura de altura. Sabor suave y textura tierna (preferido por el 46,9% de consumidores).',
    true
),
(
    'c0000000-0000-0000-0000-000000000002',
    'Quesos de Hoja',
    'quesos-de-hoja',
    'Queso de pasta hilada envuelto en hoja vegetal tradicional de achira (preferido por el 38,9% de consumidores).',
    true
),
(
    'c0000000-0000-0000-0000-000000000003',
    'Quesos Semimaduros y Finas Hierbas',
    'quesos-semimaduros-hierbas',
    'Quesos con tiempo de reposo y maduración, ideales para tablas, maridajes y catas.',
    true
),
(
    'c0000000-0000-0000-0000-000000000004',
    'Derivados Lácteos y Manjares',
    'derivados-lacteos',
    'Yogurt natural de altura, dulce de leche tradicional y mantequilla de campo.',
    true
)
on conflict (slug) do nothing;

-- ============================================================
-- 3. PRODUCTOS Y VARIANTES
-- ============================================================
insert into public.products (
    id, association_id, category_id, slug, name, cheese_type,
    short_description, description, origin_text, conservation,
    status, is_featured
) values
(
    'b0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    'queso-fresco-artesanal-el-lindero',
    'Queso Fresco Artesanal El Lindero',
    'Fresco no pasteurizado de altura',
    'Queso tierno con bajo contenido de sal, elaborado con leche recién ordeñada a 3.600 msnm.',
    'Nuestro producto estrella elaborado por las familias de El Lindero. Leche de vacas alimentadas con pastizales andinos sin aditivos químicos. Ideal para el desayuno o acompañar con choclo y café.',
    'Fábrica de Lácteos El Lindero, Pilahuín',
    'Mantener refrigerado entre 2°C y 6°C. Consumir dentro de los 15 días posteriores a la entrega.',
    'published',
    true
),
(
    'b0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000002',
    'c0000000-0000-0000-0000-000000000002',
    'queso-de-hoja-tradicional-mulanleo',
    'Queso de Hoja Tradicional Mulanleo',
    'Pasta hilada tradicional',
    'Auténtico queso de hoja con aroma vegetal y elasticidad perfecta.',
    'Elaborado manualmente mediante hilado en agua caliente y reposado en hoja de achira, otorgándole un aroma silvestre único y una textura deshebrable inconfundible.',
    'Comunidad Mulanleo, Pilahuín',
    'Mantener en refrigeración. Para disfrutar su elasticidad, atemperar 10 minutos antes de consumir.',
    'published',
    true
),
(
    'b0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000003',
    'c0000000-0000-0000-0000-000000000003',
    'queso-andino-oregano-apukanlla',
    'Queso Andino con Orégano Silvestre',
    'Semimaduro aromatizado',
    'Queso de corteza natural infusionado con orégano de páramo.',
    'Con 30 días de maduración controlada. El toque de orégano silvestre realza las notas lácteas andinas, ideal para fundir, pizzas artesanales o tablas de degustación.',
    'Quesería Apukanlla, Pilahuín',
    'Mantener refrigerado. Envolver en papel encerado o film plástico una vez abierto.',
    'published',
    true
)
on conflict (slug) do nothing;

-- Variantes / Presentaciones
insert into public.product_variants (
    id, product_id, sku, presentation_name, weight_grams, price, stock, low_stock_threshold, is_active
) values
(
    'ba000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    'LIN-FRE-500',
    'Bloque 500g (Tradicional)',
    500,
    2.50,
    80,
    10,
    true
),
(
    'ba000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000001',
    'LIN-FRE-1000',
    'Rueda 1000g (Familiar / Restaurante)',
    1000,
    4.80,
    40,
    5,
    true
),
(
    'ba000000-0000-0000-0000-000000000003',
    'b0000000-0000-0000-0000-000000000002',
    'MUL-HOJ-PACK4',
    'Pack 4 unidades (aprox. 400g)',
    400,
    2.40,
    50,
    10,
    true
),
(
    'ba000000-0000-0000-0000-000000000004',
    'b0000000-0000-0000-0000-000000000003',
    'APU-ORE-500',
    'Cuña 500g',
    500,
    3.25,
    35,
    5,
    true
)
on conflict (sku) do nothing;

-- ============================================================
-- 4. RECETAS
-- ============================================================
insert into public.recipes (
    id, slug, title, short_description, prep_minutes, servings,
    ingredients, steps, is_published
) values
(
    'd0000000-0000-0000-0000-000000000001',
    'locro-de-papa-con-queso-fresco-de-altura',
    'Locro Tradicional de Papa con Queso Fresco de Pilahuín',
    'El clásico locro andino espeso y cremoso, coronado con generoso queso fresco tierno.',
    45,
    4,
    '[
        {"item": "Papas chola peladas y cortadas", "quantity": "1 kg"},
        {"item": "Queso Fresco El Lindero cortado en cubos", "quantity": "250 g"},
        {"item": "Leche entera tibia", "quantity": "1 taza"},
        {"item": "Cebolla blanca picada finamente", "quantity": "1 tallo"},
        {"item": "Achiote en pasta o aceite", "quantity": "1 cda"},
        {"item": "Aguacate y cilantro para servir", "quantity": "al gusto"}
    ]'::jsonb,
    '[
        {"step": 1, "instruction": "Hacer un refrito en una olla mediana con el achiote y la cebolla blanca picada."},
        {"step": 2, "instruction": "Agregar las papas en trozos medianos y dorar por 3 minutos con el refrito."},
        {"step": 3, "instruction": "Cubrir con agua hirviendo y cocinar a fuego medio hasta que las papas se deshagan parcialmente y espesen el caldo."},
        {"step": 4, "instruction": "Incorporar la taza de leche tibia y dejar hervir 5 minutos más."},
        {"step": 5, "instruction": "Retirar del fuego e incorporar los cubos de queso fresco El Lindero. Servir de inmediato con aguacate."}
    ]'::jsonb,
    true
),
(
    'd0000000-0000-0000-0000-000000000002',
    'humitas-andinas-con-queso-de-hoja',
    'Humitas Dulces Rellenas de Queso de Hoja',
    'Tiernas humitas de choclo criollo con centro derretido de queso de hoja deshebrado.',
    60,
    6,
    '[
        {"item": "Choclos tiernos desgranados", "quantity": "6 unidades"},
        {"item": "Queso de Hoja Mulanleo deshebrado", "quantity": "200 g"},
        {"item": "Mantequilla derretida", "quantity": "4 cdas"},
        {"item": "Huevos (separadas claras de yemas)", "quantity": "2 unidades"},
        {"item": "Pizca de sal y azúcar", "quantity": "al gusto"},
        {"item": "Hojas de choclo limpias para envolver", "quantity": "12 hojas"}
    ]'::jsonb,
    '[
        {"step": 1, "instruction": "Moler los granos de choclo hasta obtener una masa homogénea pero con textura."},
        {"step": 2, "instruction": "Batir las claras a punto de nieve y mezclar suavemente con la masa de choclo, las yemas y la mantequilla."},
        {"step": 3, "instruction": "Colocar 2 cucharadas de masa en cada hoja de choclo y colocar una porción generosa de queso de hoja en el centro."},
        {"step": 4, "instruction": "Doblar y cerrar las humitas cuidadosamente."},
        {"step": 5, "instruction": "Cocinar al vapor en tamalera durante 40 a 45 minutos hasta que la hoja se desprenda con facilidad."}
    ]'::jsonb,
    true
)
on conflict (slug) do nothing;

-- Relación Receta -> Producto
insert into public.recipe_products (recipe_id, product_id, is_recommended) values
('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', true),
('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', true)
on conflict (recipe_id, product_id) do nothing;

-- ============================================================
-- 5. ATRACTIVOS TURÍSTICOS
-- ============================================================
insert into public.tourist_attractions (
    id, association_id, name, attraction_type, description,
    access_conditions, latitude, longitude, requires_confirmation, is_published
) values
(
    '00000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'Mirador y Pastizales de Altura El Lindero',
    'Paisaje natural andino',
    'Vista panorámica a los páramos andinos y ganado lechero de altura a 3.600 msnm. Sendero rural con aire puro de montaña.',
    'Vía secundaria de segundo orden. Acceso preferente en camioneta o vehículo particular alto. Clima frío andino; llevar ropa abrigada y calzado cerrado.',
    -1.298500, -78.712300,
    true,
    true
),
(
    '00000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000002',
    'Ruta del Hilado de Queso en Mulanleo',
    'Experiencia gastronómica vivencial',
    'Demostración del hilado tradicional del queso de hoja y explicación del proceso artesanal guiada por los propios socios de la quesería.',
    'Visita previa coordinación por WhatsApp con 48h de anticipación. Capacidad máxima de 8 a 10 personas por turno debido a espacio en planta.',
    -1.289100, -78.725400,
    true,
    true
)
on conflict do nothing;

-- ============================================================
-- 6. ZONAS DE ENVÍO
-- ============================================================
insert into public.shipping_zones (
    id, name, description, delivery_fee, delivery_days_text, is_active
) values
(
    'f0000000-0000-0000-0000-000000000001',
    'Ambato Urbano (Puntos Céntricos y Domicilio)',
    'Entregas directas en la ciudad de Ambato y coordinación de retiro en ferias aliadas (Plaza Pachano).',
    1.50,
    'Martes, Jueves y Sábados',
    true
),
(
    'f0000000-0000-0000-0000-000000000002',
    'Retiro en Fábricas (Pilahuín)',
    'Retiro directo sin costo de envío en las queserías comunitarias asociadas a CONLAC-T.',
    0.00,
    'Lunes a Domingo (Previa coordinación)',
    true
),
(
    'f0000000-0000-0000-0000-000000000003',
    'Quito Metropolitano (Envío Refrigerado)',
    'Despacho interprovincial refrigerado hacia terminales o entregas programadas en Quito.',
    4.50,
    'Miércoles y Viernes',
    true
),
(
    'f0000000-0000-0000-0000-000000000004',
    'Guayaquil y Costa (Envío por Transporte Especializado)',
    'Envíos de pedidos consolidados y mayoristas con transporte refrigerado.',
    6.00,
    'Sábados (Salida desde Ambato)',
    true
)
on conflict (name) do nothing;

-- ============================================================
-- 7. TESTIMONIOS
-- ============================================================
insert into public.testimonials (
    id, author_name, author_type, quote, is_authorized, is_published, is_approved
) values
(
    'e0000000-0000-0000-0000-000000000001',
    'Mariana Morales',
    'Cliente Final (Ambato)',
    'El queso fresco de El Lindero sabe a la leche de campo de antes, tierno y con el punto exacto de sal. Se nota la diferencia frente al queso industrial.',
    true,
    true,
    true
),
(
    'e0000000-0000-0000-0000-000000000002',
    'Restaurante Tradiciones Andinas',
    'Comprador Comercial B2B (Quito)',
    'Compramos semanalmente el queso de hoja para nuestras humitas y empanadas. La elasticidad y el aroma de achira son insuperables para nuestros platos.',
    true,
    true,
    true
)
on conflict do nothing;

commit;
