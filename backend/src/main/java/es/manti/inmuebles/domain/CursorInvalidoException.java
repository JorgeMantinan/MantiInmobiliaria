package es.manti.inmuebles.domain;

/** Cursor corrupto o emitido para otro orden → 400 ProblemDetail. */
public class CursorInvalidoException extends RuntimeException {

    public CursorInvalidoException() {
        super("Cursor inválido o emitido con otro orden; empiece de nuevo sin cursor");
    }
}
