package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.AlertaBusquedaRepository;
import es.manti.inmuebles.domain.AlertaNotificacionRepository;
import es.manti.inmuebles.domain.CanalAvisoAlerta;
import es.manti.inmuebles.domain.FilaAvisoAlerta;
import es.manti.inmuebles.domain.InmuebleFiltros;
import es.manti.inmuebles.domain.InmuebleRepository;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.shared.notificaciones.NotificacionesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluación de UN lote de alertas del barrido (follow-up #98): la carga
 * completa del job vive en {@link AlertaEvaluacionService}, que pagina las
 * activas y llama a este servicio — cada lote en SU propia transacción corta,
 * en lugar de una única transacción con todas las alertas.
 *
 * <p>Dentro del lote:</p>
 * <ul>
 *   <li>el {@code SELECT ... FOR UPDATE SKIP LOCKED} del repositorio evita que
 *       dos instancias del job avisen dos veces la misma alerta;</li>
 *   <li>los emails de los dueños se resuelven en UNA query
 *       ({@code id = ANY(:ids)}), no una por alerta;</li>
 *   <li>email AL DUEÑO por cada alerta avisada (sin cambio de comportamiento)
 *       y UNA sola fila de Telegram al grupo con el resumen del lote, para no
 *       inundar el chat (antes una fila por alerta).</li>
 * </ul>
 *
 * <p>La fila de Telegram se ancla en la PRIMERA alerta del lote
 * ({@code alerta_id}): el esquema de V13 exige un origen por fila y el
 * cascado de borrado de esa alerta purga el resumen — los emails de las
 * demás alertas del resumen son filas propias y no se ven afectados.</p>
 */
@Slf4j
@Service
public class AlertaLoteService {

    /** Coincidencias por aviso; el resto se drena en la siguiente pasada. */
    static final int LIMITE_NUEVAS = 10;

    private final AlertaBusquedaRepository alertas;
    private final InmuebleRepository inmuebles;
    private final AlertaNotificacionRepository notificaciones;
    private final NotificacionesProperties propiedades;
    private final String frontendUrl;

    public AlertaLoteService(AlertaBusquedaRepository alertas,
                             InmuebleRepository inmuebles,
                             AlertaNotificacionRepository notificaciones,
                             NotificacionesProperties propiedades,
                             @Value("${manti.alertas.frontend-url}") String frontendUrl) {
        this.alertas = alertas;
        this.inmuebles = inmuebles;
        this.notificaciones = notificaciones;
        this.propiedades = propiedades;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Resultado del barrido de un lote: la última alerta vista (clave del
     * paginado keyset del job; 0 = lote vacío, fin del barrido) y cuántas
     * llevaron aviso.
     */
    record Resultado(long ultimaAlertaId, int avisadas) {

        static final Resultado VACIO = new Resultado(0L, 0);

        boolean hayMas() {
            return ultimaAlertaId > 0;
        }
    }

    /**
     * Evalúa las alertas ACTIVAS con id superior a {@code desdeId} (hasta
     * {@code limite}), encolando sus avisos y avanzando sus marcas de agua.
     *
     * <p>NO es {@code readOnly}: el lote toma bloqueos de fila
     * ({@code FOR UPDATE SKIP LOCKED}) y escribe avisos y marcas; la
     * exclusividad entre instancias vive SÓLO durante esta transacción corta —
     * las llamadas externas quedan fuera (el envío es del despachador del
     * outbox).</p>
     */
    @Transactional
    public Resultado evaluarLote(long desdeId, int limite) {
        List<AlertaBusqueda> lote = alertas.activasDesde(desdeId, limite);
        if (lote.isEmpty()) {
            return Resultado.VACIO;
        }
        long ultimaId = lote.get(lote.size() - 1).getId();

        Map<AlertaBusqueda, List<InmuebleResumen>> conNuevas = coincidencias(lote);
        if (conNuevas.isEmpty()) {
            return new Resultado(ultimaId, 0);
        }

        // UNA sola query de emails para todo el lote (antes: una por alerta).
        Map<Long, String> emails = notificaciones.emailsDeUsuarios(
                conNuevas.keySet().stream().map(AlertaBusqueda::getUsuarioId).toList());

        List<AlertaAvisoRenderer.Resumen> avisos = new ArrayList<>();
        for (Map.Entry<AlertaBusqueda, List<InmuebleResumen>> coincidencia : conNuevas.entrySet()) {
            AlertaBusqueda alerta = coincidencia.getKey();
            List<InmuebleResumen> nuevas = coincidencia.getValue();
            if (avisarSiHayDestinatario(alerta, nuevas, emails)) {
                avisos.add(new AlertaAvisoRenderer.Resumen(alerta.getNombre(), nuevas.size(),
                        AlertaAvisoRenderer.enlaceDe(alerta, frontendUrl)));
            }
        }
        encolarTelegramResumen(lote.get(0), avisos);
        return new Resultado(ultimaId, avisos.size());
    }

    /**
     * Inmuebles nuevos desde la marca de agua de CADA alerta del lote; sólo se
     * mira {@code id > marca}, así un inmueble ya avisado nunca repite (la
     * marca es el guard real; el bloqueo sólo evita el doble trabajo).
     */
    private Map<AlertaBusqueda, List<InmuebleResumen>> coincidencias(List<AlertaBusqueda> lote) {
        Map<AlertaBusqueda, List<InmuebleResumen>> conNuevas = new LinkedHashMap<>();
        for (AlertaBusqueda alerta : lote) {
            List<InmuebleResumen> nuevas = inmuebles.listarNuevosDesde(
                    filtrosDe(alerta), alerta.getUltimoInmuebleAvisadoId(), LIMITE_NUEVAS);
            if (!nuevas.isEmpty()) {
                conNuevas.put(alerta, nuevas);
            }
        }
        return conNuevas;
    }

    /**
     * Encola el email al dueño de UNA alerta y, sólo entonces, avanza su marca
     * de agua al último inmueble del aviso.
     *
     * @return true si se encoló el aviso
     */
    private boolean avisarSiHayDestinatario(AlertaBusqueda alerta, List<InmuebleResumen> nuevas,
                                            Map<Long, String> emails) {
        String email = emails.get(alerta.getUsuarioId());
        if (email == null) {
            // Usuario inactivo o borrado: no se gasta envío y la marca NO
            // avanza (si vuelve a activarse, no pierde lo ocurrido mientras tanto).
            log.warn("Alerta {} sin destinatario (usuario inactivo o ausente): se omite el aviso",
                    alerta.getId());
            return false;
        }
        notificaciones.encolar(new FilaAvisoAlerta(alerta.getId(), CanalAvisoAlerta.EMAIL,
                email, AlertaAvisoRenderer.asuntoDe(alerta, nuevas.size()),
                AlertaAvisoRenderer.cuerpoEmail(alerta, nuevas,
                        AlertaAvisoRenderer.enlaceDe(alerta, frontendUrl))));
        // Orden id ASC: la última fila es la de mayor id (la marca de agua).
        alerta.registrarAviso(nuevas.get(nuevas.size() - 1).id(), Instant.now());
        alertas.guardar(alerta);
        return true;
    }

    /**
     * UNA fila de Telegram por lote (en la práctica, por pasada del job: los
     * lotes son de cientos de alertas) con el resumen agrupado de las avisadas
     * — antes una fila por alerta, que inundaba el grupo de negocio.
     */
    private void encolarTelegramResumen(AlertaBusqueda ancla,
                                        List<AlertaAvisoRenderer.Resumen> avisos) {
        if (avisos.isEmpty()) {
            return;
        }
        if (propiedades.telegram().tieneChat()) {
            notificaciones.encolar(new FilaAvisoAlerta(ancla.getId(), CanalAvisoAlerta.TELEGRAM,
                    propiedades.telegram().chatId(),
                    AlertaAvisoRenderer.asuntoResumenTelegram(avisos.size()),
                    AlertaAvisoRenderer.resumenTelegram(avisos)));
        } else {
            log.debug("TELEGRAM_CHAT_ID vacío: el lote de alertas {} no encola avisos de Telegram",
                    ancla.getId());
        }
    }

    /** Filtros almacenados como los del listado (patrón de municipio incluido). */
    static InmuebleFiltros filtrosDe(AlertaBusqueda alerta) {
        return new InmuebleFiltros(
                alerta.getEstado(), alerta.getTipo(),
                BusquedaInmuebleService.patronMunicipio(alerta.getMunicipio()),
                alerta.getPrecioMin(), alerta.getPrecioMax(),
                alerta.getHabitacionesMin(), alerta.getDestacado());
    }
}
