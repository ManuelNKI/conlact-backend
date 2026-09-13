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
        check (longitude is null or longitude between -180 and 180)
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
-- storage_path se enlazará a Supabase Storage en Sprint 4.
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
        check (longitude is null or longitude between -180 and 180)
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
-- provider_transaction_id e idempotency_key ayudan a evitar
-- registrar dos veces una misma notificación/transacción.
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
-- Su uso efectivo se implementará en Sprint 7.
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
-- 19. RLS: seguro por defecto.
-- En Semana 1 NO se crean políticas públicas.
-- Los permisos específicos se definirán por módulo en
-- los sprints de API, administración y seguridad.
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

commit;

-- ============================================================
-- Verificación sugerida después de ejecutar:
--
-- select table_name
-- from information_schema.tables
-- where table_schema = 'public'
-- order by table_name;
--
-- Deben aparecer 17 tablas del esquema public.
-- ============================================================
