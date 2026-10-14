package es.manti.usuarios.api;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.usuarios.domain.PasswordComprometidoPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #10 — la contraseña filtrada (HIBP) se rechaza en registro y en el
 * canje del restablecimiento, con 400 y {@code errors[0].field = password}.
 *
 * <p>El puerto va mockeado: en el perfil de test HIBP está desactivado
 * ({@code auth.hibp.enabled=false}) y la suite jamás sale a la red. El
 * adaptador real se prueba en {@code HibpPasswordAdapterTest} con el cliente
 * HTTP mockeado.</p>
 */
@SpringBootTest(properties = "manti.notificaciones.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordComprometidoApiTest {

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
    private PasswordComprometidoPort passwordComprometido;

    @MockitoBean
    private EmailNotificadorPort correo;

    @BeforeEach
    void porDefectoPasswordNoPwned() {
        lenient().when(passwordComprometido.estaComprometido(anyString())).thenReturn(false);
    }

    /** Borra los usuarios de prueba (tokens y refresh caen en cascada). */
    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void register_conPasswordPwned_devuelve400ConErrorDeCampoPassword() throws Exception {
        lenient().when(passwordComprometido.estaComprometido(anyString())).thenReturn(true);
        String email = emailNuevo();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/password-comprometida"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        // Nada se crea: la comprobación va ANTES del alta.
        assertThatUsuariosCreados(email, 0);
    }

    @Test
    void register_conPasswordNoPwned_llamaAlPuertoYDevuelve201() throws Exception {
        String email = emailNuevo();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        verify(passwordComprometido).estaComprometido(PASSWORD);
        assertThatUsuariosCreados(email, 1);
    }

    /** El 409 de email duplicado tiene prioridad sobre el 400 de HIBP. */
    @Test
    void register_emailDuplicadoYPasswordPwned_devuelve409() throws Exception {
        String email = emailNuevo();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        lenient().when(passwordComprometido.estaComprometido(anyString())).thenReturn(true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otra Persona","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD_NUEVA)))
                .andExpect(status().isConflict());
    }

    @Test
    void reset_conPasswordPwned_devuelve400SinConsumirElToken() throws Exception {
        String email = emailNuevo();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        String token = pedirEnlace(email);

        // 1º canje con contraseña pwned → 400 de campo password.
        lenient().when(passwordComprometido.estaComprometido(anyString())).thenReturn(true);
        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/password-comprometida"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        // El token SIGUE vigente: el 2º canje (password buena) devuelve 204.
        lenient().when(passwordComprometido.estaComprometido(anyString())).thenReturn(false);
        mockMvc.perform(post(RESET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, PASSWORD_NUEVA)))
                .andExpect(status().isNoContent());

        // Y la password nueva ya es la válida.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD_NUEVA)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private void assertThatUsuariosCreados(String email, int esperados) {
        Integer creados = jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, email);
        assertThat(creados).isEqualTo(esperados);
    }

    /** Pide el enlace y devuelve el token que sólo viaja en el email. */
    private String pedirEnlace(String email) throws Exception {
        mockMvc.perform(post(FORGOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(email), anyString(), html.capture());
        Matcher matcher = TOKEN_EN_ENLACE.matcher(html.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
