package es.manti.profesionales.domain;

import java.util.List;

/**
 * Puerto de persistencia del outbox de notificaciones.
 *
 * <p>Las escrituras de estado ({@link #marcarEnviada}, {@link #marcarFalla})
 * corren en la TRANSACCIÓN PROPIA que abre el servicio, nunca dentro de la
 * llamada HTTP al proveedor (skill rendimiento).</p>
 */
public interface NotificacionOutboxRepository {

    NotificacionOutbox guardar(NotificacionOutbox fila);

    /** Varios emails en UNA sola llamada (sin `save()` en bucle). */
    List<NotificacionOutbox> guardarTodas(List<NotificacionOutbox> filas);

    /** Lote de filas PENDIENTE más antiguas, en el orden exacto del índice parcial. */
    List<NotificacionOutbox> pendientes(int lote);

    /** Éxito: estado ENVIADA, {@code enviado_en}, sin error y +1 intento. */
    void marcarEnviada(Long id, int intentos);

    /** Fracaso: +1 intento y error truncado; estado FALLIDA o PENDIENTE. */
    void marcarFalla(Long id, int intentos, String ultimoError, boolean fallida);
}
