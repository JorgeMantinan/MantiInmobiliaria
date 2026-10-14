package es.manti.usuarios.api;

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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #10 — backoff por CUENTA en login (además del rate limit por IP, que
 * aquí sigue holgado): tras N fallos consecutivos la cuenta se bloquea con
 * backoff exponencial y la 429 lleva {@code Retry-After}; un acierto resetea
 * el contador.
 *
 * <p>Límites bajos SÓLO en este contexto de Spring (propiedades propias); la
 * protección está apagada por defecto en el perfil test.</p>
 */
@SpringBootTest(properties = {
        "auth.login.enabled=true",
        "auth.login.fallos-antes-de-backoff=3",
        "auth.login.backoff-inicial=2m",
        "auth.login.pares-por-minuto=1000",
        "manti.notificaciones.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginBackoffTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PASSWORD = "secreto123";
    private static final java.util.regex.Pattern TOKEN_EN_ENLACE =
            java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private es.manti.shared.notificaciones.EmailNotificadorPort correo;

    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void login_trasTresFallosElCuartoIntento_devuelve429ConRetryAfter() throws Exception {
        String email = registrarUsuario();

        for (int i = 0; i < 3; i++) {
            loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        }

        String retryAfter = loginConPasswordIncorrecta(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/login-bloqueado"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        // Backoff inicial de 2 minutos (redondeo al alza de los ms restantes)
        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(61, 120);
    }

    @Test
    void login_bloqueadoPorBackoff_tambienRechazaLaPasswordCorrecta() throws Exception {
        String email = registrarUsuario();
        for (int i = 0; i < 3; i++) {
            loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        }

        // Con la cuenta en backoff ni siquiera se paga BCrypt: 429 aunque la
        // password sea la buena.
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_conAcierto_reseteaElContadorDeFallos() throws Exception {
        String email = registrarUsuario();

        // 2 fallos (debajo del umbral), acierto y luego 2 fallos más: ninguno
        // debe bloquear porque el acierto puso el contador a cero.
        loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk());

        loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());

        // El siguiente fallo sería el nº3: bloquea para la petición SIGUIENTE.
        loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        loginConPasswordIncorrecta(email).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_backoffDeUnaCuenta_noAfectaAOtra() throws Exception {
        String bloqueada = registrarUsuario();
        String otra = registrarUsuario();
        for (int i = 0; i < 3; i++) {
            loginConPasswordIncorrecta(bloqueada).andExpect(status().isUnauthorized());
        }

        // La otra cuenta sigue intentando con normalidad.
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"clave-incorrecta-1"}
                                """.formatted(otra)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Escape del lockout-DoS (revisión seguridad PR #102): un atacante que
     * conozca el email puede mantener la cuenta bloqueada indefinidamente con
     * un fallo cada backoff-tope. Un canje exitoso de reset limpia el backoff,
     * así que la víctima recupera el acceso sin esperar al atacante.
     */
    @Test
    void resetExitoso_limpiaElBackoffDeLaCuenta() throws Exception {
        String email = registrarUsuario();
        for (int i = 0; i < 3; i++) {
            loginConPasswordIncorrecta(email).andExpect(status().isUnauthorized());
        }
        // Cuenta bloqueada: ni la password actual entra.
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isTooManyRequests());

        // Reset completo (forgot → token del email mockeado → reset).
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        String token = tokenDelEnlace();
        String passwordNueva = "claveNueva456";
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, passwordNueva)))
                .andExpect(status().isNoContent());

        // El backoff quedó limpio: la password NUEVA entra a la primera.
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, passwordNueva)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ResultActions loginConPasswordIncorrecta(String email) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"clave-incorrecta-1"}
                        """.formatted(email)));
    }

    private String registrarUsuario() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    /** Extrae el token del último enlace enviado por el correo mockeado. */
    private String tokenDelEnlace() {
        org.mockito.ArgumentCaptor<String> html =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(correo).enviar(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                html.capture());
        java.util.regex.Matcher matcher = TOKEN_EN_ENLACE.matcher(html.getValue());
        org.assertj.core.api.Assertions.assertThat(matcher.find())
                .as("el email de reset debe contener token=<opaco>")
                .isTrue();
        return matcher.group(1);
    }
}
