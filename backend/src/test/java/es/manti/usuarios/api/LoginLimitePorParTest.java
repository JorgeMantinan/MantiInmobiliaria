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
    // Regresión de la revisión de seguridad del PR #102: la extracción del
    // email con regex sobre el crudo se evadía con escapes unicode o claves
    // duplicadas del JSON. El filtro debe ver EXACTAMENTE lo que ve Jackson.
    // ------------------------------------------------------------------

    @Test
    void login_emailConEscapeUnicode_comparteLaMismaVentana() throws Exception {
        String email = emailNuevo();
        // Clave "email" escrita con escape unicode del JSON (e = 'e'):
        // Jackson la decodifica a "email", la regex vieja no la encontraba.
        String claveEscapada = "\\" + "u0065mail";
        String payload = "{\"" + claveEscapada + "\":\"" + email
                + "\",\"password\":\"clave-incorrecta-1\"}";

        loginCrudo("3.3.3.3", payload).andExpect(status().isUnauthorized());
        loginCrudo("3.3.3.3", payload).andExpect(status().isUnauthorized());
        // Tercera: la ventana por par sigue contando (el escape no crea una
        // clave de caché distinta).
        loginCrudo("3.3.3.3", payload).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_emailComoClaveDuplicada_contabilizaContraLaUltima() throws Exception {
        String victima = emailNuevo();
        String decoy = emailNuevo();
        // Jackson resuelve la ÚLTIMA clave: el login real es contra "victima".
        String payload = "{\"email\":\"" + decoy + "\",\"email\":\"" + victima
                + "\",\"password\":\"clave-incorrecta-1\"}";

        loginCrudo("4.4.4.4", payload).andExpect(status().isUnauthorized());
        loginCrudo("4.4.4.4", payload).andExpect(status().isUnauthorized());
        // Tercera contra la víctima: 429 (la ventana se acumuló con la
        // ÚLTIMA clave, igual que la autenticación).
        loginCrudo("4.4.4.4", payload).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_sinCampoEmail_devuelve400DesdeElFiltro() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"clave-incorrecta-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://api.manti.dev/problems/validacion-fallida"));
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

    /** Login con payload JSON crudo (escapes y claves duplicadas). */
    private ResultActions loginCrudo(String ip, String payload) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private String emailNuevo() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }
}
