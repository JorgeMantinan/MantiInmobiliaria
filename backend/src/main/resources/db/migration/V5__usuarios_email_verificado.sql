-- Pre-release (seguridad) — ATO / pre-hijack de cuenta:
-- una cuenta local creada con un email SIN verificar no puede ser "interceptada"
-- por un login con Google con el mismo email. El guard vive en
-- Usuario.vincularGoogle y consulta esta columna.
--
-- Valor por defecto false: los registros locales nacen sin verificar (aún no
-- existe flujo de verificación de email — follow-up documentado en el PR).
-- Las cuentas YA creadas con Google llevaron su email verificado por el
-- proveedor OIDC, así que se marcan como verificadas para no romper su
-- flujo de vínculo.
ALTER TABLE usuarios ADD COLUMN email_verificado boolean NOT NULL DEFAULT false;

UPDATE usuarios SET email_verificado = true WHERE google_id IS NOT NULL;
