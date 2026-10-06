package es.manti.usuarios.api;

import es.manti.usuarios.application.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Redirige al frontend indicando fallo del login con Google (sin detalles internos). */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    private final AuthProperties properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Fallo en el flujo OAuth2 de Google: {}", exception.getMessage());
        String base = properties.oauth2().google().frontendUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        response.sendRedirect(base + "/auth/google/callback?error=access_denied");
    }
}
