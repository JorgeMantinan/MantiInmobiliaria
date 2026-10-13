package es.manti.inmuebles;

import es.manti.inmuebles.application.AlertaEvaluacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caso con {@code TELEGRAM_CHAT_ID} configurado (follow-up #98): además del
 * email AL DUEÑO por cada alerta, la evaluación encola UNA sola fila de
 * Telegram al grupo con el resumen agrupado de la pasada — antes una fila por
 * alerta, que inundaba el grupo de negocio.
 *
 * <p>La fila de Telegram se ancla en la PRIMERA alerta del lote
 * ({@code alerta_id}); el esquema de V13 exige un origen por fila.</p>
 */
@SpringBootTest(properties = "TELEGRAM_CHAT_ID=-100999")
@ActiveProfiles("test")
class AlertaEvaluacionTelegramTest {

    private static final String CHAT_ID = "-100999";

    @Autowired
    private AlertaEvaluacionService evaluacion;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarFixtures() {
        FixturesAlertas.limpiar(jdbc);
    }

    @Test
    void evaluar_encolaEmailAlDueñoYUnResumenDeTelegramAgrupado() {
        String email = "it-alerta-" + fijo() + "@example.com";
        long usuario = FixturesAlertas.usuario(jdbc, email);
        String municipio = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alerta = FixturesAlertas.alerta(jdbc, usuario, "Con Telegram", municipio,
                new BigDecimal("500000"), marca);
        FixturesAlertas.inmueble(jdbc, municipio, new BigDecimal("200000"));

        assertThat(evaluacion.evaluar()).isEqualTo(1);

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT * FROM notificaciones_outbox WHERE alerta_id = ? ORDER BY id", alerta);
        assertThat(filas).hasSize(2);

        Map<String, Object> emailFila = filas.get(0);
        assertThat(emailFila.get("canal")).isEqualTo("EMAIL");
        assertThat(emailFila.get("destinatario_tipo")).isEqualTo("USUARIO");
        assertThat(emailFila.get("destino")).isEqualTo(email);
        assertThat(emailFila.get("asunto").toString()).contains("Con Telegram");

        Map<String, Object> telegramFila = filas.get(1);
        assertThat(telegramFila.get("canal")).isEqualTo("TELEGRAM");
        assertThat(telegramFila.get("destinatario_tipo")).isEqualTo("GRUPO");
        assertThat(telegramFila.get("destino")).isEqualTo(CHAT_ID);
        assertThat(telegramFila.get("solicitud_id")).isNull();
        assertThat(telegramFila.get("asunto").toString())
                .isEqualTo("1 alertas han encontrado viviendas nuevas");
        // Texto plano (no HTML): resumen del lote, una línea por alerta.
        assertThat(telegramFila.get("cuerpo").toString())
                .startsWith("1 alertas han encontrado viviendas nuevas:")
                .contains("• Con Telegram: 1 viviendas nuevas. Ver: ")
                .contains("/inmuebles?estado=VENTA");
    }

    @Test
    void evaluar_dosAlertasAvisadas_unaFilaDeTelegramParaTodoElLote() {
        String emailA = "it-alerta-" + fijo() + "@example.com";
        String emailB = "it-alerta-" + fijo() + "@example.com";
        long usuarioA = FixturesAlertas.usuario(jdbc, emailA);
        long usuarioB = FixturesAlertas.usuario(jdbc, emailB);
        String municipioA = "It-alerta-" + fijo();
        String municipioB = "It-alerta-" + fijo();
        long marca = FixturesAlertas.maximoIdInmuebles(jdbc);
        long alertaA = FixturesAlertas.alerta(jdbc, usuarioA, "Pisos A", municipioA,
                new BigDecimal("500000"), marca);
        long alertaB = FixturesAlertas.alerta(jdbc, usuarioB, "Pisos B", municipioB,
                new BigDecimal("500000"), marca);
        FixturesAlertas.inmueble(jdbc, municipioA, new BigDecimal("200000"));
        FixturesAlertas.inmueble(jdbc, municipioB, new BigDecimal("250000"));

        assertThat(evaluacion.evaluar()).isEqualTo(2);

        // UN email por alerta a su dueño (sin cambio)…
        List<Map<String, Object>> emails = jdbc.queryForList(
                "SELECT * FROM notificaciones_outbox WHERE canal = 'EMAIL' ORDER BY alerta_id");
        assertThat(emails).hasSize(2);
        assertThat(emails.get(0).get("destino")).isEqualTo(emailA);
        assertThat(emails.get(1).get("destino")).isEqualTo(emailB);

        // …y UNA sola fila de Telegram agrupando las dos alertas.
        List<Map<String, Object>> telegram = jdbc.queryForList(
                "SELECT * FROM notificaciones_outbox WHERE canal = 'TELEGRAM'");
        assertThat(telegram).hasSize(1);
        Map<String, Object> fila = telegram.get(0);
        assertThat(((Number) fila.get("alerta_id")).longValue()).isEqualTo(alertaA);
        assertThat(fila.get("cuerpo").toString())
                .startsWith("2 alertas han encontrado viviendas nuevas:")
                .contains("• Pisos A: 1 viviendas nuevas. Ver: ")
                .contains("• Pisos B: 1 viviendas nuevas. Ver: ");
        // La alerta B sólo conserva SU email (el resumen no es suyo).
        Integer filasB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones_outbox WHERE alerta_id = ?",
                Integer.class, alertaB);
        assertThat(filasB).isEqualTo(1);
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
