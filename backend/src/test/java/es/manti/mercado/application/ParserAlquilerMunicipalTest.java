package es.manti.mercado.application;

import es.manti.mercado.domain.AmbitoMercado;
import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import es.manti.mercado.domain.UnidadPrecio;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser del CSV de alquiler municipal con <b>fixtures literales</b>: la
 * cabecera es EXACTAMENTE la del fichero real publicado en el CDN del MIVAU
 * (verificada con {@code curl ... | head -5}) y las filas de Madrid y
 * Roquetas son las que trae ese fichero. Ningún test toca la red.
 */
class ParserAlquilerMunicipalTest {

    /** Cabecera real de VDP001_01.csv (UTF-8, separador ";"). */
    private static final String CABECERA =
            "COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR\n";

    /** Filas reales: Madrid 2023/2024, percentiles y vivienda unifamiliar que NO se importan. */
    private static final String MADRID_Y_BARCELONA = CABECERA + """
            28;Madrid;28079;Madrid;PRECIO;COLECTIVA;MEDIANA;2023;800
            28;Madrid;28079;Madrid;PRECIO;COLECTIVA;MEDIANA;2024;868.8
            28;Madrid;28079;Madrid;SUPERFICIE;COLECTIVA;MEDIANA;2024;66
            28;Madrid;28079;Madrid;VIVIENDA;COLECTIVA;RECUENTO;2024;300447
            28;Madrid;28079;Madrid;PRECIO;COLECTIVA;PERCENT25;2024;700
            28;Madrid;28079;Madrid;PRECIO;UNIFAMILIAR;MEDIANA;2024;1272.8
            08;Barcelona;08019;Barcelona;PRECIO;COLECTIVA;MEDIANA;2024;900
            08;Barcelona;08019;Barcelona;SUPERFICIE;COLECTIVA;MEDIANA;2024;68
            """;

    private final ParserAlquilerMunicipal parser = new ParserAlquilerMunicipal();

    @Test
    void parsear_conCabeceraReal_devuelveSóloElÚltimoAñoDeCadaMunicipio() {
        List<FilaPrecio> filas = parser.parsear(new StringReader(MADRID_Y_BARCELONA));

        // Madrid: 3 indicadores de 2024 (2023 se descarta). Barcelona: sin RECUENTO ⇒ 3.
        assertThat(filas).hasSize(6);
        assertThat(filas).allSatisfy(fila -> {
            assertThat(fila.ambito()).isEqualTo(AmbitoMercado.MUNICIPIO);
            assertThat(fila.tipo()).isEqualTo(TipoPrecio.ALQUILER);
            assertThat(fila.tipologia()).isEqualTo(TipologiaVivienda.COLECTIVA);
            assertThat(fila.trimestre()).isZero();          // dato anual
            assertThat(fila.provincia()).isNull();
        });
        assertThat(filas).extracting(FilaPrecio::codigoIne).containsOnly("28079", "08019");
        assertThat(filas).extracting(FilaPrecio::anio).containsOnly((short) 2024);
    }

    @Test
    void parsear_derivaLaRentaMedianaACpartirDeCuantiaYSuperficie() {
        Map<IndicadorPrecio, FilaPrecio> madrid = porIndicador("28079");

        // 868,8 €/mes ÷ 66 m² = 13,1636… ⇒ 13,16 €/m²·mes (2 decimales)
        assertThat(madrid.get(IndicadorPrecio.RENTA_MEDIANA).valor())
                .isEqualByComparingTo(new BigDecimal("13.16"));
        assertThat(madrid.get(IndicadorPrecio.RENTA_MEDIANA).unidad())
                .isEqualTo(UnidadPrecio.EUR_M2_MES);

        assertThat(madrid.get(IndicadorPrecio.CUANTIA_MEDIANA).valor())
                .isEqualByComparingTo(new BigDecimal("868.8"));
        assertThat(madrid.get(IndicadorPrecio.CUANTIA_MEDIANA).unidad())
                .isEqualTo(UnidadPrecio.EUR_MES);

        assertThat(madrid.get(IndicadorPrecio.SUPERFICIE_MEDIANA).valor())
                .isEqualByComparingTo(new BigDecimal("66"));
        assertThat(madrid.get(IndicadorPrecio.SUPERFICIE_MEDIANA).unidad())
                .isEqualTo(UnidadPrecio.M2);
    }

    @Test
    void parsear_losTresIndicadoresCompartenElNDeTestigosDelRecuento() {
        Map<IndicadorPrecio, FilaPrecio> madrid = porIndicador("28079");

        assertThat(madrid.values())
                .extracting(FilaPrecio::nTestigos)
                .containsOnly(300_447);

        // Barcelona no tiene fila de RECUENTO en la muestra: el campo queda null.
        assertThat(porIndicador("08019").values())
                .extracting(FilaPrecio::nTestigos)
                .containsOnlyNulls();
    }

    @Test
    void parsear_municipioSinSuperficie_sóloPublicaLaCuantia() {
        String csv = CABECERA + """
                47;Valladolid;47193;Viana de Cega;PRECIO;COLECTIVA;MEDIANA;2024;450
                """;

        List<FilaPrecio> filas = parser.parsear(new StringReader(csv));

        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().indicador()).isEqualTo(IndicadorPrecio.CUANTIA_MEDIANA);
    }

    @Test
    void parsear_conBomDelanteDeLaCabecera_loElimina() {
        String csv = "\uFEFF" + MADRID_Y_BARCELONA;

        assertThat(parser.parsear(new StringReader(csv))).hasSize(6);
    }

    @Test
    void parsear_conCabeceraDistinta_lanzaFuenteNoDisponible() {
        String csv = "Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO\n2024;4;1;Libre;28;Madrid;x;13\n";

        assertThatThrownBy(() -> parser.parsear(new StringReader(csv)))
                .isInstanceOf(FuenteMivauNoDisponibleException.class)
                .hasMessageContaining("Cabecera inesperada");
    }

    @Test
    void parsear_conFicheroVacío_lanzaFuenteNoDisponible() {
        assertThatThrownBy(() -> parser.parsear(new StringReader("")))
                .isInstanceOf(FuenteMivauNoDisponibleException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void parsear_filasCorruptasOMalformadas_seIgnoranSinRomper() {
        String csv = CABECERA + """
                fila;que;no;tiene;las;columnas;suficientes
                28;Madrid;XXXXX;Madrid;PRECIO;COLECTIVA;MEDIANA;2024;868.8
                28;Madrid;28079;Madrid;PRECIO;COLECTIVA;MEDIANA;no-anio;868.8
                28;Madrid;28079;Madrid;PRECIO;COLECTIVA;MEDIANA;2024;no-numero
                28;Madrid;28079;Madrid;PRECIO;COLECTIVA;MEDIANA;2024;868.8
                """;

        List<FilaPrecio> filas = parser.parsear(new StringReader(csv));

        assertThat(filas).extracting(FilaPrecio::indicador)
                .containsExactly(IndicadorPrecio.CUANTIA_MEDIANA);
    }

    // ------------------------------------------------------------------

    private Map<IndicadorPrecio, FilaPrecio> porIndicador(String codigoIne) {
        return parser.parsear(new StringReader(MADRID_Y_BARCELONA)).stream()
                .filter(fila -> fila.codigoIne().equals(codigoIne))
                .collect(Collectors.toMap(FilaPrecio::indicador, Function.identity()));
    }
}
