package es.manti.shared.notificaciones;

/**
 * Escape de HTML para los cuerpos de los avisos por email (RFC 9457 aside:
 * el HTML del email se construye con datos del usuario — nombre de la alerta,
 * títulos de anuncio, descripciones de solicitud—, así que SIEMPRE se escapa
 * antes de interpolar.
 *
 * <p>Nació duplicado en {@code profesionales.NotificacionOutboxService} e
 * {@code inmuebles.AlertaEvaluacionService} (issues #10/#96); el follow-up
 * #98 lo unifica aquí, el único sentido de dependencia permitido
 * ({@code shared} es transversal y no depende de ningún módulo).</p>
 */
public final class HtmlEscaping {

    private HtmlEscaping() {
    }

    /** Escapa los 5 caracteres significativos del HTML; {@code null} → "". */
    public static String escapar(String valor) {
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
