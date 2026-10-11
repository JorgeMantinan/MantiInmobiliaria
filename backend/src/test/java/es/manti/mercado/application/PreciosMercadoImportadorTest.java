package es.manti.mercado.application;

import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.FuenteMivauPort;
import es.manti.mercado.domain.PrecioMercadoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringReader;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Importador completo (parser + upsert) contra PostgreSQL real, con una fuente
 * FALSA que sirve las dos cabeceras reales: el test jamás toca la red y el
 * perfil test no crea el runner de dev.
 */
@SpringBootTest
@ActiveProfiles("test")
class PreciosMercadoImportadorTest {

    private static final String CABECERA_ALQUILER =
            "COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR\n";

    /**
     * Códigos 96/97 a propósito: no existen en los CSV del MIVAU, de modo que
     * la importación real del perfil dev no contamina ni es borrada por el test.
     */
    private static final String CSV_ALQUILER = CABECERA_ALQUILER + """
            96;Provincia Test;96001;Municipio Test;PRECIO;COLECTIVA;MEDIANA;2024;868.8
            96;Provincia Test;96001;Municipio Test;SUPERFICIE;COLECTIVA;MEDIANA;2024;66
            96;Provincia Test;96001;Municipio Test;VIVIENDA;COLECTIVA;RECUENTO;2024;300447
            """;

    private static final String CABECERA_VENTA =
            "Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO\n";

    private static final String CSV_VENTA = CABECERA_VENTA + """
            2024;4;1588.7;Libre;96;Provincia Test;Comunidad Test;0
            2026;1;1700.2;Libre;97;Otra Provincia Test;Comunidad Test;0
            """;

    @Autowired
    private PrecioMercadoRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')");
    }

    @Test
    void importar_persisteAlquilerMunicipalYValorTasadoProvincial() {
        ResultadoImportacion resultado = importador(CSV_ALQUILER, CSV_VENTA).importar();

        assertThat(resultado.filasAlquiler()).isEqualTo(3);
        assertThat(resultado.filasVenta()).isEqualTo(2);
        assertThat(resultado.fechaDescarga()).isNotNull();
        assertThat(filasDeLosCodigosDePrueba()).isEqualTo(5);

        assertThat(valor("96001", "RENTA_MEDIANA")).isEqualByComparingTo(new BigDecimal("13.16"));
        assertThat(valor("96001", "CUANTIA_MEDIANA")).isEqualByComparingTo(new BigDecimal("868.8"));
        assertThat(valor("96000", "VALOR_TASADO")).isEqualByComparingTo(new BigDecimal("1588.7"));
        assertThat(jdbc.queryForObject(
                "SELECT n_testigos FROM precios_mercado WHERE codigo_ine = '96001' AND indicador = 'RENTA_MEDIANA'",
                Integer.class)).isEqualTo(300_447);
        assertThat(jdbc.queryForObject(
                "SELECT fuente FROM precios_mercado WHERE codigo_ine = '96000' AND indicador = 'VALOR_TASADO'",
                String.class)).isEqualTo("MIVAU");
    }

    @Test
    void importar_repetirActualizaLosValoresSinDuplicarFilas() {
        importador(CSV_ALQUILER, CSV_VENTA).importar();
        assertThat(filasDeLosCodigosDePrueba()).isEqualTo(5);

        // Misma clave natural, valores distintos: upsert, no duplicado.
        importador(CSV_ALQUILER.replace("868.8", "900"), CSV_VENTA).importar();

        assertThat(filasDeLosCodigosDePrueba()).isEqualTo(5);
        // 900 €/mes ÷ 66 m² = 13,6363… ⇒ 13,64
        assertThat(valor("96001", "RENTA_MEDIANA")).isEqualByComparingTo(new BigDecimal("13.64"));
        assertThat(valor("96001", "CUANTIA_MEDIANA")).isEqualByComparingTo(new BigDecimal("900"));
    }

    @Test
    void importar_cuandoLaFuenteFalla_propagaLaExcepcionDeDominio() {
        FuenteMivauPort caido = new FuenteMivauPort() {
            @Override
            public void conAlquilerMunicipal(ConsumidorCsv consumidor) {
                throw new FuenteMivauNoDisponibleException("El MIVAU ha respondido 503");
            }

            @Override
            public void conVentaProvincial(ConsumidorCsv consumidor) {
                throw new FuenteMivauNoDisponibleException("El MIVAU ha respondido 503");
            }
        };

        assertThatThrownBy(() -> new PreciosMercadoImportador(
                caido, repository, new ParserAlquilerMunicipal(), new ParserVentaProvincial()).importar())
                .isInstanceOf(FuenteMivauNoDisponibleException.class)
                .hasMessageContaining("503");

        assertThat(filasDeLosCodigosDePrueba()).isZero();
    }

    @Test
    void importar_conUnCsvDeEstructuraNueva_propagaLaExcepcionDeFormato() {
        String cabeceraNueva = "CPRO;Provincia;Valor\n28;Madrid;1000\n";

        assertThatThrownBy(() -> importador(cabeceraNueva, CSV_VENTA).importar())
                .isInstanceOf(FuenteMivauNoDisponibleException.class)
                .hasMessageContaining("Cabecera inesperada");
    }

    // ------------------------------------------------------------------

    private PreciosMercadoImportador importador(String alquiler, String venta) {
        return new PreciosMercadoImportador(fuenteFalsa(alquiler, venta), repository,
                new ParserAlquilerMunicipal(), new ParserVentaProvincial());
    }

    /** Doble de red que entrega los CSV literales como si el CDN los hubiera servido. */
    private static FuenteMivauPort fuenteFalsa(String alquiler, String venta) {
        return new FuenteMivauPort() {
            @Override
            public void conAlquilerMunicipal(ConsumidorCsv consumidor) {
                consumidor.procesar(new StringReader(alquiler));
            }

            @Override
            public void conVentaProvincial(ConsumidorCsv consumidor) {
                consumidor.procesar(new StringReader(venta));
            }
        };
    }

    private int filasDeLosCodigosDePrueba() {
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')",
                Integer.class);
        return filas == null ? 0 : filas;
    }

    private BigDecimal valor(String codigoIne, String indicador) {
        return jdbc.queryForObject(
                "SELECT valor FROM precios_mercado WHERE codigo_ine = ? AND indicador = ?",
                BigDecimal.class, codigoIne, indicador);
    }
}
