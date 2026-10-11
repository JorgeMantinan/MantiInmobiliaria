package es.manti.mercado.domain;

/** Unidad de {@link PrecioMercado#valor}; un valor por cada {@link IndicadorPrecio}. */
public enum UnidadPrecio {

    /** Euros por metro cuadrado y mes (renta del alquiler). */
    EUR_M2_MES,

    /** Euros al mes (cuantía total del contrato). */
    EUR_MES,

    /** Metros cuadrados (superficie mediana). */
    M2,

    /** Euros por metro cuadrado (valor tasado de venta). */
    EUR_M2
}
