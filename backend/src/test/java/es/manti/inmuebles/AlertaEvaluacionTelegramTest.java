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
 * Caso con {@code TELEGRAM_CHAT_ID} configurado: además del email al dueño, la
 * evaluación de una alerta encola UNA fila de Telegram al grupo (mismo patrón
 * que las solicitudes del módulo profesionales), con el enlace al listado.
 *
 * <p>Decisión MVP del issue #96: no existe chat_id por usuario, así que el
 * aviso de Telegram va al grupo configurado; si algún día hay preferencia
 * personal, sólo cambia el destino de esta fila.</p>
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
    void evaluar_encolaEmailAlDueñoYTelegramAlGrupo() {
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

        Map<String, Object> telegramFila = filas.get(1);
        assertThat(telegramFila.get("canal")).isEqualTo("TELEGRAM");
        assertThat(telegramFila.get("destinatario_tipo")).isEqualTo("GRUPO");
        assertThat(telegramFila.get("destino")).isEqualTo(CHAT_ID);
        assertThat(telegramFila.get("solicitud_id")).isNull();
        // Texto plano (no HTML): el grupo recibe resumen + enlace.
        assertThat(telegramFila.get("cuerpo").toString())
                .startsWith("Alerta 'Con Telegram': 1 viviendas nuevas. Ver: ")
                .contains("/inmuebles?estado=VENTA");
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
