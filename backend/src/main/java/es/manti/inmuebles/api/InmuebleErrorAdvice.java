package es.manti.inmuebles.api;

import es.manti.inmuebles.domain.CursorInvalidoException;
import es.manti.inmuebles.domain.InmuebleNoEncontradoException;
import es.manti.inmuebles.domain.InmuebleYaEliminadoException;
import es.manti.inmuebles.domain.ParametroInvalidoException;
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
 * Mapeo a ProblemDetail RFC 9457 de las excepciones de dominio de INMUEBLES.
 *
 * <p>Está acotado a {@code basePackages = "es.manti.inmuebles.api"} para que
 * {@code shared} (que sólo maneja errores genéricos y tiene un
 * {@code @ExceptionHandler(Exception.class)} de último recurso) no capture estas
 * excepciones: los advices se consultan en orden y éste va primero
 * ({@link Order}). Ver {@code ArquitecturaTest} — {@code shared} no depende de
 * {@code ..domain..}.</p>
 *
 * <p>Mensajes fijos del dominio: no se filtra ningún dato interno.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "es.manti.inmuebles.api")
public class InmuebleErrorAdvice {

    /** Detalle de un anuncio inexistente o ya eliminado (soft-delete). */
    @ExceptionHandler(InmuebleNoEncontradoException.class)
    public ProblemDetail noEncontrado(InmuebleNoEncontradoException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    /** Backoffice escribiendo sobre un anuncio borrado: 409. */
    @ExceptionHandler(InmuebleYaEliminadoException.class)
    public ProblemDetail yaEliminado(InmuebleYaEliminadoException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "inmueble-ya-eliminado",
                "Inmueble ya eliminado", e.getMessage(), request);
    }

    /**
     * Query params de negocio inválidos del listado: cursor corrupto/emitido con
     * otro orden, {@code sort} no admitido o rangos inválidos
     * ({@code precioMin > precioMax}, precios negativos, habitaciones &lt; 0).
     */
    @ExceptionHandler({CursorInvalidoException.class, ParametroInvalidoException.class})
    public ProblemDetail parametroDeListado(RuntimeException e, HttpServletRequest request) {
        Map<String, String> error = e instanceof ParametroInvalidoException p
                ? Problems.error(p.parametro(), p.motivo())
                : Problems.error("cursor", e.getMessage());
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "parametro-invalido",
                "Parámetro inválido", e.getMessage(), request, List.of(error));
    }
}
