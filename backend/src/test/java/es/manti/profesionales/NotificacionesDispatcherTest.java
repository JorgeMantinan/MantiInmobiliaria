package es.manti.profesionales;

import es.manti.profesionales.application.NotificacionesDispatcher;
import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.shared.notificaciones.NotificacionException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Despachador del outbox: envía por los PUERTOS (mockeados — cero red en la
 * suite) y marca cada fila en SU propia transacción.
 *
 * <p>Propiedades propias de esta clase: notificaciones ACTIVAS (el perfil test
 * las trae apagadas) y planificador desactivado, de modo que el único que
 * despacha es la propia prueba.</p>
 */
@SpringBootTest(properties = "manti.notificaciones.enabled=true")
@ActiveProfiles("test")
class NotificacionesDispatcherTest {

    private static final String DESTINO = "it-pro-dispatch@example.com";
    private static final String CHAT_ID = "-100123456";

    @MockitoBean
    private EmailNotificadorPort email;

    @MockitoBean
    private TelegramNotificadorPort telegram;

    @Autowired
    private NotificacionesDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    private long solicitud;
    private long profesional;

    @BeforeEach
    void fixtures() {
        // Estado limpio: mocks y outbox de las pruebas anteriores (sólo fixtures
        // it-*: nunca se borra el outbox de datos ajenos al test)
        reset(email, telegram);
        jdbc.update("DELETE FROM notificaciones_outbox WHERE solicitud_id IN "
                + "(SELECT id FROM solicitudes WHERE email_contacto LIKE 'it-%@example.com')");
        profesional = FixturesProfesionales.profesional(
                jdbc, DESTINO, "ACTIVO", "fontanero");
        solicitud = FixturesProfesionales.solicitud(
                jdbc, "it-sol-dispatch-" + fijo() + "@example.com", "fontanero");
    }

    @AfterEach
    void limpiar() {
        FixturesProfesionales.limpiar(jdbc);
    }

    @Test
    void despachar_conEnvioOk_marcaLaFilaComoEnviada() {
        long fila = filaEmail();

        dispatcher.despachar();

        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("ENVIADA");
        assertThat(campo(fila, "enviado_en")).isNotNull();
        assertThat(campo(fila, "ultimo_error")).isNull();
        assertThat(entero(fila, "intentos")).isEqualTo(1);
        verify(email).enviar(eq(DESTINO), eq("Asunto de prueba"), contains("Cuerpo de prueba"));
    }

    @Test
    void despachar_conFalloReintentable_vuelveAPendienteHastaElTercerIntento() {
        long fila = filaEmail();
        doThrow(NotificacionException.reintentable("caida de red"))
                .when(email).enviar(any(), any(), any());

        dispatcher.despachar();
        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("PENDIENTE");
        assertThat(entero(fila, "intentos")).isEqualTo(1);

        dispatcher.despachar();
        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("PENDIENTE");
        assertThat(entero(fila, "intentos")).isEqualTo(2);

        dispatcher.despachar();
        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("FALLIDA");
        assertThat(entero(fila, "intentos")).isEqualTo(3);
        assertThat(campo(fila, "ultimo_error")).isEqualTo("caida de red");
    }

    @Test
    void despachar_conErrorDefinitivo_marcaFallidaEnElPrimerIntento() {
        long fila = filaEmail();
        doThrow(NotificacionException.definitiva("BREVO_API_KEY sin configurar"))
                .when(email).enviar(any(), any(), any());

        dispatcher.despachar();

        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("FALLIDA");
        assertThat(entero(fila, "intentos")).isEqualTo(1);
        assertThat(campo(fila, "ultimo_error")).isEqualTo("BREVO_API_KEY sin configurar");
    }

    @Test
    void despachar_conFilaDeTelegram_usaElPuertoDeTelegram() {
        long fila = filaTelegram();

        dispatcher.despachar();

        assertThat(FixturesProfesionales.estadoOutbox(jdbc, fila)).isEqualTo("ENVIADA");
        verify(telegram).enviar(eq(CHAT_ID), anyString());
        verify(email, never()).enviar(any(), any(), any());
    }

    @Test
    void despachar_conErrorMuyLargo_truncaElUltimoErrorA500() {
        long fila = filaEmail();
        doThrow(NotificacionException.reintentable("e".repeat(900)))
                .when(email).enviar(any(), any(), any());

        dispatcher.despachar();

        assertThat(campo(fila, "ultimo_error")).isEqualTo("e".repeat(500));
    }

    @Test
    void despachar_sinFilasPendientes_noLlamaANingunPuerto() {
        dispatcher.despachar();

        verify(email, never()).enviar(any(), any(), any());
        verify(telegram, never()).enviar(any(), any());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long filaEmail() {
        return jdbc.queryForObject("""
                INSERT INTO notificaciones_outbox
                    (solicitud_id, canal, destinatario_tipo, destinatario_id, destino,
                     asunto, cuerpo, estado, intentos)
                VALUES (?, 'EMAIL', 'PROFESIONAL', ?, ?, 'Asunto de prueba',
                        '<p>Cuerpo de prueba</p>', 'PENDIENTE', 0)
                RETURNING id
                """, Long.class, solicitud, profesional, DESTINO);
    }

    private long filaTelegram() {
        return jdbc.queryForObject("""
                INSERT INTO notificaciones_outbox
                    (solicitud_id, canal, destinatario_tipo, destinatario_id, destino,
                     asunto, cuerpo, estado, intentos)
                VALUES (?, 'TELEGRAM', 'GRUPO', NULL, ?, 'Asunto de prueba',
                        'Nueva solicitud en It-Municipio', 'PENDIENTE', 0)
                RETURNING id
                """, Long.class, solicitud, CHAT_ID);
    }

    private Object campo(long fila, String columna) {
        return jdbc.queryForObject(
                "SELECT " + columna + " FROM notificaciones_outbox WHERE id = ?",
                Object.class, fila);
    }

    private int entero(long fila, String columna) {
        return ((Number) campo(fila, columna)).intValue();
    }

    private static String fijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
