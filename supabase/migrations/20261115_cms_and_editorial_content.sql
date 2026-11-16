-- Migración de dominio: CMS de páginas, capacitación, y extensiones de contenido
-- Conforme a ENTREGA_BACKEND_CONTRATOS_Y_MIGRACION.md y DIVISION_EQUIPO_BACKEND.md

BEGIN;

-- 1. CMS de páginas editables
CREATE TABLE IF NOT EXISTS public.page_contents (
    slug text PRIMARY KEY,
    eyebrow text,
    title text NOT NULL,
    subtitle text,
    content_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_page_contents_updated_at') THEN
        CREATE TRIGGER trg_page_contents_updated_at
            BEFORE UPDATE ON public.page_contents
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
    END IF;
END $$;

-- 2. Secciones y Contenidos de Capacitación
CREATE TABLE IF NOT EXISTS public.capacitacion_secciones (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title text NOT NULL,
    description text,
    sort_order integer NOT NULL DEFAULT 0,
    is_published boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_capacitacion_secciones_updated_at') THEN
        CREATE TRIGGER trg_capacitacion_secciones_updated_at
            BEFORE UPDATE ON public.capacitacion_secciones
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.capacitacion_contenidos (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    seccion_id uuid NOT NULL REFERENCES public.capacitacion_secciones(id) ON DELETE CASCADE,
    title text NOT NULL,
    description text,
    content_type text NOT NULL,
    url text,
    body text,
    sort_order integer NOT NULL DEFAULT 0,
    is_published boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT capacitacion_contenidos_type_ck CHECK (content_type IN ('video', 'texto', 'enlace', 'documento'))
);

CREATE INDEX IF NOT EXISTS idx_capacitacion_contenidos_seccion_id
    ON public.capacitacion_contenidos(seccion_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_capacitacion_contenidos_updated_at') THEN
        CREATE TRIGGER trg_capacitacion_contenidos_updated_at
            BEFORE UPDATE ON public.capacitacion_contenidos
            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
    END IF;
END $$;

-- 3. Extensión de Atractivos Turísticos (campos de presentación editorial)
ALTER TABLE public.tourist_attractions
    ADD COLUMN IF NOT EXISTS duration text,
    ADD COLUMN IF NOT EXISTS difficulty text,
    ADD COLUMN IF NOT EXISTS image_url text,
    ADD COLUMN IF NOT EXISTS video_url text;

-- 4. Extensión de Recetas (campos de presentación editorial y orden)
ALTER TABLE public.recipes
    ADD COLUMN IF NOT EXISTS video_url text,
    ADD COLUMN IF NOT EXISTS cheese_type text,
    ADD COLUMN IF NOT EXISTS gradient text,
    ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0;

-- 5. Extensión de Asociaciones (ficha extendida, conteos y metadatos)
ALTER TABLE public.associations
    ADD COLUMN IF NOT EXISTS parish text,
    ADD COLUMN IF NOT EXISTS members integer,
    ADD COLUMN IF NOT EXISTS altitude_msnm integer,
    ADD COLUMN IF NOT EXISTS number_of_families integer,
    ADD COLUMN IF NOT EXISTS business_hours text,
    ADD COLUMN IF NOT EXISTS bpm_registration text,
    ADD COLUMN IF NOT EXISTS fields_json jsonb DEFAULT '[]'::jsonb;

COMMIT;
