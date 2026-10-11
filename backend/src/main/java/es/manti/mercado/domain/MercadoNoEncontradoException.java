package es.manti.mercado.domain;

/** El municipio consultado no tiene ni alquiler ni valor tasado publicados. */
public class MercadoNoEncontradoException extends RuntimeException {

    public MercadoNoEncontradoException(String codigoIne) {
        super("No hay precios de mercado publicados para el municipio " + codigoIne);
    }
}
