package es.manti.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #32 — ETag en los GET públicos ({@link PublicGetEtagFilter}).
 *
 * <p>Comprueba las dos mitades del contrato: el listado responde con cabecera
 * {@code ETag} y devuelve {@code 304} con body vacío cuando el cliente
 * revalida con {@code If-None-Match}; y las rutas FUERA del alcance (auth)
 * no llevan ETag.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicGetEtagFilterTest {

    /** Código INE sintético (rango 96xxx fuera de los CSV reales). */
    private static final String INE_TEST = "96001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM precios_mercado WHERE codigo_ine = ?", INE_TEST);
    }

    @Test
    void listadoPublico_devuelveEtag_yResponde304AlRevalidar() throws Exception {
        MvcResult primera = mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn();
        String etag = primera.getResponse().getHeader("ETag");
        assertThat(etag).as("ETag débil generado a partir del body").isNotBlank();

        // Revalidación condicional: mismo contenido → 304 y SIN cuerpo, que es
        // donde se ahorra el ancho de banda.
        mockMvc.perform(get("/api/v1/inmuebles").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(content().string(""));
    }

    @Test
    void catalogoGremios_devuelveEtag() throws Exception {
        mockMvc.perform(get("/api/v1/gremios"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void mercado_devuelveEtag() throws Exception {
        // El ETag sólo se genera en respuestas 200 (criterio de
        // ShallowEtagHeaderFilter), así que el test siembra un precio mínimo.
        jdbc.update("""
                INSERT INTO precios_mercado
                    (codigo_ine, ambito, tipo, indicador, tipologia, anio, trimestre,
                     valor, unidad, n_testigos, provincia)
                VALUES (?, 'MUNICIPIO', 'ALQUILER', 'RENTA_MEDIANA', 'COLECTIVA', 2024, 4,
                        10.5, 'EUR_M2_MES', 100, NULL)
                """, INE_TEST);

        mockMvc.perform(get("/api/v1/mercado").queryParam("codigoINE", INE_TEST))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    /**
     * El detalle se limita al patrón de dígitos: un hipotético endpoint bajo
     * el mismo prefijo pero no numérico no hereda el buffer del ETag.
     */
    @Test
    void detalleNoNumerico_noLlevaEtag() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles/export"))
                .andExpect(header().doesNotExist("ETag"));
    }

    /** El alcance es mínimo: rutas de auth y cualquier GET no listado, sin ETag. */
    @Test
    void rutasFueraDeAlcance_noLlevanEtag() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
    }
}
