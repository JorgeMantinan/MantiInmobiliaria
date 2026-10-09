package es.manti.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Devuelve 401 como ProblemDetail RFC 9457 cuando no hay (o no vale) el
 * access token. Los filtros de seguridad no pasan por el {@code @RestControllerAdvice}.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"api\"");
        ProblemResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", "Debe autenticarse para acceder a este recurso", request.getRequestURI());
    }
}
