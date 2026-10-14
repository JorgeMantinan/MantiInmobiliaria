package es.manti.usuarios.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * Issue #5 — segunda ventana del login por par sha256(IP + email): acota la
 * fuerza bruta sobre UNA cuenta desde una misma IP y, a la vez, una IP de NAT
 * no consume el presupuesto de otras cuentas. Es una capa MÁS allá del rate
 * limit por IP (aquí holgado) y del backoff por cuenta (#10, aquí con umbral
 * altísimo para aislar esta dimensión).
 */
@SpringBootTest(properties = {
        "auth.login.enabled=true",
        "auth.login.fallos-antes-de-backoff=100000",
        "auth.login.pares-por-minuto=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginLimitePorParTest {

    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mismaIpMismoEmail_tresIntentos_segundoLimiteDevuelve429() throws Exception {
        String email = emailNuevo();

        loginDesde("1.1.1.1", email).andExpect(status().isUnauthorized());
        loginDesde("1.1.1.1", email).andExpect(status().isUnauthorized());

        String retryAfter = loginDesde("1.1.1.1", email)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/rate-limit"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
    }

    @Test
    void otraIpMismoEmail_noComparteLaVentanaDelPar() throws Exception {
        String email = emailNuevo();
        loginDesde("1.1.1.1", email).andExpect(status().isUnauthorized());
        loginDesde("1.1.1.1", email).andExpect(status().isUnauthorized());
        loginDesde("1.1.1.1", email).andExpect(status().isTooManyRequests());

        // Otra IP con la misma cuenta: ventana propia (el backoff por cuenta
        // —la red que frena el ataque distribuido— está aquí con umbral altísimo).
        loginDesde("2.2.2.2", email).andExpect(status().isUnauthorized());
    }

    @Test
    void mismaIpOtraCuenta_noPenalizaAlRestoDelNat() throws Exception {
        String agotada = emailNuevo();
        String otra = emailNuevo();
        loginDesde("1.1.1.1", agotada).andExpect(status().isUnauthorized());
        loginDesde("1.1.1.1", agotada).andExpect(status().isUnauthorized());
        loginDesde("1.1.1.1", agotada).andExpect(status().isTooManyRequests());

        // Misma IP (NAT), otra cuenta: no debe heredar el castigo.
        loginDesde("1.1.1.1", otra).andExpect(status().isUnauthorized());
    }

    /** El email se extrae con tope de 8 KiB: un login abusivo no evadir la protección. */
    @Test
    void login_conCuerpoDeMasDe8KiB_devuelve400() throws Exception {
        String relleno = "x".repeat(9_000);
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailNuevo(), relleno)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/cuerpo-invalido"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Login con password inexistente y IP de origen simulada. */
    private ResultActions loginDesde(String ip, String email) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"clave-incorrecta-1"}
                        """.formatted(email))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }
}
