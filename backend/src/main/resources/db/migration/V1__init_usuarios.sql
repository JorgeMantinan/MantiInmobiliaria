-- FASE 1: autenticación — módulo de usuarios.
-- Reglas aplicadas (skill base-de-datos): snake_case, PK bigint identity,
-- timestamptz, NOT NULL/UNIQUE/CHECK con nombre y FK con ON DELETE explícito.

CREATE TABLE usuarios (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(100),                       -- NULL sólo para cuentas creadas con Google
    nombre        varchar(120) NOT NULL,
    google_id     varchar(64),                        -- NULL sólo para cuentas email/password
    rol           varchar(16) NOT NULL CHECK (rol IN ('USER', 'ADMIN')),
    activo        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT uq_usuarios_google_id UNIQUE (google_id),
    CONSTRAINT ck_usuarios_credencial CHECK (password_hash IS NOT NULL OR google_id IS NOT NULL)
);

CREATE TABLE refresh_tokens (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash  varchar(64) NOT NULL,                 -- SHA-256 del token opaco (nunca el token en claro)
    usuario_id  bigint NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked     boolean NOT NULL DEFAULT false,       -- rotación: el uso anterior queda invalidado
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE
);

-- Toda FK con índice explícito (PostgreSQL no indexa FKs) + lookup por caducidad.
CREATE INDEX idx_refresh_tokens_usuario_id ON refresh_tokens (usuario_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
