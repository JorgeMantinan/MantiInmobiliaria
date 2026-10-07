package es.manti.inmuebles.domain;

/** Query param no admitido → 400 ProblemDetail con el motivo (mensaje fijo). */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String parametro, String motivo) {
        super("Parámetro '" + parametro + "' inválido: " + motivo);
    }
}
