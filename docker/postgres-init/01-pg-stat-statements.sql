-- Issue #31 — extensión pg_stat_statements en el Postgres de DESARROLLO.
-- Se ejecuta automáticamente UNA sola vez, cuando el volumen está vacío
-- (docker-entrypoint-initdb.d de la imagen oficial); en el volumen existente
-- se crea a mano (ver el comentario de docker-compose.yml).
--
-- El preload (shared_preload_libraries) ya viene configurado en el comando del
-- servicio: sin preload esta línea fallaría con "pg_stat_statements must be
-- loaded via shared_preload_libraries".
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
