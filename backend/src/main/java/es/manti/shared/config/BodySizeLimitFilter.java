package es.manti.shared.config;

import es.manti.shared.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Límite de tamaño del cuerpo de la petición (A04 — diseño inseguro): si la
 * cabecera {@code Content-Length} declarada supera los 256 KiB se responde
 * <b>413</b> con ProblemDetail RFC 9457 ({@code payload-too-large}) y la
 * petición NO llega al controlador.
 *
 * <p>Sin {@code Content-Length} (transfer-encoding chunked) la petición pasa:
 * el filtro sólo mira la cabecera declarada y no puede medir el flujo. Ese
 * hueco lo cierra Tomcat con {@code server.tomcat.max-swallow-size: 256KB}
 * (application.yml), que limita lo que el contenedor se traga al desconectar
 * un cuerpo rechazado.</p>
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    /** 256 KiB: cualquier payload real de la API queda muy por debajo. */
    public static final long MAXIMO_CUERPO_BYTES = 262_144L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null && excede(contentLength)) {
            ProblemResponseWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE,
                    "payload-too-large", "Cuerpo demasiado grande",
                    "El cuerpo de la petición supera el máximo permitido (256 KiB)",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean excede(String contentLength) {
        try {
            return Long.parseLong(contentLength.trim()) > MAXIMO_CUERPO_BYTES;
        } catch (NumberFormatException e) {
            // Cabecera no numérica: se ignora aquí (Tomcat la rechaza antes).
            return false;
        }
    }
}
