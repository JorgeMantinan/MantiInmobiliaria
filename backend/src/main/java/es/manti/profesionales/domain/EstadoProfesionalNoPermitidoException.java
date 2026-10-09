package es.manti.profesionales.domain;

/**
 * Intento de llevar un profesional a un estado que la moderación no admite
 * (sólo {@code ACTIVO} o {@code RECHAZADO}; {@code PENDIENTE} es únicamente el
 * estado inicial del registro público) → 409.
 */
public class EstadoProfesionalNoPermitidoException extends RuntimeException {

    public EstadoProfesionalNoPermitidoException(EstadoProfesional estado) {
        super("Estado '" + estado + "' no permitido: sólo se admite ACTIVO o RECHAZADO");
    }
}
