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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
 * <p>El enlace del email apunta al listado público con los MISMOS query params
 * ({@code manti.alertas.frontend-url}/inmuebles?...), que el frontend ya sabe
 * leer con {@code leerFiltros}.</p>
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

        String asunto = asuntoDe(alerta, nuevas.size());
        String enlace = enlaceDe(alerta);
        notificaciones.encolar(new FilaAvisoAlerta(alerta.getId(), CanalAvisoAlerta.EMAIL,
                email, asunto, cuerpoEmail(alerta, nuevas, enlace)));
        if (propiedades.telegram().tieneChat()) {
            // Mismo patrón que las solicitudes: UNA fila al grupo configurado
            // (no existe chat_id por usuario; ver decisión en el PR del issue #96).
            notificaciones.encolar(new FilaAvisoAlerta(alerta.getId(), CanalAvisoAlerta.TELEGRAM,
                    propiedades.telegram().chatId(), asunto, cuerpoTelegram(alerta, nuevas, enlace)));
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
    private static InmuebleFiltros filtrosDe(AlertaBusqueda alerta) {
        return new InmuebleFiltros(
                alerta.getEstado(), alerta.getTipo(),
                BusquedaInmuebleService.patronMunicipio(alerta.getMunicipio()),
                alerta.getPrecioMin(), alerta.getPrecioMax(),
                alerta.getHabitacionesMin(), alerta.getDestacado());
    }

    private static String asuntoDe(AlertaBusqueda alerta, int cuantas) {
        return "%d viviendas nuevas en tu alerta '%s'".formatted(cuantas, alerta.getNombre());
    }

    /**
     * Enlace al listado público con los query params de la alerta: la MISMA
     * serialización que lee el frontend ({@code leerFiltros}), con cada valor
     * codificado para sobrevivir espacios y acentos.
     */
    private String enlaceDe(AlertaBusqueda alerta) {
        List<String> parametros = new ArrayList<>();
        if (alerta.getEstado() != null) {
            parametros.add("estado=" + alerta.getEstado().name());
        }
        if (alerta.getTipo() != null) {
            parametros.add("tipo=" + alerta.getTipo().name());
        }
        if (alerta.getMunicipio() != null) {
            parametros.add("municipio=" + codificar(alerta.getMunicipio()));
        }
        if (alerta.getPrecioMin() != null) {
            parametros.add("precioMin=" + alerta.getPrecioMin().toPlainString());
        }
        if (alerta.getPrecioMax() != null) {
            parametros.add("precioMax=" + alerta.getPrecioMax().toPlainString());
        }
        if (alerta.getHabitacionesMin() != null) {
            parametros.add("habitacionesMin=" + alerta.getHabitacionesMin());
        }
        if (alerta.getDestacado() != null) {
            parametros.add("destacado=" + alerta.getDestacado());
        }
        String query = parametros.isEmpty() ? "" : "?" + String.join("&", parametros);
        return frontendUrl + "/inmuebles" + query;
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String cuerpoEmail(AlertaBusqueda alerta, List<InmuebleResumen> nuevas,
                                      String enlace) {
        StringBuilder items = new StringBuilder(nuevas.size() * 96);
        for (InmuebleResumen inmueble : nuevas) {
            items.append("<li><strong>")
                    .append(escaparHtml(inmueble.titulo()))
                    .append("</strong> — ")
                    .append(inmueble.precio().toPlainString())
                    .append(" € · ")
                    .append(escaparHtml(inmueble.municipio()))
                    .append("</li>");
        }
        return """
                <p>La alerta <strong>%s</strong> tiene <strong>%d</strong> vivienda(s) nueva(s) \
                desde el último aviso:</p>
                <ul>%s</ul>
                <p><a href="%s">Ver la búsqueda completa</a></p>
                """.formatted(escaparHtml(alerta.getNombre()), nuevas.size(), items,
                escaparHtml(enlace));
    }

    private static String cuerpoTelegram(AlertaBusqueda alerta, List<InmuebleResumen> nuevas,
                                         String enlace) {
        return "Alerta '%s': %d viviendas nuevas. Ver: %s"
                .formatted(alerta.getNombre(), nuevas.size(), enlace);
    }

    /**
     * El HTML del email se construye con datos del usuario (nombre de la
     * alerta, títulos de anuncio): SIEMPRE escapado. Mismo criterio que el
     * cuerpo de las solicitudes en el módulo profesionales.
     */
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
