package es.manti.swipes.domain;

/**
 * Query param de listado no admitido → 400 ProblemDetail con
 * {@code errors:[{field, message}]}. Conserva el nombre del parámetro y el
 * motivo (fijos, sin datos de la petición) para que el advice del módulo pueda
 * rellenar el array.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String parametro;
    private final String motivo;

    public ParametroInvalidoException(String parametro, String motivo) {
        super("Parámetro '" + parametro + "' inválido: " + motivo);
        this.parametro = parametro;
        this.motivo = motivo;
    }

    /** Nombre del query param rechazado (p. ej. {@code limit}). */
    public String parametro() {
        return parametro;
    }

    /** Motivo fijo, sin datos de la petición. */
    public String motivo() {
        return motivo;
    }
}
