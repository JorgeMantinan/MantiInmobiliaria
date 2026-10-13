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
import java.util.List;

/**
 * Evaluación de las alertas activas (issue #96): por cada alerta, los inmuebles
 * nuevos DESDE su marca de agua que cumplen sus filtros, y si los hay se encola
 * un aviso en el outbox compartido (Brevo + Telegram) y se avanza la marca.
 *
 * <p><b>Anti-spam</b>: la marca de agua ({@code ultimo_inmueble_avisado_id})
 * sólo avanza cuando se encola un aviso, y sólo se mira {@code id > marca}, así
 * que un inmueble nunca se notifica dos veces y las pausas no generan avisos.
 * El límite de {@link #LIMITE_NUEVAS} por pasada drena los picos en varias
 * tandas en vez de cargar el catálogo entero ni mandar un email gigante.</p>
 *
 * <p><b>Transacción</b>: una sola corta y SIN llamadas externas (el envío es
 * del despachador del outbox, después del commit) — o se encola el aviso y se
 * avanza la marca, o no ocurre ninguno de los dos.</p>
 *
 * <p>El render de los mensajes vive en {@link AlertaAvisoRenderer} (follow-up
 * #98): este servicio sólo orquesta.</p>
 */
@Slf4j
@Service
public class AlertaEvaluacionService {

    /** Coincidencias por aviso; el resto se drena en la siguiente pasada. */
    static final int LIMITE_NUEVAS = 10;

    private final AlertaBusquedaRepository alertas;
    private final InmuebleRepository inmuebles;
    private final AlertaNotificacionRepository notificaciones;
    private final NotificacionesProperties propiedades;
    private final String frontendUrl;

    public AlertaEvaluacionService(AlertaBusquedaRepository alertas,
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
     * Evalúa TODAS las alertas activas y encola un aviso por cada una con
     * inmuebles nuevos desde su marca de agua.
     *
     * @return número de alertas avisadas en esta pasada
     */
    @Transactional
    public int evaluar() {
        int avisadas = 0;
        for (AlertaBusqueda alerta : alertas.activas()) {
            if (avisarSiHayNuevas(alerta)) {
                avisadas++;
            }
        }
        if (avisadas > 0) {
            log.info("Alertas de búsqueda con avisos encolados: {}", avisadas);
        }
        return avisadas;
    }

    /**
     * Encola los avisos de UNA alerta si hay inmuebles nuevos y, sólo entonces,
     * avanza su marca de agua al último de ellos.
     *
     * @return true si se encoló algún aviso
     */
    private boolean avisarSiHayNuevas(AlertaBusqueda alerta) {
        List<InmuebleResumen> nuevas = inmuebles.listarNuevosDesde(
                filtrosDe(alerta), alerta.getUltimoInmuebleAvisadoId(), LIMITE_NUEVAS);
        if (nuevas.isEmpty()) {
            return false;
        }

        String email = notificaciones.emailDeUsuario(alerta.getUsuarioId()).orElse(null);
        if (email == null) {
            // Usuario inactivo o borrado: no se gasta envío y la marca NO
            // avanza (si vuelve a activarse, no pierde lo ocurrido mientras tanto).
            log.warn("Alerta {} sin destinatario (usuario inactivo o ausente): se omite el aviso",
                    alerta.getId());
            return false;
        }

        String asunto = AlertaAvisoRenderer.asuntoDe(alerta, nuevas.size());
        String enlace = AlertaAvisoRenderer.enlaceDe(alerta, frontendUrl);
        notificaciones.encolar(new FilaAvisoAlerta(alerta.getId(), CanalAvisoAlerta.EMAIL,
                email, asunto, AlertaAvisoRenderer.cuerpoEmail(alerta, nuevas, enlace)));
        if (propiedades.telegram().tieneChat()) {
            // Mismo patrón que las solicitudes: UNA fila al grupo configurado
            // (no existe chat_id por usuario; ver decisión en el PR del issue #96).
            notificaciones.encolar(new FilaAvisoAlerta(alerta.getId(), CanalAvisoAlerta.TELEGRAM,
                    propiedades.telegram().chatId(), asunto,
                    AlertaAvisoRenderer.cuerpoTelegram(alerta, nuevas, enlace)));
        } else {
            log.debug("TELEGRAM_CHAT_ID vacío: la alerta {} no encola avisos de Telegram",
                    alerta.getId());
        }

        // Orden id ASC: la última fila es la de mayor id (la marca de agua).
        alerta.registrarAviso(nuevas.get(nuevas.size() - 1).id(), Instant.now());
        alertas.guardar(alerta);
        return true;
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
