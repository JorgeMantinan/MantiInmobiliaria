package es.manti.mercado;

import com.jayway.jsonpath.JsonPath;
import es.manti.mercado.application.CandadoImportacion;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.FuenteMivauPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.StringReader;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backoffice de precios: 401 anónimo, 403 sin rol y 200 con ADMIN.
 *
 * <p>El puerto de descarga se SUSTITUYE por un doble ({@code @MockitoBean}): el
 * endpoint admin es la única llamada de red del sistema y ningún test puede
 * depender del CDN del Ministerio. El doble entrega las cabeceras reales de los
 * dos CSV.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMercadoApiTest {

    private static final String PASSWORD = "secreto123";

    private static final String CSV_ALQUILER = """
            COD_PROVINCIA;PROVINCIA;COD_POSTAL;NOMBRE_MUNICIPIO;ELEMENTO;TIPO_VIVIENDA;TIPO_MEDIDA;AÑO;VALOR
            96;Provincia Test;96001;Municipio Test;PRECIO;COLECTIVA;MEDIANA;2024;868.8
            96;Provincia Test;96001;Municipio Test;SUPERFICIE;COLECTIVA;MEDIANA;2024;66
            96;Provincia Test;96001;Municipio Test;VIVIENDA;COLECTIVA;RECUENTO;2024;300447
            """;

    private static final String CSV_VENTA = """
            Año;Trimestre;Valor;Régimen;CPRO;Provincia;Comunidad_Autónoma;CODAUTO
            2024;4;1588.7;Libre;96;Provincia Test;Comunidad Test;0
            2026;1;1700.2;Libre;97;Otra Provincia Test;Comunidad Test;0
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Sustituye a MivauRestAdapter: sin él la suite tocaría la red del Estado. */
    @MockitoBean
    private FuenteMivauPort fuente;

    /** Mismo candado que usa el controller; el test lo toma a mano (sin carreras). */
    @Autowired
    private CandadoImportacion candado;

    private String tokenAdmin;

    @BeforeEach
    void configurarFuenteFalsa() {
        Mockito.reset(fuente);
        servir(CSV_ALQUILER, CSV_VENTA);
    }

    @AfterEach
    void limpiarFixtures() {
        // Por si un test se queda a medias con el candado tomado.
        while (candado.ocupado()) {
            candado.salir();
        }
        jdbc.update("DELETE FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')");
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-admin-%@example.com'");
    }

    @Test
    void actualizar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/mercado/actualizar"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void actualizar_comoUsuarioNormal_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUsuario()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void actualizar_comoAdmin_devuelve200ConElRecuentoDeFilas() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filasAlquiler").value(3))
                .andExpect(jsonPath("$.filasVenta").value(2))
                .andExpect(jsonPath("$.fechaDescarga").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(cuerpo, "$.fechaDescarga")).isNotBlank();
        // La reimportación escribe de verdad: 3 + 2 filas de los códigos de prueba.
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')",
                Integer.class);
        assertThat(filas).isEqualTo(5);
    }

    @Test
    void actualizar_repetirNoDuplicaFilas() throws Exception {
        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filasAlquiler").value(3))
                .andExpect(jsonPath("$.filasVenta").value(2));

        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')",
                Integer.class);
        assertThat(filas).isEqualTo(5);
    }

    @Test
    void actualizar_cuandoElMivauFalla_devuelve500ProblemDetail() throws Exception {
        doThrow(new FuenteMivauNoDisponibleException("El MIVAU ha respondido 503"))
                .when(fuente).conAlquilerMunicipal(any());

        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Fuente no disponible"))
                .andExpect(jsonPath("$.detail").value("El MIVAU ha respondido 503"));

        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')",
                Integer.class);
        assertThat(filas).isZero();
    }

    @Test
    void actualizar_conOtraImportacionEnCurso_devuelve409() throws Exception {
        // Simula el "otro" ADMIN que ya está importando: toma el candado a mano.
        assertThat(candado.intentarEntrar()).isTrue();

        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Importación en curso"))
                .andExpect(jsonPath("$.detail").value(
                        "Ya hay una importación de precios del MIVAU en curso; inténtelo de nuevo en unos segundos"));

        // El endpoint no ha pisado el candado ajeno ni ha escrito nada.
        assertThat(candado.ocupado()).isTrue();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM precios_mercado WHERE codigo_ine IN ('96001', '96000', '97000')",
                Integer.class);
        assertThat(filas).isZero();
    }

    @Test
    void actualizar_conUnCsvDeEstructuraNueva_devuelve500ConDetailFijo() throws Exception {
        servir("CPRO;Provincia;Valor\n28;Madrid;1000\n", CSV_VENTA);

        mockMvc.perform(post("/api/v1/admin/mercado/actualizar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Fuente no disponible"))
                // El detail es FIJO: la cabecera real del MIVAU sólo va al log.
                .andExpect(jsonPath("$.detail")
                        .value("El CSV del MIVAU tiene una estructura inesperada"));
    }

    // ------------------------------------------------------------------

    /** Hace que el doble entregue los dos CSV a los parsers reales. */
    private void servir(String alquiler, String venta) {
        doAnswer(invocation -> {
            invocation.<FuenteMivauPort.ConsumidorCsv>getArgument(0)
                    .procesar(new StringReader(alquiler));
            return null;
        }).when(fuente).conAlquilerMunicipal(any());

        doAnswer(invocation -> {
            invocation.<FuenteMivauPort.ConsumidorCsv>getArgument(0)
                    .procesar(new StringReader(venta));
            return null;
        }).when(fuente).conVentaProvincial(any());
    }

    /** Usuario ADMIN con fixtures propios (prefijo {@code it-admin-}), una vez por clase. */
    private String tokenAdmin() throws Exception {
        if (tokenAdmin == null) {
            String email = "it-admin-" + UUID.randomUUID() + "@example.com";
            jdbc.update("""
                    INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                    VALUES (?, ?, 'Admin Mercado Test', 'ADMIN', true)
                    """, email, passwordEncoder.encode(PASSWORD));
            tokenAdmin = login(email);
        }
        return tokenAdmin;
    }

    private String tokenUsuario() throws Exception {
        String email = "it-user-" + UUID.randomUUID() + "@example.com";
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String login(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }
}
