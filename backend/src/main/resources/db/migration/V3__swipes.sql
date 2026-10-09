-- FASE 4: módulo de swipes (like/nope) y favoritos.
-- Reglas aplicadas (skill base-de-datos):
--   * snake_case, PK bigint identity, timestamptz, CHECK/UNIQUE/FK con nombre y
--     ON DELETE explícito (nunca la cláusula olvidada).
--   * decision varchar(10) + CHECK: enums de aplicación como CHECK, jamás
--     CREATE TYPE ... AS ENUM (tipos inmutables en PostgreSQL).
--   * Una fila por (usuario, inmueble): el swipe es el ESTADO actual de la
--     decisión, no un histórico, de ahí UNIQUE (usuario_id, inmueble_id). Esa
--     misma constraint es a la vez índice de sondeo del anti-join del deck
--     (NOT EXISTS por usuario_id+inmueble_id).
--   * Índices diseñados sobre las queries REALES de la FASE 4:
--       - deck: NOT EXISTS (usuario_id, inmueble_id) -> uq_swipes_usuario_inmueble
--       - favoritos: usuario_id = ? ORDER BY creado_en DESC
--         -> idx_swipes_usuario_creado (usuario_id, creado_en)
--       - DELETE /favoritos/{id} y el ON DELETE CASCADE de inmuebles
--         -> idx_swipes_inmueble (inmueble_id) (las FK no se indexan solas)

CREATE TABLE swipes (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     bigint NOT NULL,
    inmueble_id    bigint NOT NULL,
    decision       varchar(10) NOT NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_swipes_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_swipes_inmueble FOREIGN KEY (inmueble_id)
        REFERENCES inmuebles (id) ON DELETE CASCADE,
    CONSTRAINT ck_swipes_decision CHECK (decision IN ('LIKE', 'NOPE')),
    CONSTRAINT uq_swipes_usuario_inmueble UNIQUE (usuario_id, inmueble_id)
);

CREATE INDEX idx_swipes_usuario_creado ON swipes (usuario_id, creado_en);
CREATE INDEX idx_swipes_inmueble ON swipes (inmueble_id);
