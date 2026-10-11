package es.manti.mercado.domain;

/** Ámbito territorial al que se refiere una fila de precios del MIVAU. */
public enum AmbitoMercado {

    /** Municipio: alquiler municipal (SERPAVI), código INE de 5 dígitos. */
    MUNICIPIO,

    /** Provincia: valor tasado de venta, código INE de provincia + "000". */
    PROVINCIA
}
