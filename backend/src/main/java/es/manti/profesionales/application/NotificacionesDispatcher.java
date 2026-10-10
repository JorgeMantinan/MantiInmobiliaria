package es.manti.profesionales.application;

import es.manti.shared.notificaciones.EmailNotificadorPort;
import es.manti.shared.notificaciones.NotificacionException;
import es.manti.shared.notificaciones.NotificacionesProperties;
import es.manti.profesionales.domain.NotificacionOutbox;
import es.manti.profesionales.domain.TelegramNotificadorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Despachador del outbox: cada 15 s coge hasta 20 filas PENDIENTE y las envía
 * por su canal.
 *
 * <p><b>Las llamadas a Brevo/Telegram ocurren FUERA de cualquier
 * transacción</b> (skill rendimiento):</p>
 * <ol>
 *   <li>transacción de lectura corta que trae el lote;</li>
 *   <li>envío de cada fila sin transacción abierta;</li>
 *   <li>transacción propia por fila que la marca ENVIADA o FALLIDA.</li>
 * </ol>
 *
 * <p>Guard global {@code manti.notificaciones.enabled}: apagado (local y CI)
 * no lee ni envía nada, sólo log de debug. Apagado no significa que las filas
 * se pierdan: siguen en PENDIENTE y se enviarán al activarlo.</p>
 *
 * <p>Al ser un envío <i>at-least-once</i>, si el proceso muere tras el envío y
 * antes del marcado, la fila se reenvía; los proveedores son idempotentes a
 * efectos prácticos (un email duplicado puntual es preferible a uno perdido).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionesDispatcher {

    /** Tamaño del lote por pasada (ticket FASE 5). */
    static final int LOTE = 20;

    /** Tras 3 intentos fallidos la fila queda FALLIDA para siempre. */
    static final int MAXIMO_INTENTOS = 3;

    /** Ancho de la columna {@code ultimo_error} (varchar 500). */
    static final int LARGO_MAXIMO_ERROR = 500;

    private final NotificacionOutboxService outbox;
    private final EmailNotificadorPort email;
    private final TelegramNotificadorPort telegram;
    private final NotificacionesProperties propiedades;

    @Scheduled(fixedDelay = 15000)
    public void despachar() {
        if (!propiedades.enabled()) {
            log.debug("Notificaciones deshabilitadas (manti.notificaciones.enabled=false): "
                    + "se omite el envío de las filas pendientes");
            return;
        }
        List<NotificacionOutbox> lote = outbox.pendientes(LOTE);
        if (lote.isEmpty()) {
            return;
        }
        log.debug("Despachando {} notificación(es) pendiente(s)", lote.size());
        for (NotificacionOutbox fila : lote) {
            enviar(fila);
        }
    }

    /** Envía UNA fila y registra el resultado. Sin transacción activa. */
    private void enviar(NotificacionOutbox fila) {
        boolean enviado = false;
        boolean reintentable = true;
        String error = null;
        try {
            enviarPorCanal(fila);
            enviado = true;
        } catch (NotificacionException fallo) {
            error = limpiarError(fallo.getMessage());
            reintentable = fallo.esReintentable();
        } catch (RuntimeException inesperado) {
            // Caída de red, timeout u otro fallo no previsto: siempre se puede
            // reintentar (si persiste, a la 3ª la fila cae a FALLIDA).
            error = limpiarError(String.valueOf(inesperado));
        }

        if (enviado) {
            outbox.marcarEnviada(fila);
            log.debug("Notificación {} enviada por {}", fila.getId(), fila.getCanal());
            return;
        }
        int intentos = fila.getIntentos() + 1;
        boolean fallida = intentos >= MAXIMO_INTENTOS || !reintentable;
        outbox.marcarFalla(fila, error, fallida);
        log.warn("Notificación {} no enviada (intento {}/{}, canal {}): {}",
                fila.getId(), intentos, MAXIMO_INTENTOS, fila.getCanal(), error);
    }

    private void enviarPorCanal(NotificacionOutbox fila) {
        if (fila.getCanal() == null) {
            throw NotificacionException.definitiva("canal de la fila " + fila.getId() + " ausente");
        }
        switch (fila.getCanal()) {
            case EMAIL -> email.enviar(fila.getDestino(), fila.getAsunto(), fila.getCuerpo());
            case TELEGRAM -> telegram.enviar(fila.getDestino(), fila.getCuerpo());
        }
    }

    /**
     * El error se guarda en {@code ultimo_error} (500) y se escribe en el log:
     * antes de hacerlo se retiran las credenciales por si un mensaje de error
     * las arrastrara (el token de Telegram viaja en la URI, la clave de Brevo
     * en una cabecera).
     */
    private String limpiarError(String mensaje) {
        String texto = mensaje == null || mensaje.isBlank() ? "error sin mensaje" : mensaje;
        texto = ocultar(texto, propiedades.telegram().botToken());
        texto = ocultar(texto, propiedades.brevo().apiKey());
        return texto.length() > LARGO_MAXIMO_ERROR
                ? texto.substring(0, LARGO_MAXIMO_ERROR)
                : texto;
    }

    private static String ocultar(String texto, String secreto) {
        return secreto == null || secreto.isBlank() ? texto : texto.replace(secreto, "***");
    }
}
