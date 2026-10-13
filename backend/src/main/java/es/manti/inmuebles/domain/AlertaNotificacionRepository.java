package es.manti.inmuebles.domain;

import java.util.Optional;

/**
 * Puerto de salida para los avisos de alertas: lee el destinatario y encola
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
     * Email del usuario dueño de la alerta; vacío si el usuario no existe o
     * está inactivo (en ese caso el job no avisa ni gasta envíos).
     */
    Optional<String> emailDeUsuario(Long usuarioId);

    /** Encola una fila PENDIENTE en el outbox (en la transacción del job). */
    void encolar(FilaAvisoAlerta fila);
}
