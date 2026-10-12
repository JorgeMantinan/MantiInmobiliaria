package es.manti.mercado.application;

import java.time.Instant;

/**
 * Resumen de una pasada de importación de los CSV del MIVAU.
 *
 * @param filasAlquiler  filas de alquiler municipal escritas (nuevas + actualizadas)
 * @param filasVenta     filas de valor tasado provincial escritas
 * @param fechaDescarga  instante en que se descargaron los dos ficheros
 */
public record ResultadoImportacion(
        int filasAlquiler,
        int filasVenta,
        Instant fechaDescarga) {
}
