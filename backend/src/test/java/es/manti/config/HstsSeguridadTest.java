package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #9 — HSTS emítido SÓLO cuando está activado y la petición es segura
 * (en prod lo será tras el TLS terminator de Vercel gracias a
 * forward-headers-strategy). Contexto con la cabecera ACTIVADA para poder
 * verificar ambos caminos.
 */
@SpringBootTest(properties = {
        "manti.seguridad.hsts.enabled=true",
        "manti.seguridad.hsts.max-age-segundos=1234"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HstsSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void peticionSegura_conHstsActivado_llevaLaCabecera() throws Exception {
        mockMvc.perform(segura(get("/api/v1/inmuebles")))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        allOf(containsString("max-age=1234"), containsString("includeSubDomains"))));
    }

    @Test
    void peticionInsegura_noLlevaHstsAunqueEsteActivado() throws Exception {
        // Spring Security sólo emite HSTS si request.isSecure(): en local
        // (http://) la cabecera sería contraproducente.
        mockMvc.perform(get("/api/v1/inmuebles"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    /** Marca la petición como servida por HTTPS (simula X-Forwarded-Proto). */
    private static MockHttpServletRequestBuilder segura(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            ((MockHttpServletRequest) request).setSecure(true);
            return request;
        });
    }
}
