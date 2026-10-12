package es.manti.mercado.domain;

/**
 * Magnitud estadística medida. Los tres primeros vienen del CSV de alquiler
 * municipal (VDP001) y el último del de valor tasado (VDP006).
 */
public enum IndicadorPrecio {

    /** Renta mediana en €/m²·mes. En VDP001 no viene publicada: se deriva de cuantía / superficie. */
    RENTA_MEDIANA,

    /** Cuantía mediana del alquiler en €/mes (columna ELEMENTO=PRECIO). */
    CUANTIA_MEDIANA,

    /** Superficie mediana de la vivienda arrendada en m² (ELEMENTO=SUPERFICIE). */
    SUPERFICIE_MEDIANA,

    /** Valor tasado de la vivienda en €/m² (VDP006). */
    VALOR_TASADO
}
