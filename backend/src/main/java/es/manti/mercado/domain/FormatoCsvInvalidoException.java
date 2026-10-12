package es.manti.mercado.domain;

/**
 * El MIVAU ha publicado un CSV con una estructura distinta a la que conoce el
 * parser (cabecera cambiada, fichero vacío...). Es un subtipo de
 * {@link FuenteMivauNoDisponibleException} para que el importador y el runner
 * de dev la traten igual, pero la API la traduce a un <b>detail fijo</b>: la
 * cabecera real sólo va al log, nunca a la respuesta (skill clean-code, regla 7
 * — mensajes fijos del dominio).
 */
public class FormatoCsvInvalidoException extends FuenteMivauNoDisponibleException {

    private final String cabeceraDetectada;

    public FormatoCsvInvalidoException(String origen, String cabeceraDetectada) {
        super("Cabecera inesperada en el CSV de " + origen + ": " + cabeceraDetectada);
        this.cabeceraDetectada = cabeceraDetectada;
    }

    /** Cabecera real encontrada; sólo para diagnóstico en el log. */
    public String cabeceraDetectada() {
        return cabeceraDetectada;
    }
}
