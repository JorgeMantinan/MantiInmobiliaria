package es.manti.usuarios.infra.filter;

import es.manti.shared.error.ProblemResponseWriter;
import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting básico en memoria por IP (sin dependencias externas) para
 * {@code POST /api/v1/auth/login} y {@code POST /api/v1/auth/register}:
 * ventana fija de 60 s por (ruta, IP), configurable con
 * {@code auth.rate-limit.*}.
 *
 * <p>Nota despliegue: la IP se toma de {@code remoteAddr}; si el backend va
 * detrás de un reverse proxy hay que activar
 * {@code server.forward-headers-strategy} para que la IP real llegue en
 * {@code X-Forwarded-For}. Límite best-effort pensado para una sola instancia.</p>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000L;
    private static final int LIMPIA_A_PARTIR_DE = 10_000;

    private final AuthProperties properties;
    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Integer limite = limitePara(request);
        if (limite == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String clave = request.getRequestURI() + '|' + ipDe(request);
        if (!permitido(clave, limite)) {
            ProblemResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limit", "Demasiadas peticiones",
                    "Límite de peticiones alcanzado; inténtelo de nuevo en un minuto",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Integer limitePara(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case "/api/v1/auth/login" -> properties.rateLimit().loginPerMinute();
            case "/api/v1/auth/register" -> properties.rateLimit().registerPerMinute();
            default -> null;
        };
    }

    private boolean permitido(String clave, int limite) {
        limpiarSiDesbordado();
        Deque<Long> marcas = ventanas.computeIfAbsent(clave, k -> new ArrayDeque<>());
        long ahora = System.currentTimeMillis();
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst() < ahora - VENTANA_MS) {
                marcas.pollFirst();
            }
            if (marcas.size() >= limite) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private void limpiarSiDesbordado() {
        if (ventanas.size() > LIMPIA_A_PARTIR_DE) {
            long limiteVentana = System.currentTimeMillis() - VENTANA_MS;
            ventanas.entrySet().removeIf(e -> {
                Deque<Long> marcas = e.getValue();
                synchronized (marcas) {
                    return marcas.isEmpty() || marcas.peekLast() < limiteVentana;
                }
            });
        }
    }

    static String ipDe(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
