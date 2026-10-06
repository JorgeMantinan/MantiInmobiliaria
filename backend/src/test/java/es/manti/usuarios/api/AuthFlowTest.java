package es.manti.usuarios.api;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de la FASE 1 con MockMvc contra PostgreSQL real:
 * registro → login → me → refresh (rotación) → logout, más los caminos de error.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    private static final String PASSWORD = "secreto123";
    private static final String CABECERA_CSRF = "X-Requested-With";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Borra los usuarios de prueba (los refresh tokens caen en cascada). */
    @AfterEach
    void limpiarUsuariosCreados() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@example.com'");
    }

    @Test
    void flujoCompleto_registrar_login_me_refresh_conRotacion_y_logout_devuelve200() throws Exception {
        String email = emailNuevo();

        // 1) Registro (auto-login): access token en body + refresh cookie
        Sesion registro = registrar(email);

        // 2) Login con las credenciales correctas
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.rol").value("USER"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn();
        Sesion sesionLogin = sesionDesde(login);

        // 3) GET /me con el access token (Bearer)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nombre").value("Ana Test"))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // 4) Refresh: rota la cookie y devuelve un access token nuevo
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(registro.refreshCookie())
                        .header(CABECERA_CSRF, "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn();
        Sesion refrescada = sesionDesde(refresh);
        assertThat(refrescada.accessToken()).isNotEqualTo(sesionLogin.accessToken());
        assertThat(refrescada.refreshCookie().getValue())
                .isNotEqualTo(registro.refreshCookie().getValue());

        // 5) Reutilizar el refresh ya rotado → 401 (rotación + detección de reuso)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(registro.refreshCookie())
                        .header(CABECERA_CSRF, "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // 6) Logout: borra la cookie y revoca el refresh
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refrescada.refreshCookie())
                        .header(CABECERA_CSRF, "XMLHttpRequest"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // 7) El refresh deslogueado ya no sirve
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refrescada.refreshCookie())
                        .header(CABECERA_CSRF, "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_conPasswordIncorrecta_devuelve401ConProblemDetail() throws Exception {
        String email = emailNuevo();
        registrar(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"clave-incorrecta-1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
    }

    @Test
    void login_conUsuarioInexistente_devuelve401ConMensajeGenerico() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-existe@example.com","password":"clave-incorrecta-1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
    }

    @Test
    void refresh_conCookieInvalida_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "token-que-no-existe"))
                        .header(CABECERA_CSRF, "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void refresh_sinCabeceraXRequestedWith_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "cualquiera")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("X-Requested-With")));
    }

    @Test
    void logout_sinCabeceraXRequestedWith_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "cualquiera")))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void me_conTokenFalso_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_conPasswordCorta_devuelve400ConErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","email":"corto@example.com","password":"1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void register_emailDuplicado_devuelve409() throws Exception {
        String email = emailNuevo();
        registrar(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otra Persona","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está registrado"));
    }

    @Test
    void oauth2_token_conCodigoInvalido_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"codigo-que-no-existe"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Código de autenticación inválido o caducado"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Sesion(String accessToken, Cookie refreshCookie) {
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private Sesion registrar(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn();
        return sesionDesde(resultado);
    }

    private Sesion sesionDesde(MvcResult resultado) throws Exception {
        String accessToken = JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
        Cookie refresh = resultado.getResponse().getCookie("refresh_token");
        assertThat(refresh).as("cookie refresh_token presente").isNotNull();
        return new Sesion(accessToken, refresh);
    }
}
