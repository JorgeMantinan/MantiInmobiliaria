package es.manti.inmuebles.domain;

import java.util.Collection;
import java.util.Map;

/**
 * Puerto de salida para los avisos de alertas: lee los destinatarios y encola
 * en el outbox COMPARTIDO con el módulo profesionales.
 *
 * <p><b>Esquema compartido, código no compartido</b> (mismo criterio que el
 * deck de swipes sobre la tabla {@code inmuebles}): la implementación escribe
 * la tabla {@code notificaciones_outbox} con SQL nativo y NUNCA importa las
 * clases de {@code profesionales.domain} — el despachador existente recoge las
 * filas por su estado, sin distinguir el origen.</p>
 */
public interface AlertaNotificacionRepository {

    /**
     * Emails de los usuarios dados de alta y ACTIVOS, resueltos en UNA sola
     * query (follow-up #98: antes una query por alerta, un N+1 innecesario en
     * el job). El mapa sólo contiene las entradas con email; un id ausente es
     * un usuario inactivo o borrado y su alerta no se avisa.
     */
    Map<Long, String> emailsDeUsuarios(Collection<Long> usuarioIds);

    /** Encola una fila PENDIENTE en el outbox (en la transacción del job). */
    void encolar(FilaAvisoAlerta fila);
}
