package es.manti.profesionales.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Puerto de persistencia de solicitudes de servicio.
 */
public interface SolicitudRepository {

    Solicitud guardar(Solicitud solicitud);

    /** Las {@code limite} solicitudes más recientes, más nuevas primero. */
    List<Solicitud> ultimas(int limite);

    /**
     * Slugs de los gremios pedidos por cada solicitud, en UNA sola query
     * (anti-N+1 para el listado del backoffice).
     */
    Map<Long, List<String>> slugsDeGremios(Collection<Long> solicitudIds);
}
