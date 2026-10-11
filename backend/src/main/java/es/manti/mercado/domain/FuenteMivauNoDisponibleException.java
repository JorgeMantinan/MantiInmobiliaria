package es.manti.mercado.domain;

/**
 * El CDN del MIVAU no responde, devuelve un error o publica un CSV con una
 * estructura distinta a la esperada. Se lanza desde el adaptador (red) y desde
 * los parsers (formato); la API la traduce a ProblemDetail 500.
 */
public class FuenteMivauNoDisponibleException extends RuntimeException {

    public FuenteMivauNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public FuenteMivauNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
