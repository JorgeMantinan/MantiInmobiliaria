package es.manti.profesionales.domain;

import java.util.Optional;

/**
 * Puerto de persistencia de las claves {@code Idempotency-Key} ya gastadas del
 * alta de solicitudes. La escritura ocurre en la MISMA transacción que la
 * solicitud (sin commit no hay clave, y sin clave no hay replay posible).
 */
public interface SolicitudIdempotenciaRepository {

    /** La clave gastada, o vacío si todavía no se ha usado. */
    Optional<SolicitudIdempotencia> porClave(String clave);

    SolicitudIdempotencia guardar(SolicitudIdempotencia registro);
}
