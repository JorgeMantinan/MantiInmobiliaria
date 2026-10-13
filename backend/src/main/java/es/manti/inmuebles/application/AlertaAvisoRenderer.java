package es.manti.inmuebles.application;

import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.InmuebleResumen;
import es.manti.shared.notificaciones.HtmlEscaping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Render de los avisos de una alerta (follow-up #98): asunto, enlace al
 * listado y cuerpos de email/Telegram. Funciones PURAS — sin red, sin BD y
 * sin configuración propia ({@code frontendUrl} se recibe como parámetro—,
 * así se prueban y reutilizan sin contexto Spring.
 *
 * <p>El HTML del email se construye con datos del usuario (nombre de la
 * alerta, títulos de anuncio): SIEMPRE escapado con el
 * {@link HtmlEscaping} compartido (antes duplicado en este módulo y en
 * profesionales).</p>
 *
 * <p>El enlace del email apunta al listado público con los MISMOS query params
 * ({@code manti.alertas.frontend-url}/inmuebles?...), que el frontend ya sabe
 * leer con {@code leerFiltros}.</p>
 */
final class AlertaAvisoRenderer {

    private AlertaAvisoRenderer() {
    }

    /** Fila del resumen agrupado de Telegram de una pasada del job. */
    record Resumen(String nombre, int cuantas, String enlace) {
    }

    static String asuntoDe(AlertaBusqueda alerta, int cuantas) {
        return "%d viviendas nuevas en tu alerta '%s'".formatted(cuantas, alerta.getNombre());
    }

    /** Asunto del ÚNICO mensaje de Telegram de la pasada (resumen agrupado). */
    static String asuntoResumenTelegram(int cuantas) {
        return "%d alertas han encontrado viviendas nuevas".formatted(cuantas);
    }

    /**
     * Enlace al listado público con los query params de la alerta: la MISMA
     * serialización que lee el frontend ({@code leerFiltros}), con cada valor
     * codificado para sobrevivir espacios y acentos.
     */
    static String enlaceDe(AlertaBusqueda alerta, String frontendUrl) {
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

    static String cuerpoEmail(AlertaBusqueda alerta, List<InmuebleResumen> nuevas,
                              String enlace) {
        StringBuilder items = new StringBuilder(nuevas.size() * 96);
        for (InmuebleResumen inmueble : nuevas) {
            items.append("<li><strong>")
                    .append(HtmlEscaping.escapar(inmueble.titulo()))
                    .append("</strong> — ")
                    .append(inmueble.precio().toPlainString())
                    .append(" € · ")
                    .append(HtmlEscaping.escapar(inmueble.municipio()))
                    .append("</li>");
        }
        return """
                <p>La alerta <strong>%s</strong> tiene <strong>%d</strong> vivienda(s) nueva(s) \
                desde el último aviso:</p>
                <ul>%s</ul>
                <p><a href="%s">Ver la búsqueda completa</a></p>
                """.formatted(HtmlEscaping.escapar(alerta.getNombre()), nuevas.size(), items,
                HtmlEscaping.escapar(enlace));
    }

    /**
     * Resumen de texto plano (Telegram) de TODAS las alertas avisadas en la
     * pasada: una línea por alerta con su cuenta y su enlace.
     */
    static String resumenTelegram(List<Resumen> avisos) {
        StringBuilder sb = new StringBuilder(avisos.size() * 96);
        sb.append(asuntoResumenTelegram(avisos.size())).append(':');
        for (Resumen aviso : avisos) {
            sb.append("\n• ").append(aviso.nombre()).append(": ")
                    .append(aviso.cuantas()).append(" viviendas nuevas. Ver: ")
                    .append(aviso.enlace());
        }
        return sb.toString();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
