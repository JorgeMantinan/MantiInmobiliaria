-- V10__precios_mercado.sql — precios oficiales del mercado (MIVAU).
--
-- Tabla de datos EXTERNOS: sólo la llena el importador
-- (es.manti.mercado.application.PreciosMercadoImportador), nunca un V*.
-- Decisión documentada en el PR: los CSV del MIVAU cambian cada año y se
-- republican en el CDN, de modo que sembrar datos desde Flyway obligaría a
-- editar una migración ya aplicada (prohibido) o a acumular duplicados.
-- Migración = sólo DDL.
--
-- Fuentes (aviso legal: "Fuente: Ministerio de Vivienda y Agenda Urbana"):
--   * VDP001_01.csv — alquiler municipal SERPAVI. Cabecera REAL verificada:
--       COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;
--       TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR
--     Donde COD_POSTAL lleva el CÓDIGO INE de 5 dígitos del municipio
--     (28079 Madrid, 08019 Barcelona, 28005 Alcalá de Henares...).
--     ELEMENTO ∈ {PRECIO, SUPERFICIE, VIVIENDA}; TIPO_MEDIDA ∈
--     {MEDIANA, PERCENT25, PERCENT75, RECUENTO}; sólo se importa
--     tipología COLECTIVA con TIPO_MEDIDA = MEDIANA (y RECUENTO para
--     n_testigos). 531.585 filas, último año 2024.
--   * VDP006_01.csv — valor tasado provincial. Cabecera REAL verificada:
--       Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO
--     Valor en €/m²; CPRO sin rellenar a la izquierda (1..52) y literal
--     "null" para la fila histórica "Ceuta y Melilla"; sólo se importan las
--     filas con CPRO numérico (provincia real).
--
-- Nota de modelado: los ámbitos comparten tabla porque comparten la misma
-- clave natural y el mismo ciclo de vida de importación. El ámbito PROVINCIA
-- usa codigo_ine = <CPRO>||'000' (notación INE de "total de provincia"), que
-- no puede colisionar con ningún municipio real (el rango municipal es
-- PP001..PP999).

CREATE TABLE precios_mercado (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 5 dígitos INE: municipio (28079) o total de provincia (28000).
    codigo_ine     char(5)      NOT NULL,
    ambito         varchar(16)  NOT NULL,
    tipo           varchar(8)   NOT NULL,
    indicador      varchar(20)  NOT NULL,
    tipologia      varchar(16)  NOT NULL,
    anio           smallint     NOT NULL,
    -- 0 = dato anual (alquiler municipal); 1..4 = trimestre (valor tasado).
    trimestre      smallint     NOT NULL DEFAULT 0,
    valor          numeric(12, 2) NOT NULL,
    unidad         varchar(16)  NOT NULL,
    -- Nº de testigos de la muestra SERPAVI; NULL cuando la fuente no lo da.
    n_testigos     integer,
    -- Sólo en ámbito PROVINCIA (nombre de la provincia, para la respuesta).
    provincia      varchar(100),
    fuente         varchar(100) NOT NULL DEFAULT 'MIVAU',
    fecha_descarga timestamptz  NOT NULL DEFAULT now(),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_precios_mercado_codigo_ine CHECK (codigo_ine ~ '^[0-9]{5}$'),
    CONSTRAINT ck_precios_mercado_ambito     CHECK (ambito IN ('MUNICIPIO', 'PROVINCIA')),
    CONSTRAINT ck_precios_mercado_tipo       CHECK (tipo IN ('ALQUILER', 'VENTA')),
    CONSTRAINT ck_precios_mercado_indicador  CHECK (indicador IN (
        'RENTA_MEDIANA', 'CUANTIA_MEDIANA', 'SUPERFICIE_MEDIANA', 'VALOR_TASADO')),
    CONSTRAINT ck_precios_mercado_tipologia  CHECK (tipologia IN (
        'COLECTIVA', 'UNIFAMILIAR', 'LIBRE', 'PROTEGIDA')),
    CONSTRAINT ck_precios_mercado_anio       CHECK (anio BETWEEN 1990 AND 2100),
    CONSTRAINT ck_precios_mercado_trimestre  CHECK (trimestre BETWEEN 0 AND 4),
    CONSTRAINT ck_precios_mercado_valor      CHECK (valor >= 0),
    CONSTRAINT ck_precios_mercado_unidad     CHECK (unidad IN (
        'EUR_M2_MES', 'EUR_MES', 'M2', 'EUR_M2')),
    CONSTRAINT ck_precios_mercado_n_testigos CHECK (n_testigos IS NULL OR n_testigos >= 0),
    -- Coherencia ámbito/provincia: el nombre sólo existe en el ámbito provincial.
    CONSTRAINT ck_precios_mercado_provincia  CHECK (
        (ambito = 'PROVINCIA') = (provincia IS NOT NULL)),
    -- Clave natural del upsert del importador.
    CONSTRAINT uk_precios_mercado_clave UNIQUE (codigo_ine, tipo, indicador, tipologia, anio, trimestre)
);

-- Lookup de lectura: GET /api/v1/mercado pide el último (anio, trimestre) de
-- un codigo_ine + tipo. El índice de la UNIQUE no sirve para ordenar porque
-- entre codigo_ine/tipo y anio están indicador y tipologia; éste sí permite
-- ORDER BY anio DESC, trimestre DESC sin sort.
CREATE INDEX idx_precios_mercado_ultima ON precios_mercado (codigo_ine, tipo, anio DESC, trimestre DESC);
