package es.manti.swipes.api;

import es.manti.swipes.domain.CursorInvalidoException;
import es.manti.swipes.domain.FavoritoNoEncontradoException;
import es.manti.swipes.domain.InmuebleInexistenteException;
import es.manti.swipes.domain.ParametroInvalidoException;
import es.manti.swipes.domain.SesionInvalidaException;
import es.manti.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Mapeo a ProblemDetail RFC 9457 de las excepciones de dominio de SWIPES.
 *
 * <p>Está acotado a {@code basePackages = "es.manti.swipes.api"} para que
 * {@code shared} (con su {@code @ExceptionHandler(Exception.class)} de último
 * recurso) no capture estas excepciones: los advices se consultan en orden y
 * éste va primero ({@link Order}). Mismo patrón que
 * {@code es.manti.inmuebles.api.InmuebleErrorAdvice}.</p>
 *
 * <p>Mensajes fijos del dominio: no se filtra ningún dato interno ni se revela
 * si un inmueble es favorito de OTRO usuario (siempre 404, nunca 403).</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "es.manti.swipes.api")
public class SwipeErrorAdvice {

    /** Swipe sobre un inmueble inexistente o eliminado (soft-delete). */
    @ExceptionHandler(InmuebleInexistenteException.class)
    public ProblemDetail inmuebleNoEncontrado(InmuebleInexistenteException e,
                                              HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    /** Baja de un favorito que el usuario actual no tiene (o que era un NOPE). */
    @ExceptionHandler(FavoritoNoEncontradoException.class)
    public ProblemDetail favoritoNoEncontrado(FavoritoNoEncontradoException e,
                                              HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    /**
     * Cursor corrupto/no base64/emitido para otro listado, o {@code limit}
     * fuera de 1..50 → 400 con {@code errors:[{field, message}]}.
     */
    @ExceptionHandler({CursorInvalidoException.class, ParametroInvalidoException.class})
    public ProblemDetail parametroDeListado(RuntimeException e, HttpServletRequest request) {
        Map<String, String> error = e instanceof ParametroInvalidoException p
                ? Problems.error(p.parametro(), p.motivo())
                : Problems.error("cursor", e.getMessage());
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "parametro-invalido",
                "Parámetro inválido", e.getMessage(), request, List.of(error));
    }

    /** Token autenticado pero con un {@code sub} no utilizable → 401. */
    @ExceptionHandler(SesionInvalidaException.class)
    public ProblemDetail sesionInvalida(SesionInvalidaException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.UNAUTHORIZED, "no-autenticado",
                "No autenticado", e.getMessage(), request);
    }
}
