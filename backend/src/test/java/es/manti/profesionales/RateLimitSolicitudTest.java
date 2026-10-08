package es.manti.profesionales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit de las rutas PÚBLICAS de la FASE 5 (ventana de 1 hora por IP en
 * {@link es.manti.usuarios.infra.filter.AuthRateLimitFilter}). Los límites
 * bajos sólo afectan a ESTE contexto de Spring: el resto de tests usa los
 * límites altos del perfil test.
 */
@SpringBootTest(properties = {
        "auth.rate-limit.solicitudes-per-hour=2",
        "auth.rate-limit.registro-profesional-per-hour=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitSolicitudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        FixturesProfesionales.limpiar(jdbc);
    }

    @Test
    void crear_masDeDiezSolicitudesPorHora_devuelve429() throws Exception {
        for (int intento = 1; intento <= 2; intento++) {
            mockMvc.perform(post("/api/v1/solicitudes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoSolicitud()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSolicitud()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"))
                .andExpect(jsonPath("$.detail").value(
                        "Límite de peticiones alcanzado; inténtelo de nuevo en una hora"));
    }

    @Test
    void registrar_masDeCincoProfesionalesPorHora_devuelve429() throws Exception {
        for (int intento = 1; intento <= 2; intento++) {
            mockMvc.perform(post("/api/v1/profesionales/registro")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoProfesional()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/profesionales/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoProfesional()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String cuerpoSolicitud() {
        String fijo = UUID.randomUUID().toString().substring(0, 8);
        return """
                {
                  "nombreContacto": "Contacto It",
                  "emailContacto": "it-sol-%s@example.com",
                  "municipio": "It-Municipio",
                  "descripcion": "Se ha roto la tubería del baño y hay una fuga continua.",
                  "gremios": ["fontanero"]
                }
                """.formatted(fijo);
    }

    private static String cuerpoProfesional() {
        return """
                {
                  "nombre": "Fontaneria It",
                  "email": "it-pro-%s@example.com",
                  "gremios": ["fontanero"]
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));
    }
}
