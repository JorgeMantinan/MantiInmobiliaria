package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FASE 7 — issue #25: el actuator expone metrics/prometheus (percentiles
 * p50/p95/p99) pero sólo detrás de autenticación.
 *
 * <ul>
 *   <li>{@code /actuator/health} sigue ANÓNIMO (healthcheck del hosting);</li>
 *   <li>{@code /actuator/metrics} sin token → 401;</li>
 *   <li>{@code /actuator/metrics} con rol ADMIN → 200;</li>
 *   <li>rol USER → 403 (autenticado pero sin permiso).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_sinToken_devuelve200Anonimo() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                // Boot 4 responde con su media type de actuator (v3+json)
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json")))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void metrics_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void metrics_conAdmin_devuelve200ConLasMétricasClave() throws Exception {
        // La 1ª llamada registra el timer http.server.requests (se cronometra al
        // terminar la petición), por eso se pide dos veces antes de asertar.
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray())
                .andExpect(jsonPath("$.names", hasItem("http.server.requests")))
                .andExpect(jsonPath("$.names", hasItem("hikaricp.connections.active")))
                .andExpect(jsonPath("$.names", hasItem("jvm.memory.used")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void metrics_conRolUser_devuelve403() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void prometheus_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void prometheus_conAdmin_devuelve200ConFormatoPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
