package es.manti.usuarios.infra.filter;

import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Rate limit unitario (sin contexto Spring): cubre el caso borde del límite 0
 * — la ventana queda vacía y {@code peekFirst()} no tiene marca de la que
 * restar; sin el guard sería un NPE en vez de la 429 habitual.
 */
class AuthRateLimitFilterTest {

    @Test
    void limiteCero_devuelve429ConRetryAfterSinNpe() throws Exception {
        AuthProperties properties = new AuthProperties(null, null, null,
                new AuthProperties.RateLimit(0, 0, 0, 0, 0, 0));
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
}
