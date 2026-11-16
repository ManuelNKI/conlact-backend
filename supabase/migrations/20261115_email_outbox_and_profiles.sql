-- ============================================================
-- CONLAC-T - Migración David Barragán: Email Outbox y Perfiles
-- ============================================================

-- 1. Añadir 'editor' al tipo enum user_role si no existe
DO $$
BEGIN
    ALTER TYPE public.user_role ADD VALUE IF NOT EXISTS 'editor';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. Añadir columna email a profiles si no existe
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email VARCHAR(254);
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_email_lower ON public.profiles(LOWER(email));

-- 3. Crear tabla email_outbox para entrega fiable de correos con reintentos
CREATE TABLE IF NOT EXISTS public.email_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    template VARCHAR(50) NOT NULL,
    template_variables JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_retry_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT uq_email_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_pending ON public.email_outbox(status, next_retry_at) WHERE status = 'PENDING';

-- RLS para email_outbox
ALTER TABLE public.email_outbox ENABLE ROW LEVEL SECURITY;
