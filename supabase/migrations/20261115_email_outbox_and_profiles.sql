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

-- 4. Tablas para retos OTP de recuperación de contraseña y cambio de correo (Heydi)
CREATE TABLE IF NOT EXISTS public.password_reset_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID,
    email VARCHAR(254) NOT NULL,
    code VARCHAR(6) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    reset_token VARCHAR(64) UNIQUE,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    is_consumed BOOLEAN NOT NULL DEFAULT false,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_after TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reset_challenges_req ON public.password_reset_challenges(request_id);
CREATE INDEX IF NOT EXISTS idx_reset_challenges_token ON public.password_reset_challenges(reset_token);

CREATE TABLE IF NOT EXISTS public.email_change_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    current_email VARCHAR(254) NOT NULL,
    new_email VARCHAR(254) NOT NULL,
    code VARCHAR(6) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    is_consumed BOOLEAN NOT NULL DEFAULT false,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_after TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_email_challenges_req ON public.email_change_challenges(request_id);
CREATE INDEX IF NOT EXISTS idx_email_challenges_user ON public.email_change_challenges(user_id);

-- RLS para email_outbox
ALTER TABLE public.email_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.email_change_challenges ENABLE ROW LEVEL SECURITY;
