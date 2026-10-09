package es.manti.inmuebles.domain;

/** Lanza {@code GlobalExceptionHandler} → 404 ProblemDetail. */
public class InmuebleNoEncontradoException extends RuntimeException {

    public InmuebleNoEncontradoException(Long id) {
        super("Inmueble " + id + " inexistente");
    }
}
