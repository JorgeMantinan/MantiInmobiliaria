package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private CuentaGoogleService cuentaGoogle;
    private GoogleOAuth2SuccessHandler handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        cuentaGoogle = mock(CuentaGoogleService.class);
        AuthProperties properties = new AuthProperties(
                null,
                null,
                new AuthProperties.Oauth2(new AuthProperties.Google(
                        "http://localhost:3000/", Duration.ofMinutes(2))),
                null);
        handler = new GoogleOAuth2SuccessHandler(cuentaGoogle, properties);
        response = new MockHttpServletResponse();
    }

    @Test
    void alAutenticarConGoogle_redirigeAlFrontendConCodigoDeUnSoloUso() throws Exception {
        OidcUser oidc = oidcUser("google-id-123", "ana@example.com", "Ana Pérez");
        when(cuentaGoogle.crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez"))
                .thenReturn("CODIGO-DE-UN-SOLO-USO");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?code=CODIGO-DE-UN-SOLO-USO");
        verify(cuentaGoogle).crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez");
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
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString()))
                .thenThrow(new CuentaGoogleConflictivaException());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=account_conflict");
    }

    @Test
    void alAutenticarConGoogle_invalidaLaSesionDelNavegador() throws Exception {
        OidcUser oidc = oidcUser("google-id-000", "sesion@example.com", "Sin Sesión");
        when(cuentaGoogle.crearCodigoGoogle("google-id-000", "sesion@example.com", "Sin Sesión"))
                .thenReturn("CODIGO-UN-USO");
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        MockHttpSession sesion = new MockHttpSession();
        peticion.setSession(sesion);

        handler.onAuthenticationSuccess(peticion, response, autenticacion(oidc));

        // El flujo JWT no deja JSESSIONID detrás del callback de Google
        assertThat(sesion.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
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
