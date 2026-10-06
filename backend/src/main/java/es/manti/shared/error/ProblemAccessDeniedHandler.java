package es.manti.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Devuelve 403 como ProblemDetail RFC 9457 (autenticado sin permiso). */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemResponseWriter.write(response, HttpStatus.FORBIDDEN, "acceso-denegado",
                "Acceso denegado", "No tiene permisos para este recurso", request.getRequestURI());
    }
}
