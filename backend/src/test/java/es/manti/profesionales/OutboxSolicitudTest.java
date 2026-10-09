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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outbox transaccional del alta de una solicitud (patrón microservicios):
 * las filas se escriben en la MISMA transacción que la solicitud y sólo para
 * los profesionales ACTIVOS que comparten gremios pedidos.
 *
 * <p>{@code TELEGRAM_CHAT_ID} se fuerza a vacío con una propiedad inline para
 * que el resultado NO dependa del {@code .env} de cada máquina.</p>
 */
@SpringBootTest(properties = "TELEGRAM_CHAT_ID=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxSolicitudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        FixturesProfesionales.limpiar(jdbc);
    }

    /**
     * Todas las aserciones se limitan a los PROFESIONALES de las fixtures:
     * el outbox de una solicitud incluye legítimamente a todo profesional
     * ACTIVO que comparta gremio, también a los que existan en la BD de
     * desarrollo (datos reales de la máquina, no del test).
     */
    @Test
    void crear_soloEncolaEmailParaLosProfesionalesActivos() throws Exception {
        long activo = FixturesProfesionales.profesional(
                jdbc, "it-pro-activo-" + fijo() + "@example.com", "ACTIVO", "fontanero");
        long pendiente = FixturesProfesionales.profesional(
                jdbc, "it-pro-pendiente-" + fijo() + "@example.com", "PENDIENTE", "fontanero");
        long rechazado = FixturesProfesionales.profesional(
                jdbc, "it-pro-rechazado-" + fijo() + "@example.com", "RECHAZADO", "fontanero");

        long solicitud = crearSolicitud("fontanero");

        assertThat(FixturesProfesionales.contarOutboxDestinatario(jdbc, solicitud, "EMAIL", activo))
                .isEqualTo(1);
        assertThat(FixturesProfesionales.destinatariosDeEmail(jdbc, solicitud))
                .contains(activo)
                .doesNotContain(pendiente, rechazado);
        // Sin TELEGRAM_CHAT_ID no se encola NADA de Telegram
        assertThat(FixturesProfesionales.contarOutbox(jdbc, solicitud, "TELEGRAM")).isZero();
    }

    @Test
    void crear_profesionalConVariosGremiosEnComun_generaUnaSolaFilaEmail() throws Exception {
        long activo = FixturesProfesionales.profesional(
                jdbc, "it-pro-dedupe-" + fijo() + "@example.com", "ACTIVO",
                "fontanero", "albañil");

        long solicitud = crearSolicitud("fontanero", "albañil");

        // Dos gremios en común, pero UNA sola notificación para este profesional
        assertThat(FixturesProfesionales.contarOutboxDestinatario(jdbc, solicitud, "EMAIL", activo))
                .isEqualTo(1);
        assertThat(FixturesProfesionales.destinatariosDeEmail(jdbc, solicitud))
                .contains(activo);
    }

    @Test
    void crear_profesionalSinGremiosPedidos_noRecibeNada() throws Exception {
        long pintor = FixturesProfesionales.profesional(
                jdbc, "it-pro-pintor-" + fijo() + "@example.com", "ACTIVO", "pintor");

        long solicitud = crearSolicitud("fontanero");

        assertThat(FixturesProfesionales.contarOutboxDestinatario(jdbc, solicitud, "EMAIL", pintor))
                .isZero();
        // Sin chat de Telegram no hay fila de GRUPO, con o sin profesionales
        assertThat(FixturesProfesionales.contarOutbox(jdbc, solicitud, "TELEGRAM")).isZero();
    }

    @Test
    void crear_sinChatIdDeTelegram_noGeneraNingunaFilaDeTelegram() throws Exception {
        long activo = FixturesProfesionales.profesional(
                jdbc, "it-pro-telegram-" + fijo() + "@example.com", "ACTIVO",
                "fontanero", "albañil");

        long solicitud = crearSolicitud("fontanero", "albañil");

        assertThat(FixturesProfesionales.contarOutbox(jdbc, solicitud, "TELEGRAM")).isZero();
        // ...pero el email del profesional activo SÍ se ha encolado
        assertThat(FixturesProfesionales.contarOutboxDestinatario(jdbc, solicitud, "EMAIL", activo))
                .isEqualTo(1);
    }

    @Test
    void crear_lasFilasDelOutboxQuedanPendientesYConCuerpo() throws Exception {
        long activo = FixturesProfesionales.profesional(
                jdbc, "it-pro-cuerpo-" + fijo() + "@example.com", "ACTIVO", "fontanero");

        long solicitud = crearSolicitud("fontanero");

        List<String> estados = jdbc.queryForList(
                "SELECT estado FROM notificaciones_outbox WHERE solicitud_id = ? "
                        + "AND destinatario_id = ?",
                String.class, solicitud, activo);
        assertThat(estados).containsExactly("PENDIENTE");

        String asunto = jdbc.queryForObject(
                "SELECT asunto FROM notificaciones_outbox WHERE solicitud_id = ? "
                        + "AND destinatario_id = ?",
                String.class, solicitud, activo);
        assertThat(asunto).startsWith("Nueva solicitud en It-");

        String cuerpo = jdbc.queryForObject(
                "SELECT cuerpo FROM notificaciones_outbox WHERE solicitud_id = ? "
                        + "AND destinatario_id = ?",
                String.class, solicitud, activo);
        assertThat(cuerpo).contains("fontanero").contains("it-sol-");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long crearSolicitud(String... gremios) throws Exception {
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
                """.formatted(fijo(), lista);
        String respuesta = mockMvc.perform(post("/api/v1/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("NUEVA"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(respuesta, "$.id")).longValue();
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
