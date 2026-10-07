package es.manti.swipes.application;

import es.manti.swipes.domain.InmuebleSwipeResumen;

import java.util.List;

/**
 * Resultado paginado del deck o de favoritos, ya en el envelope de la API
 * ({@code content + nextCursor + totalElements}).
 *
 * @param contenido      filas de la página (proyección ligera, nunca entidades)
 * @param cursorSiguiente cursor opaco para la página siguiente; null si no hay más
 * @param totalElements  total SÓLO en la primera página (sin cursor): en las
 *                       siguientes es null para no pagar un COUNT en cada salto
 *                       del feed (skill consultas-eficientes)
 */
public record ListadoSwipe(
        List<InmuebleSwipeResumen> contenido,
        String cursorSiguiente,
        Long totalElements) {
}
