-- Heydi: códigos OTP hasheados, sesión opaca y contadores de identidad.
-- No reescribe migraciones ya aplicadas. Los retos activos no se conservan.

DELETE FROM public.password_reset_challenges;
DELETE FROM public.email_change_challenges;

ALTER TABLE public.password_reset_challenges
    ALTER COLUMN code TYPE varchar(64);
ALTER TABLE public.email_change_challenges
    ALTER COLUMN code TYPE varchar(64);

CREATE TABLE IF NOT EXISTS public.auth_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash varchar(64) NOT NULL UNIQUE,
    user_id uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user ON public.auth_sessions (user_id);

CREATE TABLE IF NOT EXISTS public.auth_rate_limits (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope varchar(48) NOT NULL,
    key_hash varchar(64) NOT NULL,
    window_start timestamptz NOT NULL,
    hits integer NOT NULL,
    CONSTRAINT uq_auth_rate_scope_key UNIQUE (scope, key_hash)
);

ALTER TABLE public.auth_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auth_rate_limits ENABLE ROW LEVEL SECURITY;
