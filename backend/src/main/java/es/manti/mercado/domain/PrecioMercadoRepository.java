package es.manti.mercado.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de precios del mercado. Implementado en infra por
 * Spring Data JPA (patrón idéntico al de inmuebles/profesionales).
 */
public interface PrecioMercadoRepository {

    /**
     * Fila más reciente de un código + tipo (orden {@code anio DESC, trimestre DESC}).
     * Fija el periodo del que cuelgan el resto de consultas.
     */
    Optional<PrecioMercado> ultimaDe(String codigoIne, TipoPrecio tipo);

    /** Todas las magnitudes de un código + tipo en un año concreto (3-4 filas). */
    List<PrecioMercado> deAnio(String codigoIne, TipoPrecio tipo, Short anio);

    /**
     * Fila concreta de un periodo y tipología. Se usa para elegir el régimen
     * {@code LIBRE} del valor tasado cuando existe para el último trimestre.
     */
    Optional<PrecioMercado> porPeriodoYTipologia(String codigoIne, TipoPrecio tipo,
                                                 Short anio, Short trimestre,
                                                 TipologiaVivienda tipologia);

    /**
     * Upsert por clave natural de todas las filas en UNA transacción
     * ({@code INSERT ... ON CONFLICT}). La llamada de red que produce las filas
     * ocurre ANTES, fuera de la transacción (skill clean-code-servicios, regla 13).
     *
     * @return número de filas escritas (nuevas + actualizadas)
     */
    int guardarOActualizar(List<FilaPrecio> filas, Instant fechaDescarga);

    /** Nº de filas importadas; lo usa el sembrador de desarrollo. */
    long contar();
}
