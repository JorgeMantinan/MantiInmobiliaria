package es.manti.profesionales.application;

import es.manti.shared.notificaciones.NotificacionesProperties;

import es.manti.profesionales.domain.Gremio;
import es.manti.profesionales.domain.NotificacionOutbox;
import es.manti.profesionales.domain.NotificacionOutboxRepository;
import es.manti.profesionales.domain.ProfesionalRepository;
import es.manti.profesionales.domain.Solicitud;
import es.manti.shared.notificaciones.NotificacionesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Escritura y marcado de las filas del outbox de notificaciones.
 *
 * <p>{@link #encolarParaSolicitud} corre DENTRO de la transacción de la
 * solicitud (misma conexión, mismo commit). Los tres marcados de estado sólo
 * se usan desde el despachador y abren SU propia transacción corta: la llamada
 * HTTP al proveedor ocurre SIEMPRE entre transacciones (skill rendimiento).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionOutboxService {

    private final NotificacionOutboxRepository repository;
    private final ProfesionalRepository profesionales;
    private final NotificacionesProperties propiedades;

    /**
     * Encola las notificaciones de una solicitud recién creada, en la MISMA
     * transacción:
     *
     * <ul>
     *   <li><b>EMAIL</b>: UNA fila por cada profesional ACTIVO que comparta al
     *       menos un gremio pedido. La query es {@code SELECT DISTINCT}, así
     *       que un profesional con varios gremios en común no duplica filas.</li>
     *   <li><b>TELEGRAM</b>: UNA sola fila con el listado de gremios en el
     *       cuerpo (&#8220;[fontanero, albañil]&#8221;), SÓLO si hay
     *       {@code TELEGRAM_CHAT_ID} configurado (si no, no se encola nada
     *       para no mandar mensajes sin destino). Una fila por gremio mandaría
     *       N mensajes idénticos al grupo.</li>
     * </ul>
     */
    @Transactional
    public void encolarParaSolicitud(Solicitud solicitud) {
        Set<Gremio> pedidos = solicitud.getGremios();
        if (pedidos.isEmpty()) {
            return;
        }
        String asunto = "Nueva solicitud en " + solicitud.getMunicipio();

        List<Long> gremioIds = pedidos.stream().map(Gremio::getId).toList();
        List<NotificacionOutbox> emails = profesionales.activosCompartiendoGremios(gremioIds).stream()
                .map(profesional -> NotificacionOutbox.email(
                        solicitud, profesional, asunto, cuerpoEmail(solicitud)))
                .toList();
        if (!emails.isEmpty()) {
            repository.guardarTodas(emails);
        }

        if (propiedades.telegram().tieneChat()) {
            // UNA fila por solicitud: el cuerpo ya lista todos los gremios pedidos.
            repository.guardar(NotificacionOutbox.telegram(
                    solicitud, propiedades.telegram().chatId(), asunto, cuerpoTelegram(solicitud)));
            log.debug("Solicitud {}: fila de Telegram encolada", solicitud.getId());
        } else {
            log.debug("TELEGRAM_CHAT_ID vacío: la solicitud {} no encola avisos de Telegram",
                    solicitud.getId());
        }
    }

    /**
     * Lote de pendientes más antiguas, bloqueado con
     * {@code FOR UPDATE SKIP LOCKED}: si dos instancias del despachador corren
     * a la vez, la segunda salta las filas que la primera ya ha cogido.
     *
     * <p>NO es {@code readOnly}: la query toma bloqueos de fila y no queremos
     * depender de si el driver admite {@code FOR UPDATE} dentro de una
     * transacción de sólo lectura. Los bloqueos viven sólo durante esta
     * lectura corta — el envío SIEMPRE ocurre después, fuera de transacción
     * (skill rendimiento); la exclusividad total entre instancias llegaría con
     * un estado de reclamación de la fila.</p>
     */
    @Transactional
    public List<NotificacionOutbox> pendientes(int lote) {
        return repository.pendientes(lote);
    }

    /** Transacción propia: el envío ya se hizo fuera de ella. */
    @Transactional
    public void marcarEnviada(NotificacionOutbox fila) {
        repository.marcarEnviada(fila.getId(), fila.getIntentos() + 1);
    }

    /**
     * Transacción propia: +1 intento y error guardado; vuelve a PENDIENTE si
     * quedan reintentos y no es definitivo, o cae a FALLIDA.
     *
     * @param ultimoError mensaje ya truncado a 500 caracteres (ancho de la columna)
     * @param fallida     true para FAIL definitiva (sin más reintentos)
     */
    @Transactional
    public void marcarFalla(NotificacionOutbox fila, String ultimoError, boolean fallida) {
        repository.marcarFalla(fila.getId(), fila.getIntentos() + 1, ultimoError, fallida);
    }

    // ------------------------------------------------------------------
    // Cuerpos de los mensajes (puro, sin red ni BD)
    // ------------------------------------------------------------------

    private static String cuerpoEmail(Solicitud solicitud) {
        String contacto = solicitud.getTelefono() == null || solicitud.getTelefono().isBlank()
                ? solicitud.getEmailContacto()
                : solicitud.getEmailContacto() + " · " + solicitud.getTelefono();
        return """
                <p>Has recibido una nueva solicitud de servicio en \
                <strong>%s</strong> para <strong>%s</strong>.</p>\
                <p><strong>Descripción:</strong> %s</p>\
                <p><strong>Contacto:</strong> %s · %s</p>\
                """.formatted(
                escaparHtml(solicitud.getMunicipio()),
                escaparHtml(gremiosEnTexto(solicitud.getGremios())),
                escaparHtml(solicitud.getDescripcion()),
                escaparHtml(solicitud.getNombreContacto()),
                escaparHtml(contacto));
    }

    private static String cuerpoTelegram(Solicitud solicitud) {
        return "Nueva solicitud en %s [%s]: %s (%s)".formatted(
                solicitud.getMunicipio(),
                gremiosEnTexto(solicitud.getGremios()),
                solicitud.getDescripcion(),
                solicitud.getEmailContacto());
    }

    /** Slugs pedidos, unidos con coma y en el orden del catálogo. */
    private static String gremiosEnTexto(Set<Gremio> gremios) {
        return gremios.stream()
                .map(Gremio::getSlug)
                .collect(Collectors.joining(", "));
    }

    /** El HTML del email se construye con datos del usuario: SIEMPRE escapado. */
    private static String escaparHtml(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(valor.length() + 16);
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
