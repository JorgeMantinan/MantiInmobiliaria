package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fallo del login con Google: el navegador vuelve al callback del FRONTEND con
 * {@code error=access_denied}, sin colgarse de las barras sobrantes de la URL
 * configurada ni de detalles internos.
 */
class GoogleOAuth2FailureHandlerTest {

    @Test
    void onAuthenticationFailure_conFrontendConBarrasSobrantes_redirigeSinBarraFinal() throws Exception {
        GoogleOAuth2FailureHandler handler =
                new GoogleOAuth2FailureHandler(propiedades("http://localhost:3000///"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("fallo en el flujo de Google"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=access_denied");
    }

    @Test
    void onAuthenticationFailure_conFrontendSinBarrasSobrantes_redirigeAlCallback() throws Exception {
        GoogleOAuth2FailureHandler handler =
                new GoogleOAuth2FailureHandler(propiedades("https://portal.manti.dev"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("state no coincide"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://portal.manti.dev/auth/google/callback?error=access_denied");
    }

    private static AuthProperties propiedades(String frontendUrl) {
        return new AuthProperties(null, null,
                new AuthProperties.Oauth2(new AuthProperties.Google(
                        frontendUrl, Duration.ofMinutes(5))),
                null);
    }
}