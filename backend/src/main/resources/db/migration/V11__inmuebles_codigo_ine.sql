-- V11__inmuebles_codigo_ine.sql — código INE de 5 dígitos del municipio.
--
-- Clave de enlace entre un inmueble y los precios oficiales del MIVAU
-- (precios_mercado.codigo_ine). Nullable: sólo se conoce para los municipios
-- del seed de demostración; los inmuebles dados de alta después pueden no
-- tenerlo y la ficha simplemente no muestra la sección de precios de zona.
--
-- Backfill de los 35 inmuebles del seed (V900) por municipio. Los códigos se
-- han doble-checkeado contra la cabecera real del CSV del MIVAU
-- (VDP001_01.csv, columna COD_POSTAL) y contra la tabla oficial de códigos de
-- municipio del INE. CORRECCIÓN respecto al ticket: Alcalá de Henares es
-- 28005 (28001 es "Acebeda, La").

ALTER TABLE inmuebles ADD COLUMN codigo_ine char(5);

ALTER TABLE inmuebles ADD CONSTRAINT ck_inmuebles_codigo_ine
    CHECK (codigo_ine IS NULL OR codigo_ine ~ '^[0-9]{5}$');

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
