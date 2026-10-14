package es.manti.usuarios.infra.filter;

import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Rate limit unitario (sin contexto Spring) del {@link AuthRateLimitFilter}.
 *
 * <p>Cubre el caso borde del límite 0 — la ventana queda vacía y
 * {@code peekFirst()} no tiene marca de la que restar; sin el guard sería un
 * NPE en vez de la 429 habitual — el límite de las escrituras de alertas del
 * follow-up #98 (POST/PATCH/DELETE, 30/min, ids colapsados a {@code {id}}) y
 * las rutas añadidas en la FASE P2: refresh y canje OAuth2 (issue #17),
 * listado público de inmuebles (issue #22) y swipes/deck/favoritos
 * (comentario del issue #17).</p>
 */
class AuthRateLimitFilterTest {

    private static final String IP = "10.0.0.1";

    @Test
    void limiteCero_devuelve429ConRetryAfterSinNpe() throws Exception {
        AuthProperties properties = new AuthProperties(null, null, null,
                new AuthProperties.RateLimit(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        verifyNoInteractions(chain);
    }

    @Test
    void alertas_postMasAllaDelLimite_devuelve429() throws Exception {
        AuthRateLimitFilter filter = filtro(2);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse ok = filtrar(filter, chain, "POST", "/api/v1/alertas");
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse rechazada = filtrar(filter, chain, "POST", "/api/v1/alertas");

        assertThat(rechazada.getStatus()).isEqualTo(429);
        assertThat(rechazada.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void alertas_patchYDeleteTambienSeLimitan() throws Exception {
        AuthRateLimitFilter filter = filtro(1);
        FilterChain chain = mock(FilterChain.class);

        assertThat(filtrar(filter, chain, "PATCH", "/api/v1/alertas/7").getStatus()).isEqualTo(200);
        assertThat(filtrar(filter, chain, "PATCH", "/api/v1/alertas/7").getStatus()).isEqualTo(429);
    }

    @Test
    void alertas_idsDistintosCompartenLaMismaVentana() throws Exception {
        AuthRateLimitFilter filter = filtro(2);
        FilterChain chain = mock(FilterChain.class);

        // La clave colapsa el id a {id}: sin eso, N ids = N ventanas y el
        // límite de escrituras se bypasaría con ids distintos.
        assertThat(filtrar(filter, chain, "PATCH", "/api/v1/alertas/7").getStatus()).isEqualTo(200);
        assertThat(filtrar(filter, chain, "DELETE", "/api/v1/alertas/8").getStatus()).isEqualTo(200);
        assertThat(filtrar(filter, chain, "PATCH", "/api/v1/alertas/9").getStatus()).isEqualTo(429);
    }

    @Test
    void alertas_getNoSeLimita() throws Exception {
        AuthRateLimitFilter filter = filtro(1);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            assertThat(filtrar(filter, chain, "GET", "/api/v1/alertas").getStatus()).isEqualTo(200);
        }
    }

    /**
     * Issues #17 y #22: cada ruta nueva tiene SU ventana propia de 60 s. Dos
     * peticiones pasan y la tercera cae en 429 con Retry-After; el orden de
     * entrada de las rutas no comparte contador (la clave es ruta + IP).
     */
    @Test
    void rutasNuevas_devuelven429AlSuperarSuLimite() throws Exception {
        AuthProperties.RateLimit limites =
                new AuthProperties.RateLimit(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(new AuthProperties(null, null, null, limites));

        List<String[]> rutas = List.of(
                new String[]{"POST", "/api/v1/auth/refresh"},
                new String[]{"POST", "/api/v1/auth/oauth2/token"},
                new String[]{"GET", "/api/v1/inmuebles"},
                new String[]{"POST", "/api/v1/swipes"},
                new String[]{"GET", "/api/v1/swipes/deck"},
                new String[]{"GET", "/api/v1/swipes/favoritos"});

        for (String[] ruta : rutas) {
            FilterChain chain = mock(FilterChain.class);

            for (int intento = 1; intento <= 2; intento++) {
                MockHttpServletResponse permitida = filtrar(filter, chain, ruta[0], ruta[1]);
                assertThat(permitida.getStatus())
                        .as("%s %s — petición %d dentro del límite", ruta[0], ruta[1], intento)
                        .isEqualTo(200);
            }

            MockHttpServletResponse rechazo = filtrar(filter, chain, ruta[0], ruta[1]);
            assertThat(rechazo.getStatus())
                    .as("%s %s — tercera petición (límite 2)", ruta[0], ruta[1])
                    .isEqualTo(429);
            assertThat(rechazo.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
            verify(chain, times(2)).doFilter(any(), any());
        }
    }

    /** Las rutas sólo se limitan con su método: GET /auth/refresh no es refresh. */
    @Test
    void rutaNueva_conMetodoDistinto_noSeLimita() throws Exception {
        AuthProperties.RateLimit limites =
                new AuthProperties.RateLimit(0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(new AuthProperties(null, null, null, limites));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse respuesta = filtrar(filter, chain, "GET", "/api/v1/auth/refresh");

        assertThat(respuesta.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    private static AuthRateLimitFilter filtro(int alertasPerMinute) {
        return new AuthRateLimitFilter(new AuthProperties(null, null, null,
                new AuthProperties.RateLimit(100, 100, 100, 100, 100, 100,
                        alertasPerMinute, 100, 100, 100, 100, 100, 100)));
    }

    private static MockHttpServletResponse filtrar(AuthRateLimitFilter filter, FilterChain chain,
                                                   String metodo, String ruta) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr(IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
