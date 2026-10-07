package es.manti.inmuebles.domain;

/**
 * Query param no admitido → 400 ProblemDetail con el motivo (mensaje fijo).
 * Conserva el nombre del parámetro y el motivo para que el advice del módulo
 * pueda rellenar {@code errors:[{field, message}]}.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String parametro;
    private final String motivo;

    public ParametroInvalidoException(String parametro, String motivo) {
        super("Parámetro '" + parametro + "' inválido: " + motivo);
        this.parametro = parametro;
        this.motivo = motivo;
    }

    /** Nombre del query param rechazado (p. ej. {@code precioMin}). */
    public String parametro() {
        return parametro;
    }

    /** Motivo fijo, sin datos de la petición. */
    public String motivo() {
        return motivo;
    }
}
