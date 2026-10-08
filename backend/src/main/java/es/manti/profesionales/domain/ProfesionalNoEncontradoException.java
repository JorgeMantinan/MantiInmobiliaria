package es.manti.profesionales.domain;

/** El id no corresponde a ningún profesional → 404 ProblemDetail. */
public class ProfesionalNoEncontradoException extends RuntimeException {

    public ProfesionalNoEncontradoException(Long id) {
        super("Profesional " + id + " inexistente");
    }
}
