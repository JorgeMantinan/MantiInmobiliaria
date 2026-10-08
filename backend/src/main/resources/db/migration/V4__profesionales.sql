-- FASE 5: profesionales, gremios, solicitudes de servicio y outbox de
-- notificaciones.
-- Reglas aplicadas (skill base-de-datos):
--   * snake_case, PK bigint identity, timestamptz, CHECK/UNIQUE/FK con nombre y
--     ON DELETE explícito (nunca la cláusula olvidada).
--   * enums de aplicación como CHECK, jamás CREATE TYPE ... AS ENUM.
--   * Los gremios son datos de DOMINIO (catálogo cerrado de oficios), no datos
--     de ejemplo: la semilla va en esta misma migración para que cualquier BD
--     (dev, CI, prod) arranque con el mismo catálogo y los slugs que el
--     frontend pega en sus formularios. Los seeds de EJEMPLO siguen en db/dev.
--   * Índices diseñados sobre las queries REALES de la FASE 5:
--       - dispatcher del outbox: WHERE estado='PENDIENTE' ORDER BY creado_en,id
--         LIMIT 20  -> idx_outbox_pendiente PARCIAL sobre (creado_en, id).
--         Se elige parcial porque: (1) sólo indexa las filas vivas (el outbox
--         pasa a ser enorme en cuanto ENVIADA/FALLIDA superan a PENDIENTE) y
--         (2) (creado_en, id) satisface filtro + orden + LIMIT de una sola
--         pasada, sin Sort. `estado` NO es columna del índice: dentro de un
--         índice parcial con predicado `estado='PENDIENTE'` esa columna es una
--         constante y sólo añadiría bytes a cada entrada.
--         Alternativa no parcial (estado, creado_en) serviría el filtro pero
--         dejaría el desempate por id a un Incremental Sort y arrastraría las
--         millones de filas ya enviadas.
--       - "¿quién es de este gremio?": idx_profesional_gremio_gremio (PK
--         compuesta es (profesional_id, gremio_id), la FK inversa no se indexa
--         sola) e idx_solicitud_gremio_gremio, igual para solicitudes.

CREATE TABLE gremios (
    id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug   varchar(40) NOT NULL,
    nombre varchar(60) NOT NULL,
    CONSTRAINT uq_gremios_slug UNIQUE (slug)
);

-- Catálogo de oficios (slug = identificador estable usado por la API).
INSERT INTO gremios (slug, nombre) VALUES
    ('fontanero',  'Fontanero'),
    ('albañil',    'Albañil'),
    ('electricista', 'Electricista'),
    ('carpintero', 'Carpintero'),
    ('maderista',  'Maderista'),
    ('soldador',   'Soldador'),
    ('pintor',     'Pintor'),
    ('jardinero',  'Jardinero'),
    ('cerrajero',  'Cerrajero'),
    ('gasista',    'Gasista'),
    ('techador',   'Techador'),
    ('arquitecto', 'Arquitecto');

CREATE TABLE profesionales (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         varchar(120) NOT NULL,
    email          varchar(255) NOT NULL,
    telefono       varchar(30),                          -- opcional en el registro
    municipio      varchar(120),                         -- opcional en el registro
    estado         varchar(10) NOT NULL DEFAULT 'PENDIENTE',
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_profesionales_email UNIQUE (email),
    CONSTRAINT ck_profesionales_estado
        CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'RECHAZADO'))
);

CREATE TABLE profesional_gremio (
    profesional_id bigint NOT NULL,
    gremio_id      bigint NOT NULL,
    CONSTRAINT pk_profesional_gremio PRIMARY KEY (profesional_id, gremio_id),
    CONSTRAINT fk_profesional_gremio_profesional FOREIGN KEY (profesional_id)
        REFERENCES profesionales (id) ON DELETE CASCADE,
    CONSTRAINT fk_profesional_gremio_gremio FOREIGN KEY (gremio_id)
        REFERENCES gremios (id) ON DELETE CASCADE
);

