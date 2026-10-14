package es.manti.inmuebles.domain;

/**
 * El usuario ya alcanzó el máximo de alertas permitidas → 409 ProblemDetail.
 *
 * <p>Límite defensivo (skill seguridad A04): el job evalúa TODA alerta activa
 * en cada pasada, así que sin techo una cuenta podría encolar un trabajo
 * ilimitado con altas en bucle.</p>
 */
public class AlertaLimiteExcedidoException extends RuntimeException {

    public AlertaLimiteExcedidoException(int maximo) {
        super("Se ha alcanzado el máximo de " + maximo + " alertas de búsqueda");
    }
}
