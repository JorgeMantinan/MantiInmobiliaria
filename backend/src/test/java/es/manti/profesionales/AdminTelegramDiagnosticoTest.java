package es.manti.profesionales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diagnóstico de Telegram SIN credencial: el token se fuerza a vacío con una
 * propiedad inline para que el 409 sea determinista tanto en local (donde
 * {@code .env} trae un token real) como en CI (donde no existe {@code .env}).
 */
@SpringBootTest(properties = "TELEGRAM_BOT_TOKEN=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTelegramDiagnosticoTest {

    private static final String RUTA = "/api/v1/admin/telegram/diagnostico";
    private static final String PASSWORD = "secreto123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiarFixtures() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void diagnostico_sinTokenDeAdmin_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void diagnostico_comoUsuarioNormal_devuelve403() throws Exception {
        mockMvc.perform(get(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUsuarioNormal()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void diagnostico_comoAdminSinCredencialDeTelegram_devuelve409ProblemDetail() throws Exception {
        mockMvc.perform(get(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/telegram-sin-credencial"))
                .andExpect(jsonPath("$.detail").value(
                        containsString("telegram sin credencial")));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String tokenAdmin() throws Exception {
        return token("ADMIN");
    }

    private String tokenUsuarioNormal() throws Exception {
        return token("USER");
    }

    /** Usuario de prueba con el rol indicado y su access token vía login real. */
    private String token(String rol) throws Exception {
        String email = "it-" + rol.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        jdbc.update("""
                INSERT INTO usuarios (email, password_hash, nombre, rol, activo)
                VALUES (?, ?, 'Usuario Test', ?, true)
                """, email, passwordEncoder.encode(PASSWORD), rol);
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
