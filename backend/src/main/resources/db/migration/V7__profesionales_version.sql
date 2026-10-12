-- Issue #51 (review de seguridad PR49): control OPTIMISTA en la moderación de
-- profesionales (skill base-de-datos + seguridad A08 "concurrencia sin control
-- = lost update").
--
-- Escenario: dos admins moderan a la vez con listas desfasadas; sin control,
-- el segundo PATCH de RECHAZADO sobre una lista que aún veía PENDIENTE revierte
-- en silencio el ACTIVO del primero.
--
-- Solución: columna `version` gestionada por Hibernate con @Version. Cada
-- escritura lleva `UPDATE ... WHERE id = ? AND version = <leída>`; si otra
-- transacción intervino entre lectura y escritura,0 filas afectadas ->
-- OptimisticLockingFailureException -> 409 RFC 9457 (conflicto-concurrencia).
--
-- NOT NULL DEFAULT 0: conserva las filas existentes y coincide con el valor
-- inicial que Hibernate asigna a entidades nuevas. V1-V6 intactas.
ALTER TABLE profesionales
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
