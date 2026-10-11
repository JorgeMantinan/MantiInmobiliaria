-- V11__inmuebles_codigo_ine.sql — código INE de 5 dígitos del municipio.
--
-- Clave de enlace entre un inmueble y los precios oficiales del MIVAU
-- (precios_mercado.codigo_ine). Nullable: sólo se conoce para los municipios
-- del seed de demostración; los inmuebles dados de alta después pueden no
-- tenerlo y la ficha simplemente no muestra la sección de precios de zona.
--
-- Sólo DDL (decisión de revisión del PR #75): una migración de producción no
-- debe llevar DATOS de demostración. El backfill de los 35 inmuebles del seed
-- vive en db/dev/V902__inmuebles_codigo_ine.sql, que corre tras V900 en el
-- perfil dev —tanto en volúmenes nuevos como en los que ya tenían el seed—
-- y no se aplica nunca en prod (application-prod.yml sólo incluye db/migration).
--
-- Los códigos se han doble-checkeado contra la cabecera real del CSV del MIVAU
-- (VDP001_01.csv, columna COD_POSTAL) y contra la tabla oficial de códigos de
-- municipio del INE. CORRECCIÓN respecto al ticket: Alcalá de Henares es
-- 28005 (28001 es "Acebeda, La").

ALTER TABLE inmuebles ADD COLUMN codigo_ine char(5);

ALTER TABLE inmuebles ADD CONSTRAINT ck_inmuebles_codigo_ine
    CHECK (codigo_ine IS NULL OR codigo_ine ~ '^[0-9]{5}$');
