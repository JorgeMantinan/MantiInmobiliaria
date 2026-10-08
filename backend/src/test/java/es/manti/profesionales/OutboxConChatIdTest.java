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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caso con {@code TELEGRAM_CHAT_ID} configurado: el alta de una solicitud
 * encola UNA fila de Telegram por cada gremio pedido (además de los emails de
 * los profesionales activos).
 */
@SpringBootTest(properties = "TELEGRAM_CHAT_ID=-100123456")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxConChatIdTest {

    private static final String CHAT_ID = "-100123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        FixturesProfesionales.limpiar(jdbc);
    }

    @Test
    void crear_generaUnaFilaDeTelegramPorGremioYLosEmailsDeLosActivos() throws Exception {
        long activo = FixturesProfesionales.profesional(
                jdbc, "it-pro-chat-" + fijo() + "@example.com", "ACTIVO", "fontanero", "albañil");

        long solicitud = crearSolicitud("fontanero", "albañil");

        assertThat(FixturesProfesionales.contarOutbox(jdbc, solicitud, "TELEGRAM")).isEqualTo(2);
        assertThat(FixturesProfesionales.destinatariosDeEmail(jdbc, solicitud))
                .containsExactly(activo);

        List<String> destinos = jdbc.queryForList(
                "SELECT destino FROM notificaciones_outbox WHERE solicitud_id = ? AND canal = 'TELEGRAM'",
                String.class, solicitud);
        assertThat(destinos).containsOnly(CHAT_ID);

        // GRUPO sin destinatario_id: lo garantiza también ck_notificaciones_outbox_destinatario
        Integer sinDestinatario = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE solicitud_id = ? "
                        + "AND canal = 'TELEGRAM' AND destinatario_id IS NULL "
                        + "AND destinatario_tipo = 'GRUPO'",
                Integer.class, solicitud);
        assertThat(sinDestinatario).isEqualTo(2);
    }

    @Test
    void crear_elCuerpoDeTelegramSigueLaPlantillaDeLaFase5() throws Exception {
        FixturesProfesionales.profesional(
                jdbc, "it-pro-plantilla-" + fijo() + "@example.com", "ACTIVO", "fontanero");

        long solicitud = crearSolicitud("fontanero");

        String cuerpo = jdbc.queryForObject(
                "SELECT cuerpo FROM notificaciones_outbox WHERE solicitud_id = ? AND canal = 'TELEGRAM'",
                String.class, solicitud);
        assertThat(cuerpo).isEqualTo(
                "Nueva solicitud en It-Municipio [fontanero]: "
                        + "Se ha roto la tubería del baño y hay una fuga continua. "
                        + "(it-sol-" + sufijoUltimaSolicitud(solicitud) + "@example.com)");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String sufijoUltimaSolicitud(long solicitud) {
        String email = jdbc.queryForObject("SELECT email_contacto FROM solicitudes WHERE id = ?",
                String.class, solicitud);
        return email.substring("it-sol-".length(), email.indexOf('@'));
    }

    private long crearSolicitud(String... gremios) throws Exception {
        String sufijo = fijo();
        String lista = java.util.Arrays.stream(gremios)
                .map(gremio -> "\"" + gremio + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String cuerpo = """
                {
                  "nombreContacto": "Contacto It",
                  "emailContacto": "it-sol-%s@example.com",
                  "municipio": "It-Municipio",
                  "descripcion": "Se ha roto la tubería del baño y hay una fuga continua.",
                  "gremios": [%s]
                }
                """.formatted(sufijo, lista);
        String respuesta = mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(respuesta, "$.id")).longValue();
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
