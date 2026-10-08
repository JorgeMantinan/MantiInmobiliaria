package es.manti.profesionales.domain;

/**
 * Estados del alta de un profesional. El registro público siempre nace en
 * {@link #PENDIENTE}; sólo un ADMIN con {@code PATCH /api/v1/admin/profesionales/{id}}
 * puede moverlo a {@link #ACTIVO} o {@link #RECHAZADO}.
 */
public enum EstadoProfesional {
    PENDIENTE,
    ACTIVO,
    RECHAZADO
}
