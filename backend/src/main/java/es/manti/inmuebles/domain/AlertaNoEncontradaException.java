package es.manti.inmuebles.domain;

/**
 * La alerta no existe o NO pertenece al usuario actual → 404 ProblemDetail.
 *
 * <p>Owner-scoped como los favoritos: responder 404 (nunca 403) evita revelar
 * la existencia de un id ajeno; el mensaje es fijo, sin datos internos.</p>
 */
public class AlertaNoEncontradaException extends RuntimeException {

    public AlertaNoEncontradaException(Long id) {
        super("Alerta " + id + " no encontrada");
    }
}
