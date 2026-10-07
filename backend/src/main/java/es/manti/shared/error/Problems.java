package es.manti.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Construcción de {@link ProblemDetail} (RFC 9457) compartida por TODOS los
 * {@code @ControllerAdvice}: el genérico de {@code shared} y los específicos de
 * cada módulo ({@code InmuebleErrorAdvice}, {@code UsuarioErrorAdvice}).
 *
 * <p>Vive en {@code shared} porque sólo depende de Spring y del servlet: así los
 * advices de módulo pueden construir sus problemas <b>sin que {@code shared}
 * importe dominio de ningún módulo</b> (regla ArchUnit
 * {@code shared no depende de ..domain..}).</p>
 */
public final class Problems {

    /** Mismo prefijo de tipo que usa {@link ProblemResponseWriter} (RFC 9457). */
    public static final String PROBLEMS_BASE = ProblemResponseWriter.PROBLEMS_BASE;

    private Problems() {
    }

    /**
     * @param status    estado HTTP
     * @param typeSlug  último tramo del {@code type} (p. ej. {@code no-encontrado})
     * @param title     título corto
     * @param detail    detalle legible (sólo mensajes fijos de dominio)
     * @param request   petición, para el {@code instance}
     */
    public static ProblemDetail crear(HttpStatus status, String typeSlug, String title,
                                      String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(PROBLEMS_BASE + typeSlug));
        pd.setInstance(URI.create(request.getRequestURI()));
        return pd;
    }

    /** Añade la lista de errores campo/mensaje que espera la API ({@code errors[]}). */
    public static ProblemDetail conErrores(HttpStatus status, String typeSlug, String title,
                                           String detail, HttpServletRequest request,
                                           List<Map<String, String>> errores) {
        ProblemDetail pd = crear(status, typeSlug, title, detail, request);
        pd.setProperty("errors", errores);
        return pd;
    }

    /** Atajo para un único error con campo. */
    public static Map<String, String> error(String campo, String mensaje) {
        return Map.of("field", campo == null ? "param" : campo, "message", mensaje);
    }
}
