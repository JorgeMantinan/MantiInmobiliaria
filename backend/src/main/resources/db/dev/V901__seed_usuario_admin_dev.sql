-- FASE 2 — cuenta ADMIN de DESARROLLO (perfil dev únicamente).
-- Credenciales documentadas en README (sección Desarrollo) y SÓLO para dev:
-- email admin@manti.dev / password Admin1234! (hash BCrypt strength 10,
-- generado con el mismo BCryptPasswordEncoder que usa SecurityConfig).
INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
VALUES ('admin@manti.dev',
        '$2a$10$94FsVRaZhI3zkBFY6wSnvOOXE6xWI2hwP3POD9L.X2zUmt50NM39a',
        'Admin Dev', 'ADMIN', true)
ON CONFLICT (email) DO NOTHING;
