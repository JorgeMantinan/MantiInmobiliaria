-- FASE 2: módulo de inmuebles.
-- Reglas aplicadas (skill base-de-datos):
--   * snake_case, PK bigint identity, timestamptz, CHECK/NOT NULL con nombre.
--   * Soft-delete con eliminado_en timestamptz NULL + índices parciales
--     WHERE eliminado_en IS NULL (el "borrado" nunca rompe el histórico).
--   * Sin lookup de municipios: texto plano por ahora (decisión del ticket).
--   * Índices diseñados sobre las queries REALES del listado (keyset por
--     created_at/id y por precio/id, filtro estado/tipo y municipio ILIKE).

CREATE TABLE inmuebles (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo         varchar(200) NOT NULL,
    descripcion    text,                                  -- opcional
    precio         numeric(12, 2) NOT NULL,
    superficie_m2  integer,                               -- opcional (alquileres antiguos sin datos)
    habitaciones   smallint,
    banios         smallint,
    tipo           varchar(16) NOT NULL,
    estado         varchar(8)  NOT NULL,
    direccion      text NOT NULL,
    codigo_postal  varchar(5),                            -- CP español: 5 dígitos
    municipio      text NOT NULL,
    provincia      text NOT NULL,
    lat            double precision NOT NULL,             -- WGS84, rango península+Baleares+Canarias+Ceuta/Melilla
    lng            double precision NOT NULL,
    imagen_url     text,                                  -- imagen principal (placeholder en dev)
    destacado      boolean NOT NULL DEFAULT false,
    eliminado_en   timestamptz,                           -- soft-delete: NULL = vigente
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_inmuebles_precio_positivo   CHECK (precio > 0),
    CONSTRAINT ck_inmuebles_superficie_positiva CHECK (superficie_m2 IS NULL OR superficie_m2 > 0),
    CONSTRAINT ck_inmuebles_habitaciones      CHECK (habitaciones IS NULL OR habitaciones >= 0),
    CONSTRAINT ck_inmuebles_banios            CHECK (banios IS NULL OR banios >= 0),
    CONSTRAINT ck_inmuebles_tipo CHECK (tipo IN ('PISO', 'CASA', 'ATICO', 'DUPLEX', 'ESTUDIO', 'LOCAL', 'TERRENO')),
    CONSTRAINT ck_inmuebles_estado CHECK (estado IN ('VENTA', 'ALQUILER')),
    CONSTRAINT ck_inmuebles_codigo_postal CHECK (codigo_postal IS NULL OR codigo_postal ~ '^[0-9]{5}$'),
    -- España aproximada: lat 27..44 (Tenerife..Galicia), lng -19..5 (El Hierro..Menorca)
    CONSTRAINT ck_inmuebles_coordenadas CHECK (lat BETWEEN 27 AND 44 AND lng BETWEEN -19 AND 5)
);

-- Índices (nombres idx_inmuebles_*) -----------------------------------------------------------------
-- 1) Feed público por defecto: ORDER BY created_at DESC, id DESC con keyset
--    (created_at, id) < (?, ?). La B-tree ascendente se recorre al revés, por lo
--    que sirve también para DESC; el índice parcial sólo contiene filas vivas.
CREATE INDEX idx_inmuebles_creado_en_id ON inmuebles (created_at, id)
    WHERE eliminado_en IS NULL;

-- 2) sort=precio_asc|precio_desc con tie-break id (misma lectura invertida).
CREATE INDEX idx_inmuebles_precio_id ON inmuebles (precio, id)
    WHERE eliminado_en IS NULL;

-- 3) Filtro por igualdad (estado, tipo) y rango de precio, en el orden de la query.
CREATE INDEX idx_inmuebles_estado_tipo_precio ON inmuebles (estado, tipo, precio)
    WHERE eliminado_en IS NULL;

-- 4) Filtro de portada "destacados" con el mismo orden del feed.
CREATE INDEX idx_inmuebles_destacado_creado_en ON inmuebles (destacado, created_at, id)
    WHERE eliminado_en IS NULL;

-- 5) Búsqueda PARCIAL e insensible a mayúsculas de municipio: ILIKE '%x%' no puede
--    usar una B-tree (comodín inicial), hace falta trigram (extensión oficial
--    contrib, disponible en dev/CI/Neon). Coste: ~1 índice GIN pequeño por tabla
--    y una escritura extra en cada INSERT/UPDATE de municipio — despreciable frente
--    al coste de un seq scan por cada búsqueda pública. Se indexa lower(municipio)
--    y la query usa LOWER(col) LIKE LOWER(?) para que ambos lados se plieguen con
--    las MISMAS reglas de la base de datos (sin depender de la locale de la JVM).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_inmuebles_municipio_trgm ON inmuebles USING gin (lower(municipio) gin_trgm_ops)
    WHERE eliminado_en IS NULL;
