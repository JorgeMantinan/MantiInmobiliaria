package es.manti.profesionales.domain;

/**
 * La cabecera {@code Idempotency-Key} ya se gastó y no puede reutilizarse
 * como estaba → 409 (skill api-rest #9: la misma clave sólo puede describir
 * la misma operación).
 */
public class ClaveIdempotenciaConflictivaException extends RuntimeException {

    private ClaveIdempotenciaConflictivaException(String mensaje) {
        super(mensaje);
    }

    /** La clave ya se usó en una petición con un cuerpo DISTINTO al original. */
    public static ClaveIdempotenciaConflictivaException cuerpoDistinto(String clave) {
        return new ClaveIdempotenciaConflictivaException(
                "La clave Idempotency-Key '" + clave
                        + "' ya se usó con un cuerpo distinto al de la petición original");
    }

    /** El registro apunta a una solicitud que ya no existe (borrado externo). */
    public static ClaveIdempotenciaConflictivaException sinSolicitud(String clave) {
        return new ClaveIdempotenciaConflictivaException(
                "La clave Idempotency-Key '" + clave
                        + "' ya se usó y su solicitud original ya no existe");
    }
}
