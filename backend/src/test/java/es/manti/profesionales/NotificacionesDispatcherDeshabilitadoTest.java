package es.manti.profesionales;

import es.manti.profesionales.application.NotificacionesDispatcher;
import es.manti.profesionales.domain.EmailNotificadorPort;
import es.manti.profesionales.domain.TelegramNotificadorPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guard global {@code manti.notificaciones.enabled=false} (el valor por
 * defecto, el del perfil test y el de local): el despachador ni siquiera lee
 * el outbox, así que no spamea proveedores ni gasta ciclos. Las filas siguen
 * PENDIENTE y se enviarán al activarlo.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificacionesDispatcherDeshabilitadoTest {

    @MockitoBean
    private EmailNotificadorPort email;

    @MockitoBean
    private TelegramNotificadorPort telegram;

    @Autowired
    private NotificacionesDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    private long fila;

    @BeforeEach
    void fixtures() {
        reset(email, telegram);
        // Sólo fixtures it-*: nunca se borra el outbox de datos ajenos al test
        jdbc.update("DELETE FROM notificaciones_outbox WHERE solicitud_id IN "
                + "(SELECT id FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com')");
        long profesional = FixturesProfesionales.profesional(
                jdbc, "it-pro-off-" + UUID.randomUUID() + "@example.com", "ACTIVO", "fontanero");
        long solicitud = FixturesProfesionales.solicitud(
                jdbc, "it-sol-off-" + UUID.randomUUID() + "@example.com", "fontanero");
        fila = jdbc.queryForObject("""
                INSERT INTO notificaciones_outbox
                    (solicitud_id, canal, destinatario_tipo, destinatario_id, destino,
                     asunto, cuerpo, estado, intentos)
                VALUES (?, 'EMAIL', 'PROFESIONAL', ?, 'it-pro-off@example.com',
                        'Asunto', '<p>Cuerpo</p>', 'PENDIENTE', 0)
                RETURNING id
                """, Long.class, solicitud, profesional);
    }

    @AfterEach
    void limpiar() {
        FixturesProfesionales.limpiar(jdbc);
    }

    @Test
    void despachar_conNotificacionesDeshabilitadas_noLlamaAPuertosNiTocaLasFilas() {
        dispatcher.despachar();

        verifyNoInteractions(email, telegram);
        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject(
                "SELECT intentos FROM notificaciones_outbox WHERE id = ?", Short.class, fila))
                .isZero();
    }
}
