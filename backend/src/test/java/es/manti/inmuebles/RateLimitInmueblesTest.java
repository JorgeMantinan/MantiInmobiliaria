package es.manti.inmuebles;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit del listado PÚBLICO de inmuebles (issue #22): el GET es anónimo y
 * sin límite un cliente podría forzar COUNT + ILIKE trgm en cada petición.
 *
 * <p>El límite bajo (2/min) sólo afecta a este contexto de Spring; el resto de
 * la suite sube {@code auth.rate-limit.inmuebles-per-minute} a 100000 en el
 * perfil de test, así que el listado nunca se vuelve flaky. Un único método de
 * test: la ventana es compartida entre peticiones de la misma clase y así cada
 * aserción es válida sin importar el orden.</p>
 */
@SpringBootTest(properties = {
        "auth.rate-limit.inmuebles-per-minute=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitInmueblesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listado_superadoElLimite_devuelve429_yLaQueryComparteLaVentana() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isOk());

        // 3ª petición de la ventana (2/min): cortada por el filtro.
        MvcResult rechazo = mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andReturn();

        String retryAfter = rechazo.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("cabecera Retry-After en la 429").isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);

        // La clave es la RUTA normalizada, sin query string: si cada
        // combinación de filtros tuviera su propia ventana el límite se
        // evadiría variando ?precioMin= — esta variante sigue bloqueada.
        mockMvc.perform(get("/api/v1/inmuebles?limit=5&precioMax=200000"))
                .andExpect(status().isTooManyRequests());
    }
}
