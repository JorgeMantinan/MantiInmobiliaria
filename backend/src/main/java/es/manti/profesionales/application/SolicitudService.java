package es.manti.profesionales.application;

import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.Solicitud;
import es.manti.profesionales.domain.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Casos de uso del ciclo de vida de una solicitud de servicio.
 *
 * <p>El alta es UNA sola transacción: se guarda la solicitud con sus gremios y
 * se encolan las filas del outbox de notificaciones ({@link NotificacionOutboxService}),
 * siguiendo el patrón transaccional outbox de la skill {@code microservicios}.
 * Si la transacción hace rollback, tampoco hay notificaciones "fantasma".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudRepository solicitudes;
    private final GremioService gremios;
    private final NotificacionOutboxService outbox;

    /**
     * Alta de una solicitud pública (anónima, con rate limit por IP).
     *
     * @return la solicitud creada en estado {@code NUEVA}
     * @throws es.manti.profesionales.domain.GremioInexistenteException 400 si
     *         algún gremio pedido no existe
     */
    @Transactional
    public Solicitud crear(NuevaSolicitudCommand comando) {
        List<Gremio> pedidos = gremios.resolver(comando.gremios());
        Solicitud solicitud = solicitudes.guardar(Solicitud.nueva(
                comando.nombreContacto(), comando.emailContacto(), comando.telefono(),
                comando.municipio(), comando.descripcion(), pedidos));
        // Misma transacción: sin commit no hay filas de notificación.
        outbox.encolarParaSolicitud(solicitud);
        log.info("Solicitud {} creada en {} ({} gremio(s))",
                solicitud.getId(), solicitud.getMunicipio(), pedidos.size());
        return solicitud;
    }

    /**
     * Las últimas solicitudes para el backoffice (MVP sin paginación: se piden
     * como máximo 50). Los gremios se resuelven en UNA sola query.
     */
    @Transactional(readOnly = true)
    public List<SolicitudResumen> listarUltimas(int limite) {
        List<Solicitud> filas = solicitudes.ultimas(limite);
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> gremiosPedidos = solicitudes.slugsDeGremios(
                filas.stream().map(Solicitud::getId).toList());
        return filas.stream()
                .map(fila -> new SolicitudResumen(
                        fila.getId(), fila.getNombreContacto(), fila.getEmailContacto(),
                        fila.getTelefono(), fila.getMunicipio(), fila.getDescripcion(),
                        fila.getEstado(), gremiosPedidos.getOrDefault(fila.getId(), List.of()),
                        fila.getCreadoEn()))
                .toList();
    }
}
