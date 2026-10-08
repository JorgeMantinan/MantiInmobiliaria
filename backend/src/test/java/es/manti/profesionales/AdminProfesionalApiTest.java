package es.manti.profesionales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backoffice de profesionales y solicitudes: 401 sin token, 403 sin rol ADMIN,
 * listado por estado, PATCH de estado (200/400/404) y el listado de las
 * últimas solicitudes. Los tokens salen del login real (nada de @WithMockUser):
 * se comprueba la cadena de seguridad de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProfesionalApiTest {

    private static final long ID_INEXISTENTE = 987_654_321L;
    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenAdmin;
    private String tokenNormal;

    @BeforeEach
    void crearUsuarioNormal() throws Exception {
        String email = "it-user-" + UUID.randomUUID() + "@example.com";
        registrarUsuario(email, "USER");
        tokenNormal = login(email);
    }

    @AfterEach
    void limpiarFixtures() {
        FixturesProfesionales.limpiar(jdbc);
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // Autorización
    // ------------------------------------------------------------------

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profesionales"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listar_comoUsuarioNormal_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profesionales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNormal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void cambiarEstado_sinToken_devuelve401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", ID_INEXISTENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarEstado_comoUsuarioNormal_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNormal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void listarSolicitudes_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/solicitudes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarSolicitudes_comoUsuarioNormal_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/solicitudes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNormal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    // ------------------------------------------------------------------
    // GET /api/v1/admin/profesionales
    // ------------------------------------------------------------------

    @Test
    void listar_comoAdminPorEstado_devuelveSoloLosDeEseEstado() throws Exception {
        String activo = "it-pro-activo-" + UUID.randomUUID() + "@example.com";
        String pendiente = "it-pro-pendiente-" + UUID.randomUUID() + "@example.com";
        FixturesProfesionales.profesional(jdbc, activo, "ACTIVO", "fontanero");
        FixturesProfesionales.profesional(jdbc, pendiente, "PENDIENTE", "albañil");

        String cuerpo = mockMvc.perform(get("/api/v1/admin/profesionales")
                        .param("estado", "PENDIENTE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> filas = JsonPath.read(cuerpo, "$");
        assertThat(filas).extracting(fila -> fila.get("email"))
                .contains(pendiente)
                .doesNotContain(activo);
        // Los gremios salen como slugs, resueltos en la propia query (sin N+1)
        Map<String, Object> fila = filas.stream()
                .filter(fila2 -> pendiente.equals(fila2.get("email")))
                .findFirst()
                .orElseThrow();
        assertThat(gremiosDe(fila)).containsExactly("albañil");
    }

    @Test
    void listar_comoAdmin_conEstadoInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/profesionales")
                        .param("estado", "INVENTADO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("estado"));
    }

    // ------------------------------------------------------------------
    // PATCH /api/v1/admin/profesionales/{id}
    // ------------------------------------------------------------------

    @Test
    void cambiarEstado_comoAdmin_devuelve200YActualizaElEstado() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-patch-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        String estado = jdbc.queryForObject("SELECT estado FROM profesionales WHERE id = ?",
                String.class, id);
        assertThat(estado).isEqualTo("ACTIVO");
    }

    @Test
    void cambiarEstado_inexistente_devuelve404ProblemDetail() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", ID_INEXISTENTE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"RECHAZADO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    @Test
    void cambiarEstado_noAdmitido_devuelve400ConErrors() throws Exception {
        long id = FixturesProfesionales.profesional(
                jdbc, "it-pro-estado-" + UUID.randomUUID() + "@example.com", "PENDIENTE", "fontanero");

        mockMvc.perform(patch("/api/v1/admin/profesionales/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"PENDIENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("estado"));

        String estado = jdbc.queryForObject("SELECT estado FROM profesionales WHERE id = ?",
                String.class, id);
        assertThat(estado).isEqualTo("PENDIENTE");
    }

    // ------------------------------------------------------------------
    // GET /api/v1/admin/solicitudes
    // ------------------------------------------------------------------

    @Test
    void listarSolicitudes_comoAdmin_devuelve200ConLasRecientes() throws Exception {
        long solicitud = FixturesProfesionales.solicitud(
                jdbc, "it-sol-admin-" + UUID.randomUUID() + "@example.com", "fontanero");

        String cuerpo = mockMvc.perform(get("/api/v1/admin/solicitudes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> filas = JsonPath.read(cuerpo, "$");
        Map<String, Object> fila = filas.stream()
                .filter(fila2 -> ((Number) fila2.get("id")).longValue() == solicitud)
                .findFirst()
                .orElseThrow();
        assertThat(fila.get("estado")).isEqualTo("NUEVA");
        assertThat(gremiosDe(fila)).containsExactly("fontanero");
        assertThat(fila.get("municipio")).isEqualTo("It-Municipio");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Los gremios del backoffice llegan como lista de slugs desde el mapper. */
    @SuppressWarnings("unchecked")
    private static List<String> gremiosDe(Map<String, Object> fila) {
        return (List<String>) fila.get("gremios");
    }

    private String tokenAdmin() throws Exception {
        if (tokenAdmin == null) {
            String email = "it-admin-" + UUID.randomUUID() + "@example.com";
            registrarUsuario(email, "ADMIN");
            tokenAdmin = login(email);
        }
        return tokenAdmin;
    }

    private void registrarUsuario(String email, String rol) {
        jdbc.update("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, ?, 'Usuario Test', ?, true)
                """, email, passwordEncoder.encode(PASSWORD), rol);
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
