-- FASE 7 (issue #30) — correcciones del audit db-auditor (propuesta V3):
--
-- 1) Renombra el CHECK auto-generado de usuarios.rol a la convención ck_* del
--    proyecto (skill base-de-datos: todo CHECK con nombre propio para poder
--    diagnosticarlo). PostgreSQL nombra los CHECK inline como
--    <tabla>_<columna>_check; el rename no altera la validación, sólo el
--    nombre expuesto en pg_constraint y en los mensajes de error.
--
-- 2) Índice PARCIAL sobre los refresh tokens vivos por usuario: cubre el
--    predicado EXACTO de RefreshTokenJpaRepository.revocarTodosDeUsuario
--    (usuario_id = ? AND revoked = false, usado en cada rotación de token y en
--    el logout). El índice general de la FK (idx_refresh_tokens_usuario_id)
--    localiza al usuario pero deja el filtro de revoked en el heap; con el
--    parcial la propia B-tree descarta las filas revocadas.
ALTER TABLE usuarios RENAME CONSTRAINT usuarios_rol_check TO ck_usuarios_rol;

CREATE INDEX idx_refresh_tokens_usuario_activo ON refresh_tokens (usuario_id)
    WHERE revoked = false;
