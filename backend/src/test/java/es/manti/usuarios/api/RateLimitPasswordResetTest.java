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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limiting por IP de los endpoints de reset de contraseña (issue #57):
 * 5/hora en forgot y 10/hora en reset. Los límites bajos sólo afectan a ESTE
 * contexto de Spring (propiedades propias), como en {@link RateLimitLoginTest}.
 */
@SpringBootTest(properties = {
        "auth.rate-limit.forgot-password-per-hour=2",
        "auth.rate-limit.reset-password-per-hour=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitPasswordResetTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forgot_superadoElLimite_devuelve429ConRetryAfter() throws Exception {
        String body = """
                {"email":"nadie-%s@example.com"}
                """.formatted(UUID.randomUUID());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/password/forgot")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }

        MvcResult rechazo = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andReturn();

        String retryAfter = rechazo.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("cabecera Retry-After en la 429").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 3600);
    }

    @Test
    void reset_superadoElLimite_devuelve429SinLlegarAlServicio() throws Exception {
        String body = """
                {"token":"token-que-no-llega-a-servicio","password":"claveNueva456"}
                """;
        // Sin rate limit esto sería 400 (token inexistente): el filtro va ANTES.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
