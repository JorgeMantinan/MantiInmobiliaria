package es.manti.profesionales.application;

import es.manti.profesionales.domain.ClaveIdempotenciaConflictivaException;
import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.Solicitud;
import es.manti.profesionales.domain.SolicitudIdempotencia;
import es.manti.profesionales.domain.SolicitudIdempotenciaRepository;
import es.manti.profesionales.domain.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Casos de uso del ciclo de vida de una solicitud de servicio.
 *
 * <p>El alta es UNA sola transacción: se guarda la solicitud con sus gremios,
 * se encolan las filas del outbox de notificaciones
 * ({@link NotificacionOutboxService}) y — si el cliente envía
 * {@code Idempotency-Key} — se registra la clave gastada, siguiendo el patrón
 * transaccional outbox de la skill {@code microservicios} y la regla de
 * idempotencia de la skill {@code api-rest} #9. Si la transacción hace
 * rollback, tampoco quedan notificaciones ni claves "fantasma".</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudService {

    /** Código HTTP que se guarda como respuesta original del alta (201 Created). */
    static final int CODIGO_ALTA = 201;

    private final SolicitudRepository solicitudes;
    private final GremioService gremios;
    private final NotificacionOutboxService outbox;
    private final SolicitudIdempotenciaRepository idempotencia;

    /**
     * Alta de una solicitud pública (anónima, con rate limit por IP).
     *
     * @param comando         datos de la solicitud
     * @param claveIdempotencia cabecera {@code Idempotency-Key} opcional
     *                          ({@code null} o en blanco = sin idempotencia)
     * @return la solicitud creada (o la original, en replay) y el código HTTP
     * @throws es.manti.profesionales.domain.GremioInexistenteException 400 si
     *         algún gremio pedido no existe
     * @throws ClaveIdempotenciaConflictivaException 409 si la clave ya se usó
     *         con un cuerpo distinto
     */
    @Transactional
    public AltaSolicitud crear(NuevaSolicitudCommand comando, String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank()) {
            return new AltaSolicitud(alta(comando), CODIGO_ALTA);
        }
        Optional<SolicitudIdempotencia> registro = idempotencia.porClave(claveIdempotencia);
        if (registro.isPresent()) {
            return reenviar(comando, claveIdempotencia, registro.get());
        }
        Solicitud solicitud = alta(comando);
        // Misma transacción: la clave sólo queda gastada si la solicitud se guarda.
        idempotencia.guardar(
                new SolicitudIdempotencia(claveIdempotencia, solicitud.getId(), CODIGO_ALTA));
        log.debug("Clave de idempotencia {} asociada a la solicitud {}",
                claveIdempotencia, solicitud.getId());
        return new AltaSolicitud(solicitud, CODIGO_ALTA);
    }

    /** Crea la solicitud y encola su outbox en la MISMA transacción. */
    private Solicitud alta(NuevaSolicitudCommand comando) {
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
     * Replay de una clave ya gastada: devuelve la respuesta ORIGINAL (mismo id,
     * mismo código). Si el cuerpo de la petición no describe la misma operación,
     * se responde 409 en lugar de reutilizar un resultado ajeno.
     */
    private AltaSolicitud reenviar(NuevaSolicitudCommand comando, String clave,
                                   SolicitudIdempotencia registro) {
        Solicitud original = solicitudes.porId(registro.getSolicitudId())
                .orElseThrow(() -> ClaveIdempotenciaConflictivaException.sinSolicitud(clave));
        if (!mismoContenido(comando, original)) {
            throw ClaveIdempotenciaConflictivaException.cuerpoDistinto(clave);
        }
        log.debug("Replay de la clave {}: solicitud {} (código {})",
                clave, original.getId(), registro.getCodigo());
        return new AltaSolicitud(original, registro.getCodigo());
    }

    /**
     * ¿Describe la petición la MISMA operación que la original? Los gremios se
     * comparan como CONJUNTO normalizado (el orden no forma parte del pedido y
     * la entidad los guarda en un {@code Set}); el resto de campos, tal cual.
     */
    private static boolean mismoContenido(NuevaSolicitudCommand comando, Solicitud original) {
        return Objects.equals(comando.nombreContacto(), original.getNombreContacto())
                && Objects.equals(comando.emailContacto(), original.getEmailContacto())
                && Objects.equals(comando.telefono(), original.getTelefono())
                && Objects.equals(comando.municipio(), original.getMunicipio())
                && Objects.equals(comando.descripcion(), original.getDescripcion())
                && gremiosNormalizados(comando.gremios())
                       .equals(gremiosNormalizados(original.getGremios()));
    }

    private static Set<String> gremiosNormalizados(List<String> slugs) {
        Set<String> normalizados = new HashSet<>();
        if (slugs != null) {
            slugs.forEach(slug -> normalizados.add(Gremio.normalizarSlug(slug)));
        }
        return normalizados;
    }

    private static Set<String> gremiosNormalizados(Set<Gremio> pedidos) {
        Set<String> normalizados = new HashSet<>();
        pedidos.forEach(gremio -> normalizados.add(Gremio.normalizarSlug(gremio.getSlug())));
        return normalizados;
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
