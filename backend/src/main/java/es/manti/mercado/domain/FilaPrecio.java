package es.manti.mercado.domain;

import java.math.BigDecimal;

/**
 * Fila ya interpretada de uno de los CSV del MIVAU: lo que devuelven los
 * parsers y lo que consume el repositorio para hacer el upsert. Es un valor
 * inmutable — sin id, sin timestamps — porque todavía no es una fila de la BD.
 *
 * @param codigoIne  código INE de 5 dígitos (municipio o provincia + "000")
 * @param ambito     {@link AmbitoMercado#MUNICIPIO} o {@link AmbitoMercado#PROVINCIA}
 * @param tipo       {@link TipoPrecio#ALQUILER} o {@link TipoPrecio#VENTA}
 * @param indicador  magnitud medida
 * @param tipologia  tipología/régimen de la muestra
 * @param anio       año del dato
 * @param trimestre  0 = dato anual, 1..4 = trimestre
 * @param valor      valor numérico en {@link UnidadPrecio}
 * @param unidad     unidad de {@code valor}
 * @param nTestigos  nº de testigos de la muestra; null si la fuente no lo da
 * @param provincia  nombre de la provincia; sólo en {@link AmbitoMercado#PROVINCIA}
 */
public record FilaPrecio(
        String codigoIne,
        AmbitoMercado ambito,
        TipoPrecio tipo,
        IndicadorPrecio indicador,
        TipologiaVivienda tipologia,
        short anio,
        short trimestre,
        BigDecimal valor,
        UnidadPrecio unidad,
        Integer nTestigos,
        String provincia) {
}
