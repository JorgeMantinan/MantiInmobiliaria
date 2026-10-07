package es.manti.swipes.domain;

/** Cursor corrupto, no base64 o emitido para otro listado → 400 ProblemDetail. */
public class CursorInvalidoException extends RuntimeException {

    public CursorInvalidoException() {
        super("Cursor inválido o emitido para otro listado; empiece de nuevo sin cursor");
    }
}
