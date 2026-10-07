package es.manti.usuarios.api;

import es.manti.shared.error.Problems;
import es.manti.usuarios.domain.AuthException;
import es.manti.usuarios.domain.CodigoInvalidoException;
import es.manti.usuarios.domain.CredencialesInvalidasException;
import es.manti.usuarios.domain.CuentaGoogleConflictivaException;
import es.manti.usuarios.domain.EmailYaRegistradoException;
import es.manti.usuarios.domain.TokenInvalidoException;
import es.manti.usuarios.domain.UsuarioInactivoException;
import es.manti.usuarios.domain.UsuarioNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeo a ProblemDetail RFC 9457 de las excepciones de dominio de USUARIOS.
 *
 * <p>Acotado a {@code basePackages = "es.manti.usuarios.api"}: las excepciones
 * de autenticación dejan de vivir en {@code shared}, que así no importa
 * {@code es.manti.usuarios.domain.*} (regla ArchUnit {@code shared no depende
 * de ..domain..}). Va antes que el advice genérico de {@code shared}
 * ({@link Order}), que tiene un catch-all de último recurso.</p>
 *
 * <p>Mensajes fijos y genéricos: nunca distinguen "email no existe" de
 * "password incorrecto" ni revelan datos internos.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "es.manti.usuarios.api")
public class UsuarioErrorAdvice {

    /** 401: credenciales, cuenta inactiva, token/código de refresh inválidos. */
    @ExceptionHandler({CredencialesInvalidasException.class, UsuarioInactivoException.class,
            TokenInvalidoException.class, CodigoInvalidoException.class, AuthException.class})
    public ProblemDetail noAutorizado(AuthException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", e.getMessage(), request);
    }

    /** 409: email ya registrado o cuenta de Google vinculada a otro usuario. */
    @ExceptionHandler({EmailYaRegistradoException.class, CuentaGoogleConflictivaException.class})
    public ProblemDetail conflicto(AuthException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "conflicto",
                "Conflicto", e.getMessage(), request);
    }

    /** 404: usuario referenciado por un flujo de auth inexistente. */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail noEncontrado(UsuarioNoEncontradoException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }
}
