package es.manti.mercado.application;

import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.MercadoNoEncontradoException;
import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.PrecioMercadoRepository;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Consulta pública de precios oficiales de una zona (issue #12).
 *
 * <p>Dos lecturas puntuales por petición, ambas sobre
 * {@code idx_precios_mercado_ultima} (código INE + tipo + año DESC): el año más
 * reciente y las 3 magnitudes de ese año. Sin N+1 y sin cargas de histórico.</p>
 *
 * <p>Atribución obligatoria según el aviso legal del MIVAU: toda respuesta
 * lleva {@code fuente = "Ministerio de Vivienda y Agenda Urbana"}.</p>
 */
@Service
@RequiredArgsConstructor
public class MercadoService {

    /** Texto literal exigido por el aviso legal del MIVAU. */
    public static final String FUENTE = "Ministerio de Vivienda y Agenda Urbana";

    private final PrecioMercadoRepository repository;

    /**
     * @param codigoIne código INE de 5 dígitos (ya validado en la API)
     * @return datos de alquiler y/o venta del último año publicado
     * @throws MercadoNoEncontradoException si el municipio no tiene NINGÚN dato
     */
    @Transactional(readOnly = true)
    public DatosMercado consultar(String codigoIne) {
        DatosMercado.AlquilerZona alquiler = alquiler(codigoIne);
        DatosMercado.VentaZona venta = venta(codigoIne);

        if (alquiler == null && venta == null) {
            throw new MercadoNoEncontradoException(codigoIne);
        }
        short anio = alquiler != null ? alquiler.anio() : venta.anio();
        return new DatosMercado(codigoIne, anio, alquiler, venta, FUENTE);
    }

    /** Último año con alquiler municipal; sólo tipología colectiva. */
    private DatosMercado.AlquilerZona alquiler(String codigoIne) {
        Optional<PrecioMercado> ultima = repository.ultimaDe(codigoIne, TipoPrecio.ALQUILER);
        if (ultima.isEmpty()) {
            return null;
        }
        short anio = ultima.get().getAnio();
        Map<IndicadorPrecio, BigDecimal> valores = repository
                .deAnio(codigoIne, TipoPrecio.ALQUILER, anio)
                .stream()
                .collect(Collectors.toMap(
                        PrecioMercado::getIndicador,
                        PrecioMercado::getValor,
                        (primera, segunda) -> primera));

        BigDecimal renta = valores.get(IndicadorPrecio.RENTA_MEDIANA);
        BigDecimal cuantia = valores.get(IndicadorPrecio.CUANTIA_MEDIANA);
        if (renta == null && cuantia == null) {
            return null;
        }
        return new DatosMercado.AlquilerZona(renta, cuantia, anio);
    }

    /**
     * Último trimestre con valor tasado en la <b>provincia</b> del municipio,
     * prefiere el régimen LIBRE (mercado) y si no existe se queda con lo que
     * haya en ese periodo.
     *
     * <p>La venta sólo existe a escala provincial (no hay fichero público de
     * venta municipal): el código INE de la provincia son los dos primeros
     * dígitos del municipio + {@code 000}, la misma notación de "total de
     * provincia" que usa el importador.</p>
     */
    private DatosMercado.VentaZona venta(String codigoIne) {
        String codigoProvincia = codigoProvinciaDe(codigoIne);
        Optional<PrecioMercado> ultima = repository.ultimaDe(codigoProvincia, TipoPrecio.VENTA);
        if (ultima.isEmpty()) {
            return null;
        }
        PrecioMercado periodo = ultima.get();
        PrecioMercado elegida = repository
                .porPeriodoYTipologia(codigoProvincia, TipoPrecio.VENTA,
                        periodo.getAnio(), periodo.getTrimestre(), TipologiaVivienda.LIBRE)
                .orElse(periodo);

        return new DatosMercado.VentaZona(
                elegida.getValor(),
                elegida.getAmbito().name(),
                elegida.getProvincia(),
                elegida.getAnio(),
                elegida.getTrimestre());
    }

    /** {@code 08019} (Barcelona) → {@code 08000} (provincia de Barcelona). */
    static String codigoProvinciaDe(String codigoIne) {
        return codigoIne.length() < 2 ? codigoIne : codigoIne.substring(0, 2) + "000";
    }
}
