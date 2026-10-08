package es.manti.profesionales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro PÚBLICO de profesionales: 201 con estado PENDIENTE, 409 si el email
 * ya existe y validación de los gremios (1..8 y existentes).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfesionalRegistroApiTest {

    private static final String RUTA = "/api/v1/profesionales/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM profesionales WHERE email LIKE 'it-pro-%@example.com'");
    }

    @Test
    void registrar_profesionalValido_devuelve201PendienteYSusGremios() throws Exception {
        MvcResult resultado = mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("it-pro-uno@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long id = ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
        List<String> gremios = jdbc.queryForList(
                "SELECT g.slug FROM profesional_gremio pg JOIN gremios g ON g.id = pg.gremio_id "
                        + "WHERE pg.profesional_id = ? ORDER BY g.slug", String.class, id);
        assertThat(gremios).containsExactly("albañil", "fontanero");
    }

    @Test
    void registrar_emailYaRegistrado_devuelve409ProblemDetail() throws Exception {
        String email = "it-pro-dup-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(email)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/email-ya-registrado"));
    }

    @Test
    void registrar_sinGremios_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("it-pro-sin@example.com").replaceFirst(
                                "\"gremios\": \\[[^]]*]", "\"gremios\": []")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("gremios"));
    }

    @Test
    void registrar_conMasDeOchoGremios_devuelve400ConErrors() throws Exception {
        String gremios = "[\"fontanero\",\"albañil\",\"electricista\",\"carpintero\",\"maderista\","
                + "\"soldador\",\"pintor\",\"jardinero\",\"cerrajero\"]";

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("it-pro-nueve@example.com").replaceFirst(
                                "\"gremios\": \\[[^]]*]", "\"gremios\": " + gremios)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("gremios"));
    }

    @Test
    void registrar_conGremioInexistente_devuelve400GremioInexistente() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("it-pro-gremio@example.com").replaceFirst(
                                "\"gremios\": \\[[^]]*]", "\"gremios\": [\"astronauta\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/gremio-inexistente"))
                .andExpect(jsonPath("$.errors[0].field").value("gremios"));
    }

    @Test
    void registrar_emailInvalido_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("it-pro-mal@example.com").replaceFirst(
                                "\"email\": \"[^\"]+\"", "\"email\": \"sin-arroba\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String cuerpo(String email) {
        return """
                {
                  "nombre": "Fontaneria It",
                  "email": "%s",
                  "telefono": "611222333",
                  "municipio": "It-Alcalá",
                  "gremios": ["fontanero", "albañil"]
                }
                """.formatted(email);
    }
}
