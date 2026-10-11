package es.manti.mercado.application;

import es.manti.mercado.domain.AmbitoMercado;
import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import es.manti.mercado.domain.UnidadPrecio;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parser del CSV de valor tasado provincial del MIVAU (VDP006_01.csv, 689 KB).
 *
 * <p><b>Cabecera REAL</b> (verificada contra el fichero publicado, UTF-8 con
 * BOM, separador {@code ;}, decimal con punto):</p>
 * <pre>Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO</pre>
 *
 * <p>Reglas aplicadas:</p>
 * <ul>
 *   <li>{@code Valor} está en <b>€/m²</b>; vacío en algunas filas (p. ej. el
 *       consolidado histórico "Ceuta y Melilla") — esas filas se descartan.</li>
 *   <li>{@code CPRO} llega <b>sin rellenar a la izquierda</b> ({@code 1}..{@code 52})
 *       y como el literal {@code null} en la fila histórica de Ceuta y Melilla.
 *       Sólo se importan los valores numéricos: son las 48 provincias que el
 *       fichero publica a nivel provincial.</li>
 *   <li>Clave de provincia en el esquema: {@code CPRO} con dos dígitos + {@code 000}
 *       (notación INE de "total de provincia"), que no choca con ningún municipio.</li>
 *   <li>{@code Régimen} {@code Libre}/{@code Protegida} → tipología
 *       {@link TipologiaVivienda#LIBRE}/{@link TipologiaVivienda#PROTEGIDA};
 *       la respuesta pública prefiere siempre LIBRE.</li>
 *   <li>Se importan todos los años y trimestres (13 711 filas): sirven de
 *       histórico y el coste es despreciable.</li>
 * </ul>
 *
 * <p><b>Hueco documentado en la fuente:</b> el fichero NO incluye filas
 * provinciales para Madrid (28), Murcia (30), Navarra (31) ni Asturias (33) —
 * sólo aparecen como "Total CCAA" —, así que esos municipios quedan sin dato
 * de venta y la API devuelve {@code venta: null}.</p>
 */
@Component
public class ParserVentaProvincial {

    /** Cabecera exacta esperada (sin el BOM, que se elimina antes de comparar). */
    static final String CABECERA_ESPERADA =
            "Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO";

    private static final int COL_ANIO = 0;
    private static final int COL_TRIMESTRE = 1;
    private static final int COL_VALOR = 2;
    private static final int COL_REGIMEN = 3;
    private static final int COL_CPRO = 4;
    private static final int COL_PROVINCIA = 5;
    private static final int COLUMNAS = 8;

    private static final String REGIMEN_LIBRE = "Libre";
    private static final String REGIMEN_PROTEGIDA = "Protegida";

    private static final Pattern CPRO = Pattern.compile("\\d{1,2}");

    /**
     * @param lector CSV del MIVAU ya abierto en UTF-8
     * @return una fila por provincia, trimestre y régimen con valor publicado
     * @throws FuenteMivauNoDisponibleException si la cabecera no es la esperada o el fichero es ilegible
     */
    public List<FilaPrecio> parsear(Reader lector) {
        BufferedReader buffer = lector instanceof BufferedReader yaBufferizado
                ? yaBufferizado
                : new BufferedReader(lector);

        List<FilaPrecio> filas = new ArrayList<>();
        try {
            String cabecera = buffer.readLine();
            if (cabecera == null) {
                throw new FuenteMivauNoDisponibleException("El CSV de valor tasado del MIVAU está vacío");
            }
            String normalizada = cabecera.replace("\uFEFF", "");
            if (!CABECERA_ESPERADA.equals(normalizada)) {
                throw new FuenteMivauNoDisponibleException(
                        "Cabecera inesperada en el CSV de valor tasado del MIVAU: " + normalizada);
            }

            String linea;
            while ((linea = buffer.readLine()) != null) {
                FilaPrecio fila = interpretar(linea);
                if (fila != null) {
                    filas.add(fila);
                }
            }
        } catch (IOException e) {
            throw new FuenteMivauNoDisponibleException(
                    "No se ha podido leer el CSV de valor tasado del MIVAU", e);
        }
        return filas;
    }

    private FilaPrecio interpretar(String linea) {
        String[] columna = linea.split(";", -1);
        if (columna.length != COLUMNAS) {
            return null;
        }
        String cpro = columna[COL_CPRO];
        if (!CPRO.matcher(cpro).matches()) {
            return null;                  // "null" (Ceuta y Melilla) o fila de CCAA
        }
        TipologiaVivienda regimen = regimen(columna[COL_REGIMEN]);
        if (regimen == null) {
            return null;
        }
        Short anio = enteroCorto(columna[COL_ANIO]);
        Short trimestre = enteroCorto(columna[COL_TRIMESTRE]);
        BigDecimal valor = decimal(columna[COL_VALOR]);
        if (anio == null || trimestre == null || trimestre < 1 || trimestre > 4
                || valor == null) {
            return null;                  // valor vacío o fila incompleta
        }

        String codigoIne = String.format("%02d000", Integer.parseInt(cpro));
        return new FilaPrecio(codigoIne, AmbitoMercado.PROVINCIA, TipoPrecio.VENTA,
                IndicadorPrecio.VALOR_TASADO, regimen, anio, trimestre, valor,
                UnidadPrecio.EUR_M2, null, columna[COL_PROVINCIA]);
    }

    private static TipologiaVivienda regimen(String texto) {
        return switch (texto) {
            case REGIMEN_LIBRE -> TipologiaVivienda.LIBRE;
            case REGIMEN_PROTEGIDA -> TipologiaVivienda.PROTEGIDA;
            default -> null;
        };
    }

    private static Short enteroCorto(String texto) {
        try {
            return Short.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String texto) {
        if (texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
