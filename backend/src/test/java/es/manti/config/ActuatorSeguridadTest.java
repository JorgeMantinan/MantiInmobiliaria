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
 * p50/p95/p99) pero sólo detrás de autenticación, y sólo lo que dice
 * {@code management.endpoints.web.exposure.include}.
 *
 * <ul>
 *   <li>{@code /actuator/health} es el ÚNICO anónimo (healthcheck) y sin
 *       detalle ({@code show-details: never} → sin {@code components});</li>
 *   <li>{@code /actuator/info}, {@code metrics} y {@code prometheus} → 401
 *       sin token, 403 con rol USER, 200 con ADMIN;</li>
 *   <li>los endpoints fuera de la lista de exposure ({@code env}) → 404
 *       incluso con ADMIN: el recorte es de exposición, no sólo de seguridad.</li>
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

    /**
     * El healthcheck anónimo no puede filtrar el interior: ni components ni
     * indicators (sólo el estado global; Boot añade además el listado de
     * nombres de {@code groups}, sin valores).
     */
    @Test
    void health_anonimo_noExponeNiComponentsNiIndicators() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.diskSpace").doesNotExist());
    }

    /** Decisión de la revisión: SÓLO health es anónimo, info exige ADMIN. */
    @Test
    void info_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void info_conAdmin_devuelve200() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
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
    @WithMockUser(roles = "USER")
    void prometheus_conRolUser_devuelve403() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void prometheus_conAdmin_devuelve200ConFormatoPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    /**
     * El recorte de {@code exposure.include} es de EXPOSICIÓN, no sólo de
     * autorización: {@code /actuator/env} (variables de entorno, posibles
     * secretos) ni siquiera tiene handler, así que ni el rol ADMIN sirve.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void env_conAdmin_devuelve404PorNoEstarExpuesto() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
