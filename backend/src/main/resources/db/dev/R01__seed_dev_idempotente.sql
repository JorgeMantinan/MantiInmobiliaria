-- R01__seed_dev_idempotente.sql — seeds de DESARROLLO REPETIBLES (issue #31).
--
-- Por qué existe este repeatable si los V90x siguen intactos:
--   * Las migraciones versionadas YA APLICADAS son inmutables: editar V900,
--     V901 o V902 rompería su checksum en toda BD que los tenga en el
--     historial (FlywayValidateException), así que NO se tocan.
--   * Los seeds, en cambio, deben poder re-ejecutarse (reparación de
--     historial, volumen recreado, datos a medias) sin duplicar filas: eso es
--     lo que hace este R__ con guardas idempotentes.
--
-- Orden de Flyway: primero los versionados (V900-V902) y después los
-- repeatables. Sólo existe en el perfil dev (spring.flyway.locations de
-- application-dev.yml): ni prod ni la suite de tests incluyen db/dev.
--
-- El seed de INMUEBLES vive únicamente en V900 (tabla vacía como guardia; no
-- hay ON CONFLICT posible sin clave natural). Este fichero sólo repara admin
-- y código INE, que sí tienen guardas idempotentes baratas.

-- 1) Usuario admin de desarrollo — idempotente por uq_usuarios_email.
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

-- 2) Backfill del código INE de los inmuebles del seed — idempotente.
-- Sólo toca filas sin código (los inmuebles de V900 y los creados a mano en
-- dev que aún no lo tengan). Los códigos están doble-checkeados contra la
-- cabecera real del CSV del MIVAU (VDP001_01.csv) y la tabla del INE.
UPDATE inmuebles
   SET codigo_ine = CASE municipio
        WHEN 'Madrid'             THEN '28079'
        WHEN 'Barcelona'          THEN '08019'
        WHEN 'Valencia'           THEN '46250'
        WHEN 'Sevilla'            THEN '41091'
        WHEN 'Bilbao'             THEN '48020'
        WHEN 'Zaragoza'           THEN '50297'
        WHEN 'Málaga'             THEN '29067'
        WHEN 'Granada'            THEN '18087'
        WHEN 'Alicante'           THEN '03014'
        WHEN 'Badalona'           THEN '08015'
        WHEN 'Getafe'             THEN '28065'
        WHEN 'Alcalá de Henares'  THEN '28005'
        WHEN 'Palma'              THEN '07040'
        WHEN 'Murcia'             THEN '30030'
        WHEN 'Vigo'               THEN '36057'
    END
 WHERE codigo_ine IS NULL;
