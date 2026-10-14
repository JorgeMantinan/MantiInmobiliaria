package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import es.manti.usuarios.domain.CuentaGoogleSinVerificarException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        when(cuentaGoogle.crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez", true))
                .thenReturn("CODIGO-DE-UN-SOLO-USO");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?code=CODIGO-DE-UN-SOLO-USO");
        verify(cuentaGoogle).crearCodigoGoogle("google-id-123", "ana@example.com", "Ana Pérez", true);
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
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new CuentaGoogleConflictivaException());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=account_conflict");
    }

    /** Anti-ATO: cuenta local sin verificar con el mismo email → account_conflict. */
    @Test
    void alAutenticarConGoogleConCuentaLocalSinVerificar_redirigeConAccountConflict() throws Exception {
        OidcUser oidc = oidcUser("google-id-999", "sin-verificar@example.com", "Sin Verificar");
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new CuentaGoogleSinVerificarException());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=account_conflict");
    }

    /** El claim email_verified=false llega al servicio (cuenta creada sin verificar). */
    @Test
    void alAutenticarConGoogle_conEmailVerifiedFalse_pasaElClaimAlServicio() throws Exception {
        OidcUser oidc = oidcUser("google-id-111", "claim-false@example.com", "Claim False");
        when(oidc.getClaims()).thenReturn(Map.of("email_verified", false));
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString(), eq(false)))
                .thenReturn("CODIGO-CLAIM-FALSE");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        verify(cuentaGoogle).crearCodigoGoogle("google-id-111", "claim-false@example.com",
                "Claim False", false);
    }

    /** Fail-closed: claim AUSENTE → la cuenta creada desde Google queda sin verificar. */
    @Test
    void alAutenticarConGoogle_sinClaimEmailVerified_creaLaCuentaSinVerificar() throws Exception {
        OidcUser oidc = oidcUser("google-id-222", "sin-claim@example.com", "Sin Claim");
        when(oidc.getClaims()).thenReturn(Map.of());
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString(), eq(false)))
                .thenReturn("CODIGO-SIN-CLAIM");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion(oidc));

        verify(cuentaGoogle).crearCodigoGoogle("google-id-222", "sin-claim@example.com",
                "Sin Claim", false);
    }

    @Test
    void alAutenticarConGoogle_invalidaLaSesionDelNavegador() throws Exception {
        OidcUser oidc = oidcUser("google-id-000", "sesion@example.com", "Sin Sesión");
        when(cuentaGoogle.crearCodigoGoogle("google-id-000", "sesion@example.com", "Sin Sesión", true))
                .thenReturn("CODIGO-UN-USO");
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        MockHttpSession sesion = new MockHttpSession();
        peticion.setSession(sesion);

        handler.onAuthenticationSuccess(peticion, response, autenticacion(oidc));

        // El flujo JWT no deja JSESSIONID detrás del callback de Google
        assertThat(sesion.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** Los caminos de ERROR también invalidan la sesión del navegador. */
    @Test
    void alAutenticarConGoogleConError_invalidaLaSesionDelNavegador() throws Exception {
        OidcUser oidc = oidcUser("google-id-err", "error@example.com", "Con Error");
        when(cuentaGoogle.crearCodigoGoogle(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new CuentaGoogleSinVerificarException());
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        MockHttpSession sesion = new MockHttpSession();
        peticion.setSession(sesion);

        handler.onAuthenticationSuccess(peticion, response, autenticacion(oidc));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/google/callback?error=account_conflict");
        assertThat(sesion.isInvalid())
                .as("el error no debe dejar JSESSIONID detrás del callback")
                .isTrue();
    }

    // ------------------------------------------------------------------

    private static OidcUser oidcUser(String subject, String email, String nombre) {
        OidcUser oidc = mock(OidcUser.class);
        when(oidc.getSubject()).thenReturn(subject);
        when(oidc.getEmail()).thenReturn(email);
        when(oidc.getFullName()).thenReturn(nombre);
        // Google manda email_verified=true para gmail verificado
        when(oidc.getClaims()).thenReturn(Map.of("email_verified", true));
        return oidc;
    }

    private static AbstractAuthenticationToken autenticacion(OidcUser principal) {
        AbstractAuthenticationToken authentication = mock(AbstractAuthenticationToken.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }
}
