package es.manti.profesionales.domain;

/** Estados de una solicitud de servicio. MVP: sólo se crea en {@link #NUEVA}. */
public enum EstadoSolicitud {
    NUEVA,
    CERRADA
}
