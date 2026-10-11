package es.manti.mercado.domain;

/**
 * Tipología de la muestra. Una sola columna del esquema recoge dos
 * vocabularios distintos porque así vienen los dos CSV:
 * {@code COLECTIVA}/{@code UNIFAMILIAR} en el alquiler (SERPAVI) y
 * {@code LIBRE}/{@code PROTEGIDA} en el valor tasado (régimen de la vivienda).
 */
public enum TipologiaVivienda {

    /** Edificio de tipología residencial colectiva (pisos). */
    COLECTIVA,

    /** Vivienda unifamiliar. */
    UNIFAMILIAR,

    /** Régimen de protección libre (mercado). */
    LIBRE,

    /** Régimen de protección oficial. */
    PROTEGIDA
}
