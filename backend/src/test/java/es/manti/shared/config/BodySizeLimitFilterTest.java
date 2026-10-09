package es.manti.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límite de tamaño de cuerpo (pre-release): {@code Content-Length} declarado
 * por encima de 256 KiB → 413 ProblemDetail ({@code payload-too-large}) antes
 * de llegar al controlador. Sin la cabecera (chunked) la petición pasa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodySizeLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_conContentLengthDe999999_devuelve413PayloadTooLarge() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_LENGTH, "999999")
                        .content("""
                                {"email":"ana@example.com","password":"clave-incorrecta-1"}
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.type")
                        .value("https://api.manti.dev/problems/payload-too-large"));
    }

    @Test
    void solicitud_conContentLengthDe999999_devuelve413PayloadTooLarge() throws Exception {
        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_LENGTH, "999999")
                        .content("{}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type")
                        .value("https://api.manti.dev/problems/payload-too-large"));
    }

    /** Chunked real (MockMvc no setea Content-Length): el stream contado da 413. */
    @Test
    void login_chunkedSinContentLength_conCuerpoGrande_devuelve413() throws Exception {
        byte[] cuerpoGrande = new byte[300_000]; // > 256 KiB, sin Content-Length

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoGrande))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.type")
                        .value("https://api.manti.dev/problems/payload-too-large"));
    }

    /** Sin Content-Length declarada (chunked) el filtro deja pasar la petición:
     *  el login llega al controlador y sólo falla por credenciales (401). */
    @Test
    void login_sinCabeceraContentLength_noDevuelve413() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@example.com","password":"clave-incorrecta-1"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
