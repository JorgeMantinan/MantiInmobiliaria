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
 * Rate limiting básico en memoria por IP (sin dependencias externas) para los
 * POST sensibles: login/registro de usuarios (ventana de 60 s) y, desde la
 * FASE 5, solicitudes de servicio (10/hora) y registro de profesionales
 * (5/hora). Cada ruta lleva SU ventana, configurable con
 * {@code auth.rate-limit.*}.
 *
 * <p>Nota despliegue: la IP se toma de {@code remoteAddr}; si el backend va
 * detrás de un reverse proxy hay que activar
 * {@code server.forward-headers-strategy} para que la IP real llegue en
 * {@code X-Forwarded-For}. Límite best-effort pensado para una sola instancia.</p>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Ventana fija de los endpoints de autenticación. */
    private static final long VENTANA_MINUTO_MS = 60_000L;

    /** Ventana de las rutas de la FASE 5 (limites "por hora"). */
    private static final long VENTANA_HORA_MS = 3_600_000L;

    /** Limpieza del mapa cuando crece demasiado (usa la ventana MÁXIMA). */
    private static final int LIMPIA_A_PARTIR_DE = 10_000;

    private final AuthProperties properties;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    /** Límite de una ruta concreta: nº de peticiones y anchura de su ventana. */
    private record Limite(int peticiones, long ventanaMs) {
    }

    /** Marcas de tiempo de una clave (ruta + IP), con la ventana que le aplica. */
    private record Ventana(long ventanaMs, Deque<Long> marcas) {
    }

    public AuthRateLimitFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ruta = AuthRequestPath.normalizar(request);
        Limite limite = limitePara(request.getMethod(), ruta);
        if (limite == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // La clave usa la ruta normalizada: si usara la cruda, cada
        // variante ";x=1", "%6C..." tendría su propia ventana y se evade el límite.
        String clave = ruta + '|' + ipDe(request);
        if (!permitido(clave, limite)) {
            ProblemResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limit", "Demasiadas peticiones",
                    "Límite de peticiones alcanzado; inténtelo de nuevo en " + espera(limite),
                    ruta);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Limite limitePara(String metodo, String rutaNormalizada) {
        if (!"POST".equals(metodo)) {
            return null;
        }
        AuthProperties.RateLimit rateLimit = properties.rateLimit();
        return switch (rutaNormalizada) {
            case "/api/v1/auth/login" -> new Limite(rateLimit.loginPerMinute(), VENTANA_MINUTO_MS);
            case "/api/v1/auth/register" ->
                    new Limite(rateLimit.registerPerMinute(), VENTANA_MINUTO_MS);
            // FASE 5 — rutas PÚBLICAS: sin límite el outbox y el alta de
            // profesionales serían un denial-of-service económico.
            case "/api/v1/solicitudes" ->
                    new Limite(rateLimit.solicitudesPerHour(), VENTANA_HORA_MS);
            case "/api/v1/profesionales/registro" ->
                    new Limite(rateLimit.registroProfesionalPerHour(), VENTANA_HORA_MS);
            default -> null;
        };
    }

    private static String espera(Limite limite) {
        return limite.ventanaMs() == VENTANA_HORA_MS ? "una hora" : "un minuto";
    }

    private boolean permitido(String clave, Limite limite) {
        limpiarSiDesbordado();
        Ventana ventana = ventanas.computeIfAbsent(clave,
                k -> new Ventana(limite.ventanaMs(), new ArrayDeque<>()));
        Deque<Long> marcas = ventana.marcas();
        long ahora = System.currentTimeMillis();
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst() < ahora - ventana.ventanaMs()) {
                marcas.pollFirst();
            }
            if (marcas.size() >= limite.peticiones()) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private void limpiarSiDesbordado() {
        if (ventanas.size() > LIMPIA_A_PARTIR_DE) {
            long limiteVentana = System.currentTimeMillis() - VENTANA_HORA_MS;
            ventanas.entrySet().removeIf(e -> {
                Deque<Long> marcas = e.getValue().marcas();
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
