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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotencia del alta pública (skill api-rest #9): la cabecera OPCIONAL
 * {@code Idempotency-Key} evita que un doble envío cree DOS solicitudes y DOS
 * notificaciones. La clave se guarda en la MISMA transacción que la solicitud,
 * así que el replay es exacto (mismo id, mismo cuerpo de respuesta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolicitudIdempotenciaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final String clave = "idem-" + UUID.randomUUID();

    @AfterEach
    void limpiarFixtures() {
        // Borra la solicitud: la clave la sigue por ON DELETE CASCADE.
        jdbc.update("DELETE FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com'");
    }

    @Test
    void crear_conLaMismaClaveDosVeces_creaUnaSolaSolicitudYDevuelveLaMismaRespuesta()
            throws Exception {
        String cuerpo = cuerpoValido();

        String primera = crear(cuerpo, clave);
        String segunda = crear(cuerpo, clave);

        assertThat(segunda).isEqualTo(primera);
        assertThat(conteoSolicitudes(cuerpo)).isEqualTo(1);
        Integer claves = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitudes_idempotencia WHERE clave = ?", Integer.class, clave);
        assertThat(claves).isEqualTo(1);
    }

    @Test
    void crear_conLaMismaClaveYPayloadDistinto_devuelve409ProblemDetail() throws Exception {
        String cuerpo = cuerpoValido();
        crear(cuerpo, clave);

        String distinto = cuerpo.replace("Se ha roto la tubería del baño",
                "La caldera de la comunidad no enciende");

        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", clave)
                        .content(distinto))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/idempotencia-conflicto"))
                .andExpect(jsonPath("$.title").value("Clave de idempotencia en uso"));

        // Ni la solicitud original ni ninguna nueva se han tocado
        assertThat(conteoSolicitudes(cuerpo)).isEqualTo(1);
    }

    @Test
    void crear_conClaveDeMasDe64Caracteres_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k".repeat(65))
                        .content(cuerpoValido()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"));
    }

    @Test
    void crear_sinCabeceraDeIdempotencia_sigueCreandoSolicitudesNuevas() throws Exception {
        String cuerpo = cuerpoValido();

        crear(cuerpo, null);
        crear(cuerpo, null);

        assertThat(conteoSolicitudes(cuerpo)).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Devuelve el cuerpo de la respuesta 201 (JSON serializado). */
    private String crear(String cuerpo, String claveIdempotencia) throws Exception {
        var peticion = post("/api/v1/solicitudes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
        if (claveIdempotencia != null) {
            peticion = peticion.header("Idempotency-Key", claveIdempotencia);
        }
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        return resultado.getResponse().getContentAsString();
    }

    /** Nº de solicitudes creadas con el email del CUERPO de la petición. */
    private int conteoSolicitudes(String cuerpoRequest) {
        String email = JsonPath.read(cuerpoRequest, "$.emailContacto");
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitudes WHERE email_contacto = ?", Integer.class, email);
        return total == null ? 0 : total;
    }

    private static String cuerpoValido() {
        return """
                {
                  "nombreContacto": "Contacto It",
                  "emailContacto": "it-sol-idem-%s@example.com",
                  "municipio": "It-Municipio",
                  "descripcion": "Se ha roto la tubería del baño y hay una fuga continua.",
                  "gremios": ["fontanero", "albañil"]
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));
    }
}
