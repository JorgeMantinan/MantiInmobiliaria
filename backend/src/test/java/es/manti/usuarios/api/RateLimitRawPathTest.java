package es.manti.usuarios.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión de seguridad: la ruta cruda ({@code ;params} o %xx sin decodificar)
 * no puede evadir el rate limit. Los matchers de Spring normalizan la ruta,
 * así que los filtros deben hacerlo igual ({@link es.manti.usuarios.infra.filter.AuthRequestPath}).
 *
 * <p>Las propiedades incluyen una irrelevante ({@code register-per-minute})
 * distinta a la de {@link RateLimitLoginTest} para forzar un contexto de Spring
 * propio con su propia ventana de rate limit (ventana limpia al empezar).</p>
 */
@SpringBootTest(properties = {
        "auth.rate-limit.login-per-minute=2",
        "auth.rate-limit.register-per-minute=9998"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitRawPathTest {

    private static final String LOGIN_MALO = """
            {"email":"nadie@example.com","password":"clave-incorrecta-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_conPathParamsOEscapes_noEvadeElRateLimit() throws Exception {
        // 1ª petición normal: consume la ventana (1/2)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_MALO))
                .andExpect(status().isUnauthorized());

        // 2ª con ;params: MISMA ventana normalizada (2/2) — sin el fix tendría clave propia
        mockMvc.perform(post("/api/v1/auth/login;foo=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_MALO))
                .andExpect(status().isUnauthorized());

        // 3ª con %6C en lugar de 'l' (escape crudo; URI.create evita la doble
        // codificación de MockMvc): sin el fix tendría una tercera clave y nunca
        // llegaría al límite.
        mockMvc.perform(post(URI.create("/api/v1/auth/%6Cogin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_MALO))
                .andExpect(status().isTooManyRequests());
    }
}
