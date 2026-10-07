package es.manti.inmuebles.domain;

import java.math.BigDecimal;

/**
 * Filtros del listado público. Todos opcionales; los nulos se eliminan de la
 * query (nada de {@code OR null} innecesario en el plan).
 *
 * @param municipioPatron patrón ya construido para
 *                        {@code LOWER(municipio) LIKE LOWER(?)} con comodines
 *                        escapados ({@code %foo%}), o null si no se filtra
 */
public record InmuebleFiltros(
        EstadoInmueble estado,
        TipoInmueble tipo,
        String municipioPatron,
        BigDecimal precioMin,
        BigDecimal precioMax,
        Short habitacionesMin,
        Boolean destacado) {
}
