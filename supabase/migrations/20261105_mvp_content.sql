CREATE TABLE IF NOT EXISTS public.page_contents (
  slug varchar(255) PRIMARY KEY,
  eyebrow varchar(255), titulo varchar(300) NOT NULL,
  subtitulo varchar(3000), contenido jsonb NOT NULL DEFAULT '{}',
  actualizado_en timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.page_contents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recipes ADD COLUMN IF NOT EXISTS video_url text;
ALTER TABLE public.recipes ADD COLUMN IF NOT EXISTS cheese_type varchar(255);
ALTER TABLE public.recipes ADD COLUMN IF NOT EXISTS display_order integer NOT NULL DEFAULT 0;
ALTER TABLE public.tourist_attractions ADD COLUMN IF NOT EXISTS photo_url text;
ALTER TABLE public.tourist_attractions ADD COLUMN IF NOT EXISTS video_url text;
ALTER TABLE public.tourist_attractions ADD COLUMN IF NOT EXISTS duration varchar(255);
ALTER TABLE public.tourist_attractions ADD COLUMN IF NOT EXISTS difficulty varchar(255);
