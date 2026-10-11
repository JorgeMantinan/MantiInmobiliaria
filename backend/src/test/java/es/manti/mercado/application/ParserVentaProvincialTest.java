package es.manti.mercado.application;

import es.manti.mercado.domain.AmbitoMercado;
import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.FormatoCsvInvalidoException;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import es.manti.mercado.domain.UnidadPrecio;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser del CSV de valor tasado provincial con fixtures literales: la
 * cabecera es EXACTAMENTE la del fichero real del CDN del MIVAU y las filas
 * reproducen sus casos límite (CPRO sin rellenar, literal {@code null},
 * valor vacío y fila de comunidad autónoma).
 */
class ParserVentaProvincialTest {

    /** Cabecera real de VDP006_01.csv (UTF-8 con BOM, separador ";"). */
    private static final String CABECERA =
            "Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO\n";

    private static final String MUESTRAS = CABECERA + """
            2024;4;1588.7;Libre;50;Zaragoza;Aragón;2
            2024;4;;Protegida;50;Zaragoza;Aragón;2
            1995;1;;Libre;null;Ceuta y Melilla;Ceuta y Melilla;0
            2024;4;3370.5;Libre;;Total CCAA;Madrid, Comunidad de;13
            2026;1;1700.2;Libre;08;Barcelona;Cataluña;9
            2026;1;1150.0;Protegida;08;Barcelona;Cataluña;9
            """;

    private final ParserVentaProvincial parser = new ParserVentaProvincial();

    @Test
    void parsear_conCabeceraReal_importaSóloLasFilasProvincialesConValor() {
        List<FilaPrecio> filas = parser.parsear(new StringReader(MUESTRAS));

        // Se descartan: Protegida sin valor, "null" de Ceuta y Melilla y el Total CCAA.
        assertThat(filas).hasSize(3);
        assertThat(filas).allSatisfy(fila -> {
            assertThat(fila.ambito()).isEqualTo(AmbitoMercado.PROVINCIA);
            assertThat(fila.tipo()).isEqualTo(TipoPrecio.VENTA);
            assertThat(fila.indicador()).isEqualTo(IndicadorPrecio.VALOR_TASADO);
            assertThat(fila.unidad()).isEqualTo(UnidadPrecio.EUR_M2);
            assertThat(fila.nTestigos()).isNull();
            assertThat(fila.provincia()).isNotBlank();
        });
    }

    @Test
    void parsear_rellenaElCodigoIneDeLaProvinciaConDosDigitosMas000() {
        List<FilaPrecio> filas = parser.parsear(new StringReader(MUESTRAS));

        // CPRO llega sin rellenar a la izquierda: "08" y "50" ⇒ "08000"/"50000".
        assertThat(filas).extracting(FilaPrecio::codigoIne)
                .containsExactly("50000", "08000", "08000");
    }

    @Test
    void parsear_elRegimenSeConvierteEnTipologia() {
        List<FilaPrecio> filas = parser.parsear(new StringReader(MUESTRAS));

        assertThat(filas).extracting(FilaPrecio::tipologia)
                .containsExactly(TipologiaVivienda.LIBRE, TipologiaVivienda.LIBRE,
                        TipologiaVivienda.PROTEGIDA);
    }

    @Test
    void parsear_conBomDelanteDeLaCabecera_loElimina() {
        assertThat(parser.parsear(new StringReader("\uFEFF" + MUESTRAS))).hasSize(3);
    }

    @Test
    void parsear_conCabeceraDistinta_lanzaFormatoInvalido() {
        String csv = "COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR\n";

        assertThatThrownBy(() -> parser.parsear(new StringReader(csv)))
                .isInstanceOf(FormatoCsvInvalidoException.class)
                .hasMessageContaining("Cabecera inesperada")
                .hasMessageContaining("COD_PROVINCIA");
    }

    @Test
    void parsear_conUnaProvinciaMasLargaQueLaColumna_descartaLaFila() {
        String provinciaLarga = "x".repeat(ParserVentaProvincial.LARGO_MAXIMO_PROVINCIA + 1);
        String csv = CABECERA + """
                2026;1;1000;Libre;28;%s;Comunidad Test;13
                2026;1;2000;Libre;29;Málaga;Andalucía;1
                """.formatted(provinciaLarga);

        List<FilaPrecio> filas = parser.parsear(new StringReader(csv));

        // La fila corrupta no tumba la importación entera: se descarta y sigue.
        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().provincia()).isEqualTo("Málaga");
    }

    @Test
    void parsear_conMasDeCienMilFilas_lanzaFuenteNoDisponible() {
        String lineaValida = "2026;1;1000;Libre;28;Madrid;Madrid, Comunidad de;13\n";
        StringBuilder csv = new StringBuilder(CABECERA);
        for (int i = 0; i <= ParserVentaProvincial.LIMITE_FILAS; i++) {
            csv.append(lineaValida);
        }

        assertThatThrownBy(() -> parser.parsear(new StringReader(csv.toString())))
                .isInstanceOf(FuenteMivauNoDisponibleException.class)
                .hasMessageContaining("supera las 100000 filas");
    }

    @Test
    void parsear_trimestreFueraDeRango_seDescarta() {
        String csv = CABECERA + """
                2026;0;1000;Libre;28;Madrid;Madrid, Comunidad de;13
                2026;5;1000;Libre;28;Madrid;Madrid, Comunidad de;13
                2026;2;1000;Libre;28;Madrid;Madrid, Comunidad de;13
                """;

        List<FilaPrecio> filas = parser.parsear(new StringReader(csv));

        assertThat(filas).hasSize(1);
        assertThat(filas.getFirst().trimestre()).isEqualTo((short) 2);
        assertThat(filas.getFirst().codigoIne()).isEqualTo("28000");
    }
}
