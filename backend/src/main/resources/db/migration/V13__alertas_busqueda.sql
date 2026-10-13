-- issue #96 — ALERTAS DE BÚSQUEDA: guardar los filtros del listado y avisar
-- al usuario cuando entra un inmueble nuevo que encaja.
--
-- Reglas aplicadas (skill base-de-datos):
--   * snake_case, PK bigint identity, timestamptz, NOT NULL/CHECK/FK con nombre
--     y ON DELETE explícito (nunca la cláusula olvidada).
--   * Los filtros del listado van en COLUMNAS TIPADAS (una por parámetro del
--     contrato público de GET /api/v1/inmuebles), no en JSONB: son 7 campos
--     estables, el dominio ya los modela así en inmuebles y así la BD puede
--     garantizar rangos y enums con CHECK (skill base-de-datos #4/#6). Si el
--     contrato de filtros crece, se añade columna en una migración nueva —
--     coherente con cómo evoluciona el resto del esquema.
--   * El "último aviso" es una MARCA DE AGUA de id de inmueble
--     (ultimo_inmueble_avisado_id), NO una FK: los ids de inmuebles son
--     monotinos (IDENTITY) y el job sólo mira inmuebles con id superior; una
--     referencia rompería el aviso si el inmueble se borrara. El timestamp
--     (ultimo_aviso_en) sólo se expone al usuario en la UI.
--
-- Reutilización del outbox existente (Brevo + Telegram, módulo profesionales):
-- la fila de una alerta es el MISMO registro que la de una solicitud con un
-- origen distinto, así que V13 abre ese origen:
--   * solicitud_id pasa a admitir NULL y se añade alerta_id (FK CASCADE: al
--     borrar la alerta desaparecen sus avisos pendientes);
--   * ck_notificaciones_outbox_origen garantiza que UNA fila tenga EXACTAMENTE
--     un origen (solicitud XOR alerta);
--   * destinatario_tipo admite USUARIO (el dueño de la alerta, por email) con
--     destinatario_id NULL — no hay FK a usuarios porque la columna sólo se
--     usa hoy para el vínculo con profesionales.

CREATE TABLE alertas_busqueda (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id                 bigint NOT NULL,
    nombre                     varchar(80) NOT NULL,
    -- Filtros del listado (mismos valores que los query params públicos).
    estado                     varchar(8),
    tipo                       varchar(16),
    municipio                  varchar(120),
    precio_min                 numeric(12, 2),
    precio_max                 numeric(12, 2),
    habitaciones_min           smallint,
    destacado                  boolean,
    -- Marca de agua: sólo se avisa de inmuebles con id superior (ver cabecera).
    ultimo_inmueble_avisado_id bigint NOT NULL DEFAULT 0,
    ultimo_aviso_en            timestamptz,
    activa                     boolean NOT NULL DEFAULT true,
    creado_en                  timestamptz NOT NULL DEFAULT now(),
    actualizado_en             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_alertas_busqueda_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_alertas_busqueda_nombre CHECK (char_length(nombre) BETWEEN 1 AND 80),
    CONSTRAINT ck_alertas_busqueda_estado
        CHECK (estado IS NULL OR estado IN ('VENTA', 'ALQUILER')),
    CONSTRAINT ck_alertas_busqueda_tipo
        CHECK (tipo IS NULL OR tipo IN ('PISO', 'CASA', 'ATICO', 'DUPLEX',
                                        'ESTUDIO', 'LOCAL', 'TERRENO')),
    -- Misma regla que valida la API (validarRangos del listado), en defensa
    -- en profundidad: precios >= 0 y rango coherente.
    CONSTRAINT ck_alertas_busqueda_precio CHECK (
        (precio_min IS NULL OR precio_min >= 0) AND
        (precio_max IS NULL OR precio_max >= 0) AND
        (precio_min IS NULL OR precio_max IS NULL OR precio_min <= precio_max)),
    CONSTRAINT ck_alertas_busqueda_habitaciones
        CHECK (habitaciones_min IS NULL OR habitaciones_min >= 0),
    CONSTRAINT ck_alertas_busqueda_marca CHECK (ultimo_inmueble_avisado_id >= 0)
);

-- Índices sobre las queries REALES (skill base-de-datos #8):
--   * por usuario: el listado GET /api/v1/alertas filtra por usuario_id (y es
--     la FK, que PostgreSQL no indexa sola);
--   * parcial de activas: el job sólo evalúa WHERE activa = true, y así las
--     pausadas ni se tocan en el barrido.
CREATE INDEX idx_alertas_busqueda_usuario ON alertas_busqueda (usuario_id);
CREATE INDEX idx_alertas_busqueda_activas ON alertas_busqueda (id) WHERE activa;

-- ---------------------------------------------------------------------------
-- Outbox compartido: abrir el origen "alerta" (ver cabecera de esta migración).
-- El orden importa: la FK nueva necesita la tabla recién creada.
-- ---------------------------------------------------------------------------
ALTER TABLE notificaciones_outbox ALTER COLUMN solicitud_id DROP NOT NULL;

ALTER TABLE notificaciones_outbox ADD COLUMN alerta_id bigint;

ALTER TABLE notificaciones_outbox
    ADD CONSTRAINT fk_notificaciones_outbox_alerta FOREIGN KEY (alerta_id)
        REFERENCES alertas_busqueda (id) ON DELETE CASCADE;

ALTER TABLE notificaciones_outbox DROP CONSTRAINT ck_notificaciones_outbox_destinatario_tipo;
ALTER TABLE notificaciones_outbox
    ADD CONSTRAINT ck_notificaciones_outbox_destinatario_tipo
        CHECK (destinatario_tipo IN ('PROFESIONAL', 'GRUPO', 'USUARIO'));

ALTER TABLE notificaciones_outbox DROP CONSTRAINT ck_notificaciones_outbox_destinatario;
ALTER TABLE notificaciones_outbox
    ADD CONSTRAINT ck_notificaciones_outbox_destinatario CHECK (
        (destinatario_tipo = 'PROFESIONAL' AND destinatario_id IS NOT NULL)
        OR (destinatario_tipo IN ('GRUPO', 'USUARIO') AND destinatario_id IS NULL));

-- Coherencia entre columnas de origen: exactamente UNA por fila.
ALTER TABLE notificaciones_outbox
    ADD CONSTRAINT ck_notificaciones_outbox_origen CHECK (
        (solicitud_id IS NOT NULL AND alerta_id IS NULL)
        OR (solicitud_id IS NULL AND alerta_id IS NOT NULL));

-- FK indexada para los chequeos de integridad y el borrado en cascada.
CREATE INDEX idx_outbox_alerta ON notificaciones_outbox (alerta_id);
