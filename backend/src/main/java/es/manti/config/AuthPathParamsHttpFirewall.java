package es.manti.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * Firewall que relaja <b>sólo</b> la restricción de {@code ;} (path params)
 * bajo {@code /api/v1/auth/}: los filtros de auth ({@code AuthRequestPath})
 * normalizan la ruta (sin {@code ;params} y decodificada), así que las
 * variantes {@code /login;foo=1} o {@code /%6Cogin} caen en la misma ventana
 * de rate limit y en la misma protección CSRF que la ruta canónica.
 *
 * <p>El resto de rutas conserva {@link StrictHttpFirewall} por defecto, que
 * sigue rechazando {@code ;}, {@code //}, {@code ..}, {@code %2f}, etc.</p>
 */
@Slf4j
public class AuthPathParamsHttpFirewall implements HttpFirewall {

    /** Sólo se relaja el punto y coma; el resto de reglas siguen estrictas. */
    private static final String SUBARBOL_AUTH = "/api/v1/auth/";

    private final StrictHttpFirewall estricta = new StrictHttpFirewall();

    private final StrictHttpFirewall conPathParams = construirConPathParams();

    private static StrictHttpFirewall construirConPathParams() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    @Override
    public FirewalledRequest getFirewalledRequest(HttpServletRequest request) throws RequestRejectedException {
        String uri = request.getRequestURI();
        boolean esRutaAuth = uri != null && uri.startsWith(SUBARBOL_AUTH);
        try {
            return (esRutaAuth ? conPathParams : estricta).getFirewalledRequest(request);
        } catch (RequestRejectedException rechazada) {
            log.warn("Petición rechazada por el firewall: {} ({})", uri, rechazada.getMessage());
            throw rechazada;
        }
    }

    @Override
    public HttpServletResponse getFirewalledResponse(HttpServletResponse response) {
        return response;
    }
}
