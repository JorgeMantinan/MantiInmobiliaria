package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthService;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #24 — BCrypt fuera de la transacción en login y registro.
 *
 * <p>Dos niveles de verificación:</p>
 * <ol>
 *   <li><b>comportamiento visible idéntico</b>: registro → login OK y los dos
 *       caminos de login fallido (password incorrecta y usuario inexistente)
 *       responden exactamente igual que antes de partir la transacción;</li>
 *   <li><b>invariante estructural</b>: {@code login} y {@code registrar} NO
 *       pueden volver a llevar {@code @Transactional}, que es justo lo que
 *       retenía una conexión de Hikari durante los 60-150 ms de BCrypt.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLoginTransaccionTest {

    private static final String PASSWORD = "secreto123";

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
    void registroYLogin_devuelvenSesionCompleta_conElMensajeDeSiempre() throws Exception {
        String email = emailNuevo();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.rol").value("USER"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
    }

    @Test
    void login_conPasswordIncorrecta_devuelve401ConElMismoProblemDetail() throws Exception {
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

    /** El usuario inexistente paga el MISMO coste (hash dummy) y el MISMO mensaje. */
    @Test
    void login_conUsuarioInexistente_devuelve401IndistinguibleDePasswordIncorrecta() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-existe-%s@example.com","password":"clave-incorrecta-1"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
    }

    /**
     * Invariante del issue #24: si alguien vuelve a poner {@code @Transactional}
     * en estos dos métodos, BCrypt vuelve a retener una conexión del pool
     * (pool de 10) durante 60-150 ms y 10 logins concurrentes lo saturen.
     * La escritura (alta + sesión) se hace en la transacción nueva interna.
     */
    @Test
    void loginYRegistrar_noDeclaranTransaccionPropia() throws Exception {
        assertThat(AuthService.class.getMethod("login", String.class, String.class)
                .isAnnotationPresent(Transactional.class))
                .as("login no debe abrir transacción: BCrypt corre fuera de ella")
                .isFalse();
        assertThat(AuthService.class.getMethod("registrar", es.manti.usuarios.application.RegistroCommand.class)
                .isAnnotationPresent(Transactional.class))
                .as("registrar no debe abrir transacción: el encode BCrypt corre fuera de ella")
                .isFalse();
        assertThat(AuthService.class.isAnnotationPresent(Transactional.class))
                .as("AuthService no debe declarar transacción a nivel de clase")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private void registrar(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Test","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie refresh = resultado.getResponse().getCookie("refresh_token");
        assertThat(refresh).as("cookie refresh_token en el registro").isNotNull();
    }
}
