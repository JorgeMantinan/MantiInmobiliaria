package es.manti.shared.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Escribe ProblemDetail (RFC 9457) fuera del flujo MVC: los entry points y
 * filtros de seguridad no pasan por el {@code @RestControllerAdvice}.
 * El JSON se construye a mano (sólo cinco campos fijos) para no depender de
 * ninguna versión concreta de Jackson.
 */
public final class ProblemResponseWriter {

    public static final String PROBLEMS_BASE = "https://api.manti.dev/problems/";

    private ProblemResponseWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status,
                             String typeSlug, String title, String detail, String instance) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = "{\"type\":\"" + escapar(PROBLEMS_BASE + typeSlug) + '"'
                + ",\"title\":\"" + escapar(title) + '"'
                + ",\"status\":" + status.value()
                + ",\"detail\":\"" + escapar(detail) + '"'
                + ",\"instance\":\"" + escapar(instance) + '"'
                + '}';
        response.getWriter().write(json);
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(valor.length() + 8);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
