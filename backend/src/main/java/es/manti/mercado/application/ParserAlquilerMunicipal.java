package es.manti.mercado.application;

import es.manti.mercado.domain.AmbitoMercado;
import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.FormatoCsvInvalidoException;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import es.manti.mercado.domain.UnidadPrecio;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser del CSV de alquiler municipal del MIVAU (VDP001_01.csv, 37,8 MB).
 *
 * <p><b>Cabecera REAL</b> (verificada contra el fichero publicado en el CDN,
 * UTF-8 con BOM, separador {@code ;}, decimal con punto):</p>
 * <pre>COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR</pre>
 *
 * <p>Notas de la metodología SERPAVI aplicadas aquí:</p>
 * <ul>
 *   <li>{@code COD_POSTAL} lleva el <b>código INE de 5 dígitos</b> del
 *       municipio, no el postal (Madrid = 28079, Alcalá de Henares = 28005).</li>
 *   <li>{@code ELEMENTO=PRECIO} + {@code MEDIANA} es la <b>cuantía mediana en
 *       €/mes</b> y {@code SUPERFICIE} la <b>superficie mediana en m²</b>
 *       (contrastado con la tabla oficial "Roquetas de Mar 2022": 400 €/mes,
 *       77 m², 4.605 testigos — exactamente esos tres valores).</li>
 *   <li>La <b>renta mediana en €/m²·mes</b> no se publica en el CSV: se deriva
 *       como {@code cuantía / superficie} (error típico &lt; 2 % frente a la
 *       renta publicada por MIVAU, porque la mediana no es lineal).</li>
 *   <li>{@code VIVIENDA} + {@code RECUENTO} es el nº de testigos.</li>
 *   <li>Sólo tipología {@code COLECTIVA} (la que usa la ley 12/2023 para el
 *       rango de alquiler) y únicamente el <b>último año disponible de cada
 *       municipio</b>: el resto de años ocuparían 500 000 filas aportando
 *       poco (la API sólo sirve el último dato).</li>
 * </ul>
 *
 * <p>Streaming de punta a punta: sólo se retiene una muestra por municipio
 * (~3 400 entradas), nunca el fichero.</p>
 */
@Component
public class ParserAlquilerMunicipal {

    /** Cabecera exacta esperada (BOM incluido se elimina antes de comparar). */
    static final String CABECERA_ESPERADA =
            "COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR";

    private static final int COL_CODIGO_INE = 2;
    private static final int COL_ELEMENTO = 4;
    private static final int COL_TIPO_VIVIENDA = 5;
    private static final int COL_TIPO_MEDIDA = 6;
    private static final int COL_ANIO = 7;
    private static final int COL_VALOR = 8;
    private static final int COLUMNAS = 9;

    private static final String TIPO_VIVIENDA_COLECTIVA = "COLECTIVA";
    private static final String ELEMENTO_PRECIO = "PRECIO";
    private static final String ELEMENTO_SUPERFICIE = "SUPERFICIE";
    private static final String ELEMENTO_VIVIENDA = "VIVIENDA";
    private static final String MEDIDA_MEDIANA = "MEDIANA";
    private static final String MEDIDA_RECUENTO = "RECUENTO";

    private static final Pattern CODIGO_INE = Pattern.compile("\\d{5}");

    /**
     * @param lector CSV del MIVAU ya abierto en UTF-8
     * @return una fila por indicador y municipio, sólo del último año disponible
     * @throws FormatoCsvInvalidoException si la cabecera no es la esperada
     * @throws FuenteMivauNoDisponibleException si el fichero es ilegible
     */
    public List<FilaPrecio> parsear(Reader lector) {
        // Sin pattern variable: el Reader es del CALLER (no se cierra aquí) y
        // el binding de JDT lo marcaría como resource leak.
        BufferedReader buffer = lector instanceof BufferedReader
                ? (BufferedReader) lector
                : new BufferedReader(lector);

        Map<String, Muestra> muestrasPorMunicipio = new HashMap<>();
        try {
            String cabecera = buffer.readLine();
            if (cabecera == null) {
                throw new FuenteMivauNoDisponibleException("El CSV de alquiler del MIVAU está vacío");
            }
            String normalizada = cabecera.replace("\uFEFF", "");
            if (!CABECERA_ESPERADA.equals(normalizada)) {
                throw new FormatoCsvInvalidoException("alquiler municipal (VDP001)", normalizada);
            }

            String linea;
            while ((linea = buffer.readLine()) != null) {
                acumular(linea, muestrasPorMunicipio);
            }
        } catch (IOException e) {
            throw new FuenteMivauNoDisponibleException(
                    "No se ha podido leer el CSV de alquiler del MIVAU", e);
        }
        return construirFilas(muestrasPorMunicipio);
    }

