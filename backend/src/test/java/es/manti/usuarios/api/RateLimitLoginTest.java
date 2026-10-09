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
 * Rate limiting en memoria por IP para login: ventana fija de 60 s.
 * Los límites bajos sólo afectan a este contexto de Spring (propiedades propias).
 */
@SpringBootTest(properties = {
        "auth.rate-limit.login-per-minute=2",
        "auth.rate-limit.register-per-minute=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitLoginTest {

    private static final String LOGIN = """
            {"email":"nadie@example.com","password":"clave-incorrecta-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_superadoElLimiteDeIntentos_devuelve429ConRetryAfter() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isUnauthorized());

        MvcResult rechazo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andReturn();

        // Segundos hasta liberar la primera plaza de la ventana de 60 s
        String retryAfter = rechazo.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("cabecera Retry-After en la 429").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
    }
}
