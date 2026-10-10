-- issue #57: flujo de restablecimiento de contraseña (forgot/reset).
-- Reglas aplicadas (skill base-de-datos): snake_case, PK bigint identity,
-- timestamptz, NOT NULL/UNIQUE con nombre y FK con ON DELETE explícito.
-- Nada de las migraciones V1-V5 se toca.

CREATE TABLE password_reset_tokens (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  bigint NOT NULL,
    token_hash  varchar(64) NOT NULL,              -- SHA-256 hex del token opaco (NUNCA el token en claro)
    expires_at  timestamptz NOT NULL,              -- TTL 15 minutos desde la emisión
    usado_en    timestamptz,                       -- NULL = vigente; con valor = ya canjeado (un solo uso)
    creado_en   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE
);

-- uq_password_reset_tokens_token_hash ya indexa el hash (lookup del canje).
-- La FK no se indexa sola en PostgreSQL, así que su índice va explícito.
CREATE INDEX idx_password_reset_tokens_usuario_id ON password_reset_tokens (usuario_id);
