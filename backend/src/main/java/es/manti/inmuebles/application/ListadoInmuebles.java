package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.InmuebleResumen;

import java.util.List;

/**
 * Resultado del listado público, en el envelope de la API
 * ({@code content + nextCursor + totalElements}).
 *
 * @param totalElements total del filtro sólo en la PRIMERA página (sin cursor);
 *                      null en páginas siguientes para no pagar un COUNT en
 *                      cada salto del feed (skill consultas-eficientes)
 */
public record ListadoInmuebles(List<InmuebleResumen> contenido, String cursorSiguiente, Long totalElements) {
}
