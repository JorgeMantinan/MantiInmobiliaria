package es.manti.usuarios.infra.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Normalización de la ruta de petición, compartida por {@link AuthRateLimitFilter}
 * y {@link CookieEndpointCsrfFilter}. Se calcula UNA sola vez por petición
 * (cacheada en un atributo) para que la comparación con los matchers de
 * autorización/routing sea siempre la misma.
 *
 * <p>Los matchers de Spring normalizan la ruta (sin {@code ;params} y
 * decodificada); usar {@code request.getRequestURI()} —cruda, con
 * {@code ;params} y sin decodificar— permitiría evadir los filtros con
 * {@code POST /api/v1/auth/login;foo=1} o {@code /api/v1/auth/%6Cogin}.</p>
 */
final class AuthRequestPath {

    static final String ATRIBUTO = AuthRequestPath.class.getName() + ".NORMALIZED_PATH";

    private AuthRequestPath() {
    }

    /** Ruta dentro de la aplicación, sin {@code ;params} y decodificada (UTF-8). */
    static String normalizar(HttpServletRequest request) {
        Object cache = request.getAttribute(ATRIBUTO);
        if (cache instanceof String rutaNormalizada) {
            return rutaNormalizada;
        }
        String ruta = calcular(request);
        request.setAttribute(ATRIBUTO, ruta);
        return ruta;
    }

    private static String calcular(HttpServletRequest request) {
        String ruta = ServletRequestPathUtils.parse(request).pathWithinApplication().value();
        int inicioParams = ruta.indexOf(';');
        if (inicioParams >= 0) {
            ruta = ruta.substring(0, inicioParams);
        }
        try {
            // Idempotente sobre rutas ya decodificadas; sólo afecta a %xx pendientes.
            ruta = UriUtils.decode(ruta, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException rutaMalformada) {
            // Ruta con escapes inválidos: el handler tampoco la resolverá; se
            // compara en crudo para no romper el filtro.
        }
        return ruta;
    }
}