    private void acumular(String linea, Map<String, Muestra> muestras) {
        String[] columna = linea.split(";", -1);
        if (columna.length != COLUMNAS) {
            return;                       // fila corrupta o vacía: se ignora
        }
        boolean esCuantia = ELEMENTO_PRECIO.equals(columna[COL_ELEMENTO])
                && MEDIDA_MEDIANA.equals(columna[COL_TIPO_MEDIDA]);
        boolean esSuperficie = ELEMENTO_SUPERFICIE.equals(columna[COL_ELEMENTO])
                && MEDIDA_MEDIANA.equals(columna[COL_TIPO_MEDIDA]);
        boolean esRecuento = ELEMENTO_VIVIENDA.equals(columna[COL_ELEMENTO])
                && MEDIDA_RECUENTO.equals(columna[COL_TIPO_MEDIDA]);
        if (!esCuantia && !esSuperficie && !esRecuento) {
            return;                       // P25/P75 y vivienda unifamiliar no se usan
        }
        if (!TIPO_VIVIENDA_COLECTIVA.equals(columna[COL_TIPO_VIVIENDA])) {
            return;
        }
        String codigoIne = columna[COL_CODIGO_INE];
        if (!CODIGO_INE.matcher(codigoIne).matches()) {
            return;
        }
        Short anio = enteroCorto(columna[COL_ANIO]);
        BigDecimal valor = decimal(columna[COL_VALOR]);
        if (anio == null || valor == null) {
            return;
        }

        Muestra muestra = muestras.get(codigoIne);
        if (muestra == null || anio > muestra.anio) {
            muestra = new Muestra(anio);
            muestras.put(codigoIne, muestra);
        } else if (anio < muestra.anio) {
            return;                       // año anterior al ya guardado
        }
        if (esCuantia) {
            muestra.cuantia = valor;
        } else if (esSuperficie) {
            muestra.superficie = valor;
        } else {
            muestra.testigos = valor.intValue();
        }
    }

    private List<FilaPrecio> construirFilas(Map<String, Muestra> muestras) {
        List<FilaPrecio> filas = new ArrayList<>(muestras.size() * 3);
        for (Map.Entry<String, Muestra> entrada : muestras.entrySet()) {
            String codigoIne = entrada.getKey();
            Muestra muestra = entrada.getValue();
            if (muestra.cuantia == null) {
                continue;                 // sin cuantía no hay dato de alquiler
            }
            if (muestra.superficie != null && muestra.superficie.signum() > 0) {
                BigDecimal renta = muestra.cuantia
                        .divide(muestra.superficie, 2, RoundingMode.HALF_UP);
                filas.add(fila(codigoIne, muestra, IndicadorPrecio.RENTA_MEDIANA,
                        renta, UnidadPrecio.EUR_M2_MES));
            }
            filas.add(fila(codigoIne, muestra, IndicadorPrecio.CUANTIA_MEDIANA,
                    muestra.cuantia, UnidadPrecio.EUR_MES));
            if (muestra.superficie != null) {
                filas.add(fila(codigoIne, muestra, IndicadorPrecio.SUPERFICIE_MEDIANA,
                        muestra.superficie, UnidadPrecio.M2));
            }
        }
        return filas;
    }

    private static FilaPrecio fila(String codigoIne, Muestra muestra,
                                   IndicadorPrecio indicador, BigDecimal valor,
                                   UnidadPrecio unidad) {
        return new FilaPrecio(codigoIne, AmbitoMercado.MUNICIPIO, TipoPrecio.ALQUILER,
                indicador, TipologiaVivienda.COLECTIVA, muestra.anio,
                PrecioMercado.TRIMESTRE_ANUAL, valor, unidad, muestra.testigos, null);
    }

    private static Short enteroCorto(String texto) {
        try {
            return Short.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String texto) {
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Acumulador mutable de la muestra de UN municipio en su último año. */
    private static final class Muestra {
        private final short anio;
        private BigDecimal cuantia;
        private BigDecimal superficie;
        private Integer testigos;

        private Muestra(short anio) {
            this.anio = anio;
        }
    }
}
