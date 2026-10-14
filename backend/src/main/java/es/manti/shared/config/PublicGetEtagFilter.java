package es.manti.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.util.Set;

/**
 * ETag débil ({@code ShallowEtagHeaderFilter}, issue #32) para los GET
 * PÚBLICOS y acotados del portal: calcula el hash del body y responde
 * {@code 304 Not Modified} cuando el cliente revalida con
 * {@code If-None-Match}, ahorrando el ancho de banda de la respuesta completa.
 *
 * <p><b>Alcance mínimo deliberado</b> (decisión del PR): SÓLO las rutas
 * anónimas de sólo lectura cuyo tamaño está limitado por contrato —
 * {@code /api/v1/inmuebles} (listado con {@code limit} 1..50 y detalle),
 * {@code /api/v1/mercado} (un municipio) y {@code /api/v1/gremios} (catálogo
 * estático). Fuera quedan, a propósito:
 * </p>
 *
 * <ul>
 *   <li>las rutas de {@code /auth/**} y el canje OAuth2: respuestas diminutas
 *       y sensibles; la caché de un token no aporta nada;</li>
 *   <li>los endpoints con cookie de refresh / Bearer: sus bodies varían por
 *       usuario y un ETag compartible sólo añade riesgo;</li>
 *   <li>descargas o streaming: el filtro BUFFERIZA el body completo en memoria
 *       para hashear, así que jamás debe envolver un volcado grande.</li>
 * </ul>
 *
 * <p>El coste del hash es O(tamaño de la página) y está acotado por las
 * propias reglas de paginación (50 ítems máximo en el listado, ~30 KiB). El
 * filtro sólo se activa en GET/HEAD: cualquier otro método pasa de largo con
 * una comprobación de ruta en O(1).</p>
 */
@Component
public class PublicGetEtagFilter extends ShallowEtagHeaderFilter {

    /** Rutas públicas exactas con ETag. */
    private static final Set<String> RUTAS_EXACTAS = Set.of(
            "/api/v1/inmuebles",
            "/api/v1/mercado",
            "/api/v1/gremios");

    /** Prefijo del detalle de inmueble ({@code /api/v1/inmuebles/{id}}). */
    private static final String PREFIJO_DETALLE = "/api/v1/inmuebles/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String metodo = request.getMethod();
        if (!HttpMethod.GET.matches(metodo) && !HttpMethod.HEAD.matches(metodo)) {
            return true;
        }
        String ruta = rutaDe(request);
        return !RUTAS_EXACTAS.contains(ruta) && !ruta.startsWith(PREFIJO_DETALLE);
    }

    /**
     * URI sin el context path. Se usa la cruda (sin decodificar): no es una
     * decisión de seguridad, sólo de elegibilidad del ETag, y una variante
     * percent-encoded como máximo no llevará ETag.
     */
    private static String rutaDe(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return contexto == null || contexto.isEmpty() ? uri : uri.substring(contexto.length());
    }
}
