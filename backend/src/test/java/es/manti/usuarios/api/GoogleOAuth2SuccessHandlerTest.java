package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.AuthService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del success handler del flujo Google con mocks: redirige al frontend
 * con el código de un solo uso y con errores tipados cuando algo falla.
 */
class GoogleOAuth2SuccessHandlerTest {

    private AuthService authService;
    private GoogleOAuth2SuccessHandler handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthProperties properties = new AuthProperties(
                null,
                null,
                new AuthProperties.Oauth2(new AuthProperties.Google(
                        "http://localhost:3000/", Duration.ofMinutes(2))),
                null);
        handler = new GoogleOAuth2SuccessHandler(authService, properties);
        response = new MockHttpServletResponse();
    }

    @Test
    void alAutenticarConGoogle_redirigeAlFrontendConCodigoDeUnSoloUso() throws Exception {
        OidcUser oidc = oidcUser("google-id-123", "ana@example.com", "Ana Pérez");
        when(authService.crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez"))
                .thenReturn("CODIGO-DE-UN-SOLO-USO");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?code=CODIGO-DE-UN-SOLO-USO");
        verify(authService).crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez");
    }

    @Test
    void alAutenticarConGoogleSinEmail_redirigeConErrorMissingEmail() throws Exception {
        OidcUser oidc = oidcUser("google-id-456", "   ", "Sin Email");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=missing_email");
    }

    @Test
    void alAutenticarConGoogleConConflictoDeVinculacion_redirigeConError() throws Exception {
        OidcUser oidc = oidcUser("google-id-789", "otra@example.com", "Otra Persona");
        when(authService.crearCodigoGoogle(anyString(), anyString(), anyString()))
                .thenThrow(new CuentaGoogleConflictivaException());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=account_conflict");
    }

    // ------------------------------------------------------------------

    private static OidcUser oidcUser(String subject, String email, String nombre) {
        OidcUser oidc = mock(OidcUser.class);
        when(oidc.getSubject()).thenReturn(subject);
        when(oidc.getEmail()).thenReturn(email);
        when(oidc.getFullName()).thenReturn(nombre);
        return oidc;
    }

    private static AbstractAuthenticationToken autenticacion(OidcUser principal) {
        AbstractAuthenticationToken authentication = mock(AbstractAuthenticationToken.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }
}
