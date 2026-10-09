package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import es.manti.usuarios.application.CuentaGoogleService;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import es.manti.usuarios.domain.CuentaGoogleSinVerificarException;
import es.manti.usuarios.domain.UsuarioInactivoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Handler propio del callback de Google: crea/vincula la usuario, genera un
 * código opaco de un solo uso y redirige al frontend
 * ({@code ${FRONTEND_URL}/auth/google/callback?code=...}), que lo canjeará con
 * {@code POST /api/v1/auth/oauth2/token}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final CuentaGoogleService cuentaGoogle;
    private final AuthProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OidcUser oidc = usuarioOidc(authentication);
            String codigo = cuentaGoogle.crearCodigoGoogle(
                    oidc.getSubject(),
                    oidc.getEmail(),
                    oidc.getFullName() != null ? oidc.getFullName() : oidc.getPreferredUsername(),
                    emailVerificado(oidc));
            // El flujo JWT no necesita JSESSIONID persistente: la sesión sólo
            // existía para el baile OAuth2, así que se invalida antes de salir.
            invalidarSesionDeNavegador(request);
            response.sendRedirect(consultaFrontend("code=" + URLEncoder.encode(codigo, StandardCharsets.UTF_8)));
        } catch (MissingEmailException e) {
            invalidarSesionDeNavegador(request);
            log.warn("Login con Google incompleto: {}", e.getMessage());
            response.sendRedirect(consultaFrontend("error=missing_email"));
        } catch (NonOidcException e) {
            invalidarSesionDeNavegador(request);
            log.warn("Login con Google con principal no OIDC: {}", e.getMessage());
            response.sendRedirect(consultaFrontend("error=unsupported"));
        } catch (UsuarioInactivoException e) {
            invalidarSesionDeNavegador(request);
            log.warn("Login con Google de cuenta desactivada");
            response.sendRedirect(consultaFrontend("error=account_disabled"));
        } catch (CuentaGoogleConflictivaException e) {
            invalidarSesionDeNavegador(request);
            log.warn("Login con Google rechazado por conflicto de vinculación");
            response.sendRedirect(consultaFrontend("error=account_conflict"));
        } catch (CuentaGoogleSinVerificarException e) {
            // Pre-hijack: existe una cuenta local con ese email sin verificar.
            // Mismo código account_conflict que el frontend ya interpreta.
            invalidarSesionDeNavegador(request);
            log.warn("Login con Google rechazado: la cuenta local con ese email no está verificada");
            response.sendRedirect(consultaFrontend("error=account_conflict"));
        }
    }

    /**
     * Claim {@code email_verified} del token OIDC. <b>Fail-closed</b>: sólo
     * cuenta como verificada la afirmación EXPLÍCITA {@code true}; si el
     * claim está ausente (o llega raro) la cuenta creada desde Google nace
     * SIN verificar. Ese guard no la bloquea: su relogin posterior va por
     * {@code google_id} (camino de búsqueda, no de vínculo).
     */
    private static boolean emailVerificado(OidcUser oidc) {
        return Boolean.TRUE.equals(oidc.getClaims().get("email_verified"));
    }

    private static OidcUser usuarioOidc(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
            throw new NonOidcException();
        }
        if (oidc.getSubject() == null || oidc.getEmail() == null || oidc.getEmail().isBlank()) {
            throw new MissingEmailException();
        }
        return oidc;
    }

    private String consultaFrontend(String query) {
        String base = properties.oauth2().google().frontendUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/auth/google/callback?" + query;
    }

    private static void invalidarSesionDeNavegador(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /** Falta el claim de email en el token de Google. */
    private static final class MissingEmailException extends RuntimeException {
        private MissingEmailException() {
            super("token Google sin email");
        }
    }

    /** El principal no es un usuario OIDC (scope openid ausente). */
    private static final class NonOidcException extends RuntimeException {
        private NonOidcException() {
            super("principal no OIDC");
        }
    }
}
