package es.manti.usuarios.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit (issue #17) sobre las rutas de SESIÓN: rote del refresh token y
 * canje del código de Google. Los límites bajos sólo afectan a este contexto de
 * Spring (propiedades propias), igual que en {@link RateLimitLoginTest}.
 *
 * <p>El refresh lleva la cabecera {@code X-Requested-With} para que el
 * {@link es.manti.usuarios.infra.filter.CookieEndpointCsrfFilter} deje pasar la
 * petición hasta el rate limit (sin ella se cortaría antes con 403 y no se
 * estaría probando el límite).</p>
 */
@SpringBootTest(properties = {
        "auth.rate-limit.refresh-per-minute=2",
        "auth.rate-limit.oauth2-token-per-minute=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitRefreshTest {

    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String CANJE_GOOGLE = "/api/v1/auth/oauth2/token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void refresh_superadoElLimiteDeIntentos_devuelve429ConRetryAfter() throws Exception {
        // Sin cookie de refresh: las dos primeras peticiones llegan al
        // controlador y acaban en 401 (token inexistente)…
        mockMvc.perform(post(REFRESH).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(REFRESH).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());

        // …y la tercera ni siquiera llega: la corta el rate limit (2/min).
        MvcResult rechazo = mockMvc.perform(post(REFRESH)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andReturn();

        String retryAfter = rechazo.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("cabecera Retry-After en la 429").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
    }

    @Test
    void canjeOAuth2_superadoElLimiteDeIntentos_devuelve429() throws Exception {
        String cuerpo = """
                {"code":"codigo-inexistente"}
                """;

        mockMvc.perform(post(CANJE_GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(CANJE_GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(CANJE_GOOGLE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"));
    }
}
