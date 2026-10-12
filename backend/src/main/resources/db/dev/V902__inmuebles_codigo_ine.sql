-- V902__inmuebles_codigo_ine.sql — seed de DESARROLLO (perfil dev únicamente).
--
-- Activado por spring.flyway.locations en application-dev.yml (db/migration +
-- db/dev). En prod NO se aplica: application-prod.yml sólo incluye db/migration,
-- de modo que el código INE de los inmuebles de producción queda a NULL hasta
-- que el backoffice permita asignarlo (follow-up del issue #12).
--
-- Backfill de los 35 inmuebles del seed V900 por municipio. Corre DESPUÉS de
-- V11 (que añade la columna) tanto en volúmenes nuevos como en los que ya
-- tenían V900/V901 aplicados. Idempotente: sólo toca filas sin código.
--
-- Los códigos se han doble-checkeado contra la cabecera real del CSV del MIVAU
-- (VDP001_01.csv, columna COD_POSTAL) y contra la tabla oficial de códigos de
-- municipio del INE. CORRECCIÓN respecto al ticket: Alcalá de Henares es
-- 28005 (28001 es "Acebeda, La").

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
