package es.manti.profesionales.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia del outbox de notificaciones.
 *
 * <p>Las escrituras de estado ({@link #marcarEnviada}, {@link #marcarFalla})
 * corren en la TRANSACCIÓN PROPIA que abre el servicio, nunca dentro de la
 * llamada HTTP al proveedor (skill rendimiento).</p>
 */
public interface NotificacionOutboxRepository {

    NotificacionOutbox guardar(NotificacionOutbox fila);

    /** Lote de filas PENDIENTE más antiguas, en el orden exacto del índice parcial. */
    List<NotificacionOutbox> pendientes(int lote);

    Optional<NotificacionOutbox> porId(Long id);

    /** Éxito: estado ENVIADA, {@code enviado_en}, sin error y +1 intento. */
    void marcarEnviada(Long id, int intentos);

    /** Fracaso: +1 intento y error truncado; estado FALLIDA o PENDIENTE. */
    void marcarFalla(Long id, int intentos, String ultimoError, boolean fallida);
}
