package es.manti.usuarios.infra.filter;

import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Rate limit unitario (sin contexto Spring): cubre el caso borde del límite 0
 * — la ventana queda vacía y {@code peekFirst()} no tiene marca de la que
 * restar; sin el guard sería un NPE en vez de la 429 habitual. Y el límite de
 * las escrituras de alertas del follow-up #98 (POST/PATCH/DELETE, 30/min).
 */
class AuthRateLimitFilterTest {

    @Test
    void limiteCero_devuelve429ConRetryAfterSinNpe() throws Exception {
        AuthProperties properties = new AuthProperties(null, null, null,
                new AuthProperties.RateLimit(0, 0, 0, 0, 0, 0, 0));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
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

        // El DELETE de otra ruta tiene SU propia ventana (clave ruta+IP).
        assertThat(filtrar(filter, chain, "DELETE", "/api/v1/alertas/8").getStatus()).isEqualTo(200);
        assertThat(filtrar(filter, chain, "DELETE", "/api/v1/alertas/8").getStatus()).isEqualTo(429);
    }

    @Test
    void alertas_getNoSeLimita() throws Exception {
        AuthRateLimitFilter filter = filtro(1);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            assertThat(filtrar(filter, chain, "GET", "/api/v1/alertas").getStatus()).isEqualTo(200);
        }
    }

    private static AuthRateLimitFilter filtro(int alertasPerMinute) {
        return new AuthRateLimitFilter(new AuthProperties(null, null, null,
                new AuthProperties.RateLimit(100, 100, 100, 100, 100, 100, alertasPerMinute)));
    }

    private static MockHttpServletResponse filtrar(AuthRateLimitFilter filter, FilterChain chain,
                                                   String metodo, String ruta) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
