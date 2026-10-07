package es.manti.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Manejador GLOBAL de errores: SÓLO tipos genéricos de Spring/validación.
 * Todos salen como {@code application/problem+json} (RFC 9457) sin stacktraces
 * ni mensajes internos.
 *
 * <p><b>Este advice no conoce el dominio de ningún módulo</b> (regla ArchUnit
 * {@code shared no depende de ..domain..}): las excepciones de negocio viven en
 * {@link es.manti.inmuebles.api.InmuebleErrorAdvice} y
 * {@link es.manti.usuarios.api.UsuarioErrorAdvice}, acotados a su
 * {@code basePackages}. Aquí sólo queda lo transversal: validación, parámetros,
 * recursos, seguridad, el catch-all de 500 — y la construcción de los problemas
 * ({@link Problems}) y los entry points de la cadena de seguridad.</p>
 *
 * <p>Orden: los advices de módulo van ANTES que éste
 * ({@code @Order(HIGHEST_PRECEDENCE)} vs {@code LOWEST_PRECEDENCE}), de modo que
 * su catch-all nunca captura una excepción de dominio.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // Validación (400)
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "validacion",
                "Validación fallida", "Datos de entrada inválidos", request, errores);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ProblemDetail validacionParametros(Exception e, HttpServletRequest request) {
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "validacion",
                "Validación fallida", "Datos de entrada inválidos", request,
                List.of(Map.of("message", "valor inválido")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoInvalido(HttpServletRequest request) {
        return Problems.crear(HttpStatus.BAD_REQUEST, "cuerpo-invalido",
                "Cuerpo de la petición inválido", "JSON malformado o tipo de campo incorrecto", request);
    }

    /**
     * Query param con un valor que no se puede convertir ({@code estado=XYZ},
     * {@code limit=abc}...). Se rellena {@code errors[] = [{field, message}]}
     * para que el cliente sepa qué parámetro ha fallado, igual que en la
     * validación de bodies.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parametroIncorrecto(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "parametro-invalido",
                "Parámetro inválido", "Valor de parámetro incorrecto", request,
                List.of(Problems.error(e.getName(), "valor no admitido")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroFaltante(MissingServletRequestParameterException e, HttpServletRequest request) {
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "parametro-faltante",
                "Parámetro obligatorio ausente", "Falta un parámetro obligatorio", request,
                List.of(Problems.error(e.getParameterName(), "parámetro obligatorio ausente")));
    }

    // ------------------------------------------------------------------
    // Recursos y tipos (404 / 405 / 415 / 409)
    // ------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail recursoNoEncontrado(HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", "La ruta solicitada no existe", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tipoNoSoportado(HttpServletRequest request) {
        return Problems.crear(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "tipo-no-soportado",
                "Tipo de contenido no soportado", "Content-Type no soportado para este recurso", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail metodoNoSoportado(HttpServletRequest request) {
        return Problems.crear(HttpStatus.METHOD_NOT_ALLOWED, "metodo-no-soportado",
                "Método no soportado", "Método HTTP no permitido en este recurso", request);
    }

    /**
     * Carrera en el registro único de email: {@code existsByEmail} devuelve
     * falso y el INSERT posterior choca con la constraint UNIQUE
     * ({@code uq_usuarios_email}) cuando dos peticiones llegan a una vez.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail violacionDeIntegridad(HttpServletRequest request) {
        log.warn("Violación de integridad de datos en {} {}", request.getMethod(), request.getRequestURI());
        return Problems.crear(HttpStatus.CONFLICT, "conflicto-integridad",
                "Conflicto", "Los datos entran en conflicto con el estado actual", request);
    }

    // ------------------------------------------------------------------
    // Autenticación / autorización de la CADENA de seguridad (401 / 403)
    // (las de dominio viven en UsuarioErrorAdvice)
    // ------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denegado(HttpServletRequest request) {
        return Problems.crear(HttpStatus.FORBIDDEN, "acceso-denegado",
                "Acceso denegado", "No tiene permisos para este recurso", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail sinAutenticar(AuthenticationException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", "Debe autenticarse para acceder a este recurso", request);
    }

    // ------------------------------------------------------------------
    // Genérico (500): el detalle interno sólo va al log
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return Problems.crear(HttpStatus.INTERNAL_SERVER_ERROR, "error-interno",
                "Error interno", "Se ha producido un error inesperado", request);
    }
}
