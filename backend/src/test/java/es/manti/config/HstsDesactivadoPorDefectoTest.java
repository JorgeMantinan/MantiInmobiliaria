package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #9 — fuera de prod (default {@code manti.seguridad.hsts.enabled=false})
 * NUNCA se emite HSTS, ni siquiera en una petición marcada como segura.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HstsDesactivadoPorDefectoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void peticionSegura_sinActivar_noLlevaHsts() throws Exception {
        mockMvc.perform(get("/api/v1/inmuebles").with(request -> {
                    ((MockHttpServletRequest) request).setSecure(true);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
