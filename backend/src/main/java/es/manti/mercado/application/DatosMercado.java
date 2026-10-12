package es.manti.mercado.application;

import java.math.BigDecimal;

/**
 * Datos de mercado de una zona, ya ensamblados por {@link MercadoService}.
 * Ambos bloques son opcionales: la API sólo devuelve 404 si los DOS son null.
 *
 * @param codigoIne código INE consultado
 * @param anio      año del dato de ALQUILER si existe; si no, el de la venta
 * @param alquiler  alquiler municipal (SERPAVI); null si el municipio no está publicado
 * @param venta     valor tasado provincial; null si la fuente no publica la provincia
 * @param fuente    atribución obligatoria del aviso legal del MIVAU
 */
public record DatosMercado(
        String codigoIne,
        short anio,
        AlquilerZona alquiler,
        VentaZona venta,
        String fuente) {

    /**
     * Bloque de alquiler.
     *
     * <p>{@code anio} sólo lo consume el ensamblador para rellenar el año
     * raíz de la respuesta: el contrato HTTP del alquiler no lo expone.</p>
     */
    public record AlquilerZona(
            BigDecimal rentaMedianaEurM2Mes,
            BigDecimal cuantiaMedianaEurMes,
            short anio) {
    }

    /** Bloque de venta: siempre de ámbito PROVINCIA (no existe venta municipal estatal). */
    public record VentaZona(
            BigDecimal valorEurM2,
            String ambito,
            String provincia,
            short anio,
            short trimestre) {
    }
}
