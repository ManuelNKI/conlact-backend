-- ==============================================================================
-- PROYECTO CONLAC-T: Configuración de Buckets en Supabase Storage
-- Buckets:
--   1. product-images : Imágenes de catálogo y variantes (Público, max 5MB)
--   2. recipe-images  : Fotografías de recetas gastronómicas (Público, max 5MB)
--   3. payment-proofs : Comprobantes de transferencia bancaria (Privado, max 10MB)
-- ==============================================================================

CREATE SCHEMA IF NOT EXISTS storage;

CREATE TABLE IF NOT EXISTS storage.buckets (
    id text NOT NULL PRIMARY KEY,
    name text NOT NULL,
    owner uuid REFERENCES auth.users,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    public boolean DEFAULT false,
    avif_autodetection boolean DEFAULT false,
    file_size_limit bigint,
    allowed_mime_types text[]
);

CREATE TABLE IF NOT EXISTS storage.objects (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    bucket_id text REFERENCES storage.buckets(id),
    name text,
    owner uuid REFERENCES auth.users,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    last_accessed_at timestamptz DEFAULT now(),
    metadata jsonb,
    path_tokens text[] GENERATED ALWAYS AS (string_to_array(name, '/')) STORED
);

-- Inserción / Configuración de Buckets
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    (
        'product-images',
        'product-images',
        true,
        5242880, -- 5 MB
        ARRAY['image/jpeg', 'image/png', 'image/webp']
    ),
    (
        'recipe-images',
        'recipe-images',
        true,
        5242880, -- 5 MB
        ARRAY['image/jpeg', 'image/png', 'image/webp']
    ),
    (
        'payment-proofs',
        'payment-proofs',
        false,
        10485760, -- 10 MB
        ARRAY['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
    )
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Políticas de Seguridad RLS en storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 1. Lectura pública para buckets de imágenes (productos y recetas)
DROP POLICY IF EXISTS "Public Read on Public Buckets" ON storage.objects;
CREATE POLICY "Public Read on Public Buckets"
ON storage.objects FOR SELECT
USING (
    bucket_id IN ('product-images', 'recipe-images')
);

-- 2. Administradores autenticados tienen control total en todos los buckets
DROP POLICY IF EXISTS "Admin All on Storage Objects" ON storage.objects;
CREATE POLICY "Admin All on Storage Objects"
ON storage.objects FOR ALL
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE p.id = auth.uid() AND p.role = 'admin' AND p.is_active = true
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.profiles p
        WHERE p.id = auth.uid() AND p.role = 'admin' AND p.is_active = true
    )
);
