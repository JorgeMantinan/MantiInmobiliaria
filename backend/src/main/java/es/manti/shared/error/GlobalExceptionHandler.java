package es.manti.shared.error;

import es.manti.usuarios.domain.AuthException;
import es.manti.usuarios.domain.CodigoInvalidoException;
import es.manti.usuarios.domain.CredencialesInvalidasException;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import es.manti.usuarios.domain.EmailYaRegistradoException;
import es.manti.usuarios.domain.TokenInvalidoException;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Manejador global de excepciones: todos los errores salen como
 * {@code application/problem+json} (RFC 9457) sin stacktraces ni mensajes
 * internos. Los mensajes de {@link AuthException} son fijos y genéricos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // Validación (400)
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException e, HttpServletRequest request) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "validacion",
                "Validación fallida", "Datos de entrada inválidos", request);
        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errores);
        return pd;
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ProblemDetail validacionParametros(Exception e, HttpServletRequest request) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "validacion",
                "Validación fallida", "Datos de entrada inválidos", request);
        pd.setProperty("errors", List.of(Map.of("message", "valor inválido")));
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoInvalido(HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "cuerpo-invalido",
                "Cuerpo de la petición inválido", "JSON malformado o tipo de campo incorrecto", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parametroIncorrecto(HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "parametro-invalido",
                "Parámetro inválido", "Valor de parámetro incorrecto", request);
    }

    // ------------------------------------------------------------------
    // Autenticación / autorización (401 / 403 / 404 / 409)
    // ------------------------------------------------------------------

    @ExceptionHandler({CredencialesInvalidasException.class, UsuarioInactivoException.class,
            TokenInvalidoException.class, CodigoInvalidoException.class, AuthException.class})
    public ProblemDetail noAutorizado(AuthException e, HttpServletRequest request) {
        return problema(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", e.getMessage(), request);
    }

    @ExceptionHandler({EmailYaRegistradoException.class, CuentaGoogleConflictivaException.class})
    public ProblemDetail conflicto(AuthException e, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "conflicto",
                "Conflicto", e.getMessage(), request);
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail noEncontrado(UsuarioNoEncontradoException e, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denegado(HttpServletRequest request) {
        return problema(HttpStatus.FORBIDDEN, "acceso-denegado",
                "Acceso denegado", "No tiene permisos para este recurso", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail sinAutenticar(AuthenticationException e, HttpServletRequest request) {
        return problema(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", "Debe autenticarse para acceder a este recurso", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNoSoportado(HttpServletRequest request) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, "metodo-no-soportado",
                "Método no soportado", "Método HTTP no permitido en este recurso", request);
    }

    // ------------------------------------------------------------------
    // Genérico (500): el detalle interno sólo va al log
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error-interno",
                "Error interno", "Se ha producido un error inesperado", request);
    }

    private static ProblemDetail problema(HttpStatus status, String typeSlug, String title,
                                          String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(ProblemResponseWriter.PROBLEMS_BASE + typeSlug));
        pd.setInstance(URI.create(request.getRequestURI()));
        return pd;
    }
}
