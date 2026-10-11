package es.manti.mercado.domain;

import java.io.Reader;

/**
 * Puerto de descarga de los CSV oficiales del MIVAU. Es la ÚNICA llamada de
 * red del sistema y sólo ocurre bajo petición de un ADMIN (o del sembrador de
 * desarrollo, si la tabla está vacía); por eso vive tras un puerto y no se
 * inyecta un {@code RestClient} en el servicio.
 *
 * <p>El CSV de alquiler pesa ~37 MB: se entrega como {@link Reader} para que
 * el parser lo consuma en streaming, sin materializar el fichero entero.</p>
 */
public interface FuenteMivauPort {

    /** VDP001_01.csv — alquiler municipal (SERPAVI), separador {@code ;}. */
    void conAlquilerMunicipal(ConsumidorCsv consumidor);

    /** VDP006_01.csv — valor tasado provincial, separador {@code ;}. */
    void conVentaProvincial(ConsumidorCsv consumidor);

    /**
     * Recibe el CSV ya abierto en UTF-8. No lanza checked: los fallos de IO se
     * convierten en {@link FuenteMivauNoDisponibleException} para que el
     * {@code RestClient} no los reenvuelva en una excepción genérica.
     */
    @FunctionalInterface
    interface ConsumidorCsv {
        void procesar(Reader lector);
    }
}
