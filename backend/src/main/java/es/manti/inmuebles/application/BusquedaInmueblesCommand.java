package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;

import java.math.BigDecimal;

/**
 * Entrada del caso de búsqueda pública. Los valores crudos de la query string
 * ({@code sort} y {@code cursor}) se validan dentro del servicio: la capa api no
 * debe interpretarlos y la capa application no conoce tipos de la capa api.
 *
 * @param limit límite pedido (se acota a 1..50 en el servicio)
 */
public record BusquedaInmueblesCommand(
        EstadoInmueble estado,
        TipoInmueble tipo,
        String municipio,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Short habitacionesMin,
        Boolean destacado,
        String sort,
        String cursor,
        int limit) {
}
