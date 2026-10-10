package es.manti.usuarios.api;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.shared.notificaciones.NotificacionException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo forgot/reset de contraseña (issue #57) contra PostgreSQL real.
 *
 * <p>El puerto de email va mockeado: los tests verifican QUÉ enlace se envía
 * sin depender de Brevo (en test no hay credencial, así que el adaptador real
 * fallaría antes de salir a la red).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetApiTest {

    private static final String PASSWORD = "secreto123";
    private static final String PASSWORD_NUEVA = "claveNueva456";
    private static final String FORGOT = "/api/v1/auth/password/forgot";
    private static final String RESET = "/api/v1/auth/password/reset";
    private static final Pattern TOKEN_EN_ENLACE = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailNotificadorPort correo;

    /** Borra los usuarios de prueba (tokens y refresh caen en cascada). */
    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    // ------------------------------------------------------------------
    // forgot
    // ------------------------------------------------------------------

    @Test
    void forgot_conEmailInexistente_devuelve202SinEnviarCorreo() throws Exception {
        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-existe-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verifyNoInteractions(correo);
    }

    @Test
    void forgot_conEmailExistente_devuelve202YEnviaEnlaceConElToken() throws Exception {
        String email = emailNuevo();
        registrar(email);

        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String token = tokenDelEnlace(email);
        // El token vive SÓLO en el email: en BD sólo está su hash SHA-256.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class, sha256(token)))
                .as("en BD sólo se guarda el hash del token, nunca el token en claro")
                .isEqualTo(1);
    }

    @Test
    void forgot_conCuentaGoogleSinPassword_devuelve202SinEnviar() throws Exception {
        String email = emailNuevo();
        crearCuentaGoogle(email);

        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        // Cuenta Google-only (password_hash NULL): responde igual pero no envía.
        verifyNoInteractions(correo);
    }

    /** Sin enumeración: la respuesta para una cuenta real y para una inventada es la MISMA. */
    @Test
    void forgot_respuestaIdéntica_paraCuentaExistenteYNoExistente() throws Exception {
        String existente = emailNuevo();
        registrar(existente);

        String inexistente = "no-existe-" + UUID.randomUUID() + "@example.com";

        MvcResult conCuenta = mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(existente)))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult sinCuenta = mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(inexistente)))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(conCuenta.getResponse().getStatus())
                .isEqualTo(sinCuenta.getResponse().getStatus());
        assertThat(conCuenta.getResponse().getContentAsString())
                .as("cuerpo idéntico: no revela si la cuenta existe")
                .isEqualTo(sinCuenta.getResponse().getContentAsString());
        assertThat(conCuenta.getResponse().getContentAsString()).isEmpty();
    }

    /** El proveedor de email caído no cambia la respuesta: sigue siendo 202. */
    @Test
    void forgot_conFallaDelProveedorDeEmail_devuelveIgual202() throws Exception {
        String email = emailNuevo();
        registrar(email);
        doThrow(NotificacionException.definitiva("Brevo rechazó la petición (HTTP 400)"))
                .when(correo).enviar(anyString(), anyString(), anyString());

        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
    }

    @Test
    void forgot_conEmailMalFormado_devuelve400ConErrores() throws Exception {
        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"esto-no-es-un-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ------------------------------------------------------------------
    // reset
    // ------------------------------------------------------------------

    @Test
    void reset_conTokenValido_devuelve204YLaPasswordNuevaHaceLogin() throws Exception {
        String email = emailNuevo();
        registrar(email);
        String token = pedirEnlace(email);

        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // La password nueva entra…
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD_NUEVA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // …y la vieja ya no.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
    }

    @Test
    void reset_revocaTodaLaFamiliaDeRefreshTokens() throws Exception {
        String email = emailNuevo();
        // Registro (cookie 1) + refresh (cookie 2): dos tokens vivos de la misma familia.
        CookieFamily familia = registrarYRefrescar(email);
        String token = pedirEnlace(email);

        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isNoContent());

        // El refresh vigente (nunca usado antes de resetear) ya no vale.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(familia.refrescada())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // …ni el anterior (rotado, pero revocado por la familia completa).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(familia.original())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reset_tokenReusado_devuelve400ConTokenInvalido() throws Exception {
        String email = emailNuevo();
        registrar(email);
        String token = pedirEnlace(email);

        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isNoContent());

        // Un solo uso: el mismo enlace otra vez → 400 token-invalido.
        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"otraClave789"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/token-invalido"))
                .andExpect(jsonPath("$.title").value("Token inválido"));
    }

    @Test
    void reset_tokenExpirado_devuelve400ConTokenInvalido() throws Exception {
        String email = emailNuevo();
        registrar(email);
        String token = pedirEnlace(email);
        // Fixture: envejece el token por encima del TTL de 15 minutos.
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' "
                        + "WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)",
                email);

        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/token-invalido"));
    }

    @Test
    void reset_tokenInexistente_devuelve400ConTokenInvalido() throws Exception {
        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-que-nunca-se-emitio","password":"%s"}
                                """.formatted(PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/token-invalido"));
    }

    @Test
    void reset_passwordDebil_devuelve400ConErrores() throws Exception {
        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"cualquiera","password":"1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record CookieFamily(Cookie original, Cookie refrescada) {
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult registrar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private CookieFamily registrarYRefrescar(String email) throws Exception {
        Cookie original = registrar(email).getResponse().getCookie("refresh_token");
        assertThat(original).isNotNull();
        Cookie refrescada = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(original)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token");
        assertThat(refrescada).isNotNull();
        assertThat(refrescada.getValue()).isNotEqualTo(original.getValue());
        return new CookieFamily(original, refrescada);
    }

    /** Pide el enlace y devuelve el token que sólo viaja en el email. */
    private String pedirEnlace(String email) throws Exception {
        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        return tokenDelEnlace(email);
    }

    private String tokenDelEnlace(String email) {
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(email), asunto.capture(), html.capture());
        assertThat(asunto.getValue()).isNotBlank();
        Matcher matcher = TOKEN_EN_ENLACE.matcher(html.getValue());
        assertThat(matcher.find())
                .as("el email debe contener el enlace /restablecer?token=<opaco>")
                .isTrue();
        assertThat(html.getValue()).contains("/restablecer?token=");
        assertThat(html.getValue())
                .as("el TTL del enlace se deriva de auth.password-reset.ttl (15m por defecto), "
                        + "no está hardcodeado en el cuerpo")
                .contains("caduca en 15 minutos");
        return matcher.group(1);
    }

    private void crearCuentaGoogle(String email) {
        jdbc.update("INSERT INTO usuarios (email, password_hash, nombre, google_id, rol, activo, "
                        + "email_verificado) VALUES (?, NULL, 'Cuenta Google', ?, 'USER', true, true)",
                email, "g-" + UUID.randomUUID());
    }

    private static String sha256(String valor) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
