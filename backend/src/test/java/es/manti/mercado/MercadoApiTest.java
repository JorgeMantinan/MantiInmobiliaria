package es.manti.mercado;

import es.manti.mercado.domain.AmbitoMercado;
import es.manti.mercado.domain.FilaPrecio;
import es.manti.mercado.domain.IndicadorPrecio;
import es.manti.mercado.domain.PrecioMercado;
import es.manti.mercado.domain.PrecioMercadoRepository;
import es.manti.mercado.domain.TipologiaVivienda;
import es.manti.mercado.domain.TipoPrecio;
import es.manti.mercado.domain.UnidadPrecio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública de precios de zona con PostgreSQL real: 200 con ambos bloques,
 * bloques parciales, 404 sin datos, 400 por código mal formado y la
 * atribución legal obligatoria. Todas las llamadas van SIN token a propósito
 * (el recurso es público desde la SecurityConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MercadoApiTest {

    /**
     * Códigos de PRUEBA sintéticos a propósito: los 5 dígitos "96"/"97" no
     * existen en los CSV del MIVAU, así que los tests no pueden chocar (ni
     * borrar por accidente) con los precios reales que el perfil dev importa.
     */
    private static final String CON_DATOS = "96001";

    /** El valor tasado se publica por PROVINCIA: "96" + "000" (notación INE). */
    private static final String PROVINCIA_DE_CON_DATOS = "96000";

    /** Otro municipio de una provincia sin dato tasado publicado. */
    private static final String ALQUILER_SIN_VENTA = "97001";

    /** Código INE inexistente: ni alquiler ni venta. */
    private static final String SIN_DATOS = "99999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrecioMercadoRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97001', '99999')");
    }

    @Test
    void consultar_conAlquilerYVenta_devuelve200ConAtribucionLegal() throws Exception {
        sembrarAlquilerYVenta();

        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", CON_DATOS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        allOf(containsString("public"), containsString("max-age=3600"))))
                .andExpect(jsonPath("$.codigoINE").value("96001"))
                // El año raíz es el del ALQUILER aunque la venta sea de 2026.
                .andExpect(jsonPath("$.anio").value(2024))
                .andExpect(jsonPath("$.fuente").value("Ministerio de Vivienda y Agenda Urbana"))
                .andExpect(jsonPath("$.alquiler.rentaMedianaEurM2Mes").value(13.24))
                .andExpect(jsonPath("$.alquiler.cuantiaMedianaEurMes").value(900.0))
                // El año no forma parte del contrato del bloque de alquiler.
                .andExpect(jsonPath("$.alquiler.anio").doesNotExist())
                .andExpect(jsonPath("$.venta.valorEurM2").value(2766.9))
                .andExpect(jsonPath("$.venta.ambito").value("PROVINCIA"))
                .andExpect(jsonPath("$.venta.provincia").value("Provincia Test"))
                .andExpect(jsonPath("$.venta.anio").value(2026))
                .andExpect(jsonPath("$.venta.trimestre").value(1));
    }

    @Test
    void consultar_preferidoElRegimenLibreSobreElProtegido() throws Exception {
        sembrarAlquilerYVenta();

        // El último periodo tiene LIBRE (2766,9) y PROTEGIDA (1200,9): manda LIBRE.
        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", CON_DATOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venta.valorEurM2").value(2766.9));
    }

    @Test
    void consultar_soloConAlquiler_devuelveLaVentaNula() throws Exception {
        repository.guardarOActualizar(List.of(alquiler(CON_DATOS, IndicadorPrecio.RENTA_MEDIANA,
                "14.10", UnidadPrecio.EUR_M2_MES, (short) 2024)), Instant.now());

        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", CON_DATOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alquiler.rentaMedianaEurM2Mes").value(14.10))
                .andExpect(jsonPath("$.venta").value(nullValue()));
    }

    @Test
    void consultar_soloConVenta_devuelveElAlquilerNuloYElAnioDeLaVenta() throws Exception {
        repository.guardarOActualizar(List.of(
                venta(PROVINCIA_DE_CON_DATOS, TipologiaVivienda.LIBRE, "1588.7",
                        (short) 2025, (short) 3, "Provincia Test")),
                Instant.now());

        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", CON_DATOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anio").value(2025))
                .andExpect(jsonPath("$.alquiler").value(nullValue()))
                .andExpect(jsonPath("$.venta.valorEurM2").value(1588.7));
    }

    /**
     * La venta se resuelve por PREFIJO de provincia ("97" + "000"): si esa
     * provincia no tiene filas en VDP006, el bloque entero queda a null aunque
     * el municipio sí tenga alquiler. Es lo que le pasa a Madrid (28),
     * Murcia (30), Navarra (31) y Asturias (33) en el fichero real.
     */
    @Test
    void consultar_municipioDeUnaProvinciaSinDatoTasado_devuelveLaVentaNula() throws Exception {
        repository.guardarOActualizar(List.of(
                alquiler(ALQUILER_SIN_VENTA, IndicadorPrecio.RENTA_MEDIANA, "15.00",
                        UnidadPrecio.EUR_M2_MES, (short) 2024)),
                Instant.now());

        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", ALQUILER_SIN_VENTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alquiler.rentaMedianaEurM2Mes").value(15.00))
                .andExpect(jsonPath("$.venta").value(nullValue()));
    }

    @Test
    void consultar_sinDatosPublicados_devuelve404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", SIN_DATOS))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value(
                        "No hay precios de mercado publicados para el municipio " + SIN_DATOS));
    }

    @Test
    void consultar_conCodigoINEInvalido_devuelve400ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", "2807"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"));
    }

    @Test
    void consultar_conCodigoINENoNumerico_devuelve400ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/mercado").param("codigoINE", "Madrid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }

    @Test
    void consultar_sinParametro_devuelve400ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/mercado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro obligatorio ausente"))
                .andExpect(jsonPath("$.errors[0].field").value("codigoINE"));
    }

    // ------------------------------------------------------------------

    private void sembrarAlquilerYVenta() {
        repository.guardarOActualizar(List.of(
                alquiler(CON_DATOS, IndicadorPrecio.RENTA_MEDIANA, "13.24", UnidadPrecio.EUR_M2_MES, (short) 2024),
                alquiler(CON_DATOS, IndicadorPrecio.CUANTIA_MEDIANA, "900.00", UnidadPrecio.EUR_MES, (short) 2024),
                alquiler(CON_DATOS, IndicadorPrecio.SUPERFICIE_MEDIANA, "68.00", UnidadPrecio.M2, (short) 2024),
                // La venta vive bajo el código de PROVINCIA. Trimestres anteriores:
                // sólo el más reciente debe salir en la API.
                venta(PROVINCIA_DE_CON_DATOS, TipologiaVivienda.LIBRE, "2500.00", (short) 2024, (short) 4, "Provincia Test"),
                venta(PROVINCIA_DE_CON_DATOS, TipologiaVivienda.LIBRE, "2766.90", (short) 2026, (short) 1, "Provincia Test"),
                venta(PROVINCIA_DE_CON_DATOS, TipologiaVivienda.PROTEGIDA, "1200.90", (short) 2026, (short) 1, "Provincia Test")),
                Instant.now());
    }

    private static FilaPrecio alquiler(String codigoIne, IndicadorPrecio indicador,
                                       String valor, UnidadPrecio unidad, short anio) {
        return new FilaPrecio(codigoIne, AmbitoMercado.MUNICIPIO, TipoPrecio.ALQUILER, indicador,
                TipologiaVivienda.COLECTIVA, anio, PrecioMercado.TRIMESTRE_ANUAL,
                new BigDecimal(valor), unidad, 1000, null);
    }

    private static FilaPrecio venta(String codigoIne, TipologiaVivienda regimen,
                                    String valor, short anio, short trimestre, String provincia) {
        return new FilaPrecio(codigoIne, AmbitoMercado.PROVINCIA, TipoPrecio.VENTA,
                IndicadorPrecio.VALOR_TASADO, regimen, anio, trimestre,
                new BigDecimal(valor), UnidadPrecio.EUR_M2, null, provincia);
    }
}
