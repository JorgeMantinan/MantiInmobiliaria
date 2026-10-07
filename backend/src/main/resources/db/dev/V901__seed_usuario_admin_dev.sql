-- FASE 2 — cuenta ADMIN de DESARROLLO (perfil dev únicamente).
-- La contraseña en claro NO se escribe en ningún sitio del repositorio: sólo
-- vive aquí el hash BCrypt strength 10 (mismo BCryptPasswordEncoder que usa
-- SecurityConfig) y el email de la cuenta de desarrollo.
-- email: admin@manti.dev / contraseña: la facilita el equipo (ver README,
-- sección Desarrollo).
INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
VALUES ('admin@manti.dev',
        '$2a$10$94FsVRaZhI3zkBFY6wSnvOOXE6xWI2hwP3POD9L.X2zUmt50NM39a',
        'Admin Dev', 'ADMIN', true)
ON CONFLICT (email) DO NOTHING;