CREATE INDEX idx_profesional_gremio_gremio ON profesional_gremio (gremio_id);

CREATE TABLE solicitudes (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_contacto varchar(120) NOT NULL,
    email_contacto  varchar(255) NOT NULL,
    telefono        varchar(30),
    municipio       varchar(120) NOT NULL,
    descripcion     text NOT NULL,
    estado          varchar(8) NOT NULL DEFAULT 'NUEVA',
    creado_en       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_solicitudes_estado CHECK (estado IN ('NUEVA', 'CERRADA')),
    -- Misma regla que valida la API: 10..2000 caracteres (defensa en profundidad).
    CONSTRAINT ck_solicitudes_descripcion
        CHECK (char_length(descripcion) BETWEEN 10 AND 2000),
    CONSTRAINT ck_solicitudes_municipio CHECK (char_length(municipio) <= 120)
);

CREATE TABLE solicitud_gremio (
    solicitud_id bigint NOT NULL,
    gremio_id    bigint NOT NULL,
    CONSTRAINT pk_solicitud_gremio PRIMARY KEY (solicitud_id, gremio_id),
    CONSTRAINT fk_solicitud_gremio_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitud_gremio_gremio FOREIGN KEY (gremio_id)
        REFERENCES gremios (id) ON DELETE CASCADE
);

CREATE INDEX idx_solicitud_gremio_gremio ON solicitud_gremio (gremio_id);

-- Outbox transaccional (skill microservicios): la fila se escribe en la MISMA
-- transacción que la solicitud y un despachador @Scheduled la envía después.
-- Nunca se llama a Brevo/Telegram dentro de la transacción de negocio.
CREATE TABLE notificaciones_outbox (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id       bigint NOT NULL,
    canal              varchar(10) NOT NULL,
    destinatario_tipo  varchar(15) NOT NULL,
    destinatario_id    bigint,                           -- NULL sólo para GRUPO
    destino            varchar(255) NOT NULL,            -- email o chat_id
    asunto             varchar(200) NOT NULL,
    cuerpo             text NOT NULL,
    estado             varchar(10) NOT NULL DEFAULT 'PENDIENTE',
    intentos           smallint NOT NULL DEFAULT 0,
    ultimo_error       varchar(500),
    creado_en          timestamptz NOT NULL DEFAULT now(),
    enviado_en         timestamptz,
    CONSTRAINT fk_notificaciones_outbox_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitudes (id) ON DELETE CASCADE,
    CONSTRAINT fk_notificaciones_outbox_profesional FOREIGN KEY (destinatario_id)
        REFERENCES profesionales (id) ON DELETE CASCADE,
    CONSTRAINT ck_notificaciones_outbox_canal
        CHECK (canal IN ('EMAIL', 'TELEGRAM')),
    CONSTRAINT ck_notificaciones_outbox_destinatario_tipo
        CHECK (destinatario_tipo IN ('PROFESIONAL', 'GRUPO')),
    CONSTRAINT ck_notificaciones_outbox_estado
        CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    CONSTRAINT ck_notificaciones_outbox_intentos CHECK (intentos >= 0),
    -- Coherencia entre el tipo de destinatario y su referencia.
    CONSTRAINT ck_notificaciones_outbox_destinatario CHECK (
        (destinatario_tipo = 'PROFESIONAL' AND destinatario_id IS NOT NULL)
        OR (destinatario_tipo = 'GRUPO' AND destinatario_id IS NULL)
    )
);

-- Índice parcial del despachador: sólo PENDIENTE y en el orden exacto de la
-- query (ver cabecera de esta migración).
CREATE INDEX idx_outbox_pendiente ON notificaciones_outbox (creado_en, id)
    WHERE estado = 'PENDIENTE';

-- Filtro habitual del backoffice: listar las pendientes más recientes.
CREATE INDEX idx_outbox_solicitud ON notificaciones_outbox (solicitud_id);
