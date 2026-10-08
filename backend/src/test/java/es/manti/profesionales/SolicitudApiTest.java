package es.manti.profesionales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API PÚBLICA de solicitudes y catálogo de gremios: 201 del alta, validación
 * campo a campo (400 con {@code errors[]}), gremios inexistentes y caché del
 * catálogo. Ninguna ruta necesita token (permitAll en SecurityConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolicitudApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // POST /api/v1/solicitudes
    // ------------------------------------------------------------------

    @Test
    void crear_solicitudValida_devuelve201ConEstadoNueva() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("NUEVA"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.creadoEn").isString());

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com'",
                Integer.class);
        assertThat(total).isGreaterThanOrEqualTo(1);
    }

    @Test
    void crear_sinNombreContacto_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst(
                                "\"nombreContacto\": \"[^\"]+\"", "\"nombreContacto\": \"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors[0].field").value("nombreContacto"));
    }

    @Test
    void crear_conEmailInvalido_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst(
                                "\"emailContacto\": \"[^\"]+\"", "\"emailContacto\": \"no-es-un-email\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("emailContacto"));
    }

    @Test
    void crear_conMunicipioDe121Caracteres_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst(
                                "\"municipio\": \"[^\"]+\"", "\"municipio\": \"" + "M".repeat(121) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("municipio"));
    }

    @Test
    void crear_conDescripcionDeMenosDeDiezCaracteres_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst(
                                "\"descripcion\": \"[^\"]+\"", "\"descripcion\": \"corta\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("descripcion"));
    }

    @Test
    void crear_sinGremios_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst("\"gremios\": \\[[^]]*]", "\"gremios\": []")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("gremios"));
    }

    @Test
    void crear_conMasDeOchoGremios_devuelve400ConErrors() throws Exception {
        String gremios = "[\"fontanero\",\"albañil\",\"electricista\",\"carpintero\",\"maderista\","
                + "\"soldador\",\"pintor\",\"jardinero\",\"cerrajero\"]";

        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst("\"gremios\": \\[[^]]*]",
                                "\"gremios\": " + gremios)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("gremios"));
    }

    @Test
    void crear_conGremioInexistente_devuelve400GremioInexistente() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst("\"gremios\": \\[[^]]*]",
                                "\"gremios\": [\"fontanero\",\"pirata-certificado\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.manti.dev/problems/gremio-inexistente"))
                .andExpect(jsonPath("$.errors[0].field").value("gremios"))
                .andExpect(jsonPath("$.detail").value(containsString("pirata-certificado")));
    }

    @Test
    void crear_gremioSinEnye_tambienResuelveAlAlbanil() throws Exception {
        // El contrato de slugs admite el mismo oficio con y sin ñ
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replaceFirst("\"gremios\": \\[[^]]*]",
                                "\"gremios\": [\"albanil\"]")))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // GET /api/v1/gremios
    // ------------------------------------------------------------------

    @Test
    void listarGremios_devuelveElCatalogoConCach60Segundos() throws Exception {
        mockMvc.perform(get("/api/v1/gremios"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].slug", hasItem("fontanero")))
                .andExpect(jsonPath("$[*].slug", hasItem("albañil")))
                .andExpect(jsonPath("$[*].nombre", hasItem("Albañil")))
                .andExpect(jsonPath("$[*].slug", hasSize(12)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String cuerpoValido() {
        String fijo = UUID.randomUUID().toString().substring(0, 8);
        return """
                {
                  "nombreContacto": "Usuario It",
                  "emailContacto": "it-sol-%s@example.com",
                  "telefono": "600000000",
                  "municipio": "It-%s",
                  "descripcion": "Se ha roto la tubería del baño y hay una fuga continua.",
                  "gremios": ["fontanero", "albañil"]
                }
                """.formatted(fijo, fijo);
    }
}
