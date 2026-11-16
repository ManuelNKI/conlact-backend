-- Heydi: índice de búsqueda del reto vigente por correo.
-- No reescribe la migración 20261115, que ya creó las tablas de OTP.

CREATE INDEX IF NOT EXISTS idx_reset_challenges_email
    ON public.password_reset_challenges (email);
