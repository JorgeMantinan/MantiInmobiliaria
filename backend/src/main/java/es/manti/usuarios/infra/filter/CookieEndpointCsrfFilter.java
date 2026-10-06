package es.manti.usuarios.infra.filter;

import es.manti.shared.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Protección CSRF elegida para los endpoints autenticados por cookie
 * ({@code /refresh} y {@code /logout}): exigen la cabecera
 * {@code X-Requested-With: XMLHttpRequest}.
 *
 * <p>Un formulario cross-site no puede añadir cabeceras personalizadas sin
 * disparar una petición preflight CORS, que nuestra allowlist rechaza; por eso
 * con esta cabecera basta y el filtro {@code CsrfFilter} de Spring Security
 * puede permanecer deshabilitado (evita el doble-submit token que el frontend
 * no gestionaría). Documentado también en el PR y en la spec OpenAPI.</p>
 */
public class CookieEndpointCsrfFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Requested-With";
    public static final String VALOR_ESPERADO = "XMLHttpRequest";

    private static final Set<String> ENDPOINTS_CON_COOKIE = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requiereCabecera(request)) {
            ProblemResponseWriter.write(response, HttpStatus.FORBIDDEN, "csrf",
                    "CSRF denegado",
                    "Falta la cabecera " + CABECERA + ": " + VALOR_ESPERADO,
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiereCabecera(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && ENDPOINTS_CON_COOKIE.contains(request.getRequestURI())
                && !VALOR_ESPERADO.equalsIgnoreCase(request.getHeader(CABECERA));
    }
}
