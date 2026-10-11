package es.manti.mercado.api;

import es.manti.mercado.domain.FormatoCsvInvalidoException;
import es.manti.mercado.domain.FuenteMivauNoDisponibleException;
import es.manti.mercado.domain.ImportacionEnCursoException;
import es.manti.mercado.domain.MercadoNoEncontradoException;
import es.manti.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeo a ProblemDetail RFC 9457 de las excepciones del módulo mercado.
 *
 * <p>Acotado a {@code es.manti.mercado.api} y con la máxima precedencia para
 * que el handler genérico de {@code shared} (último recurso con
 * {@code Exception.class}) no capture estas excepciones. Ver
 * {@code ArquitecturaTest}: {@code shared} no depende de {@code ..domain..}.</p>
 *
 * <p>Los mensajes que van al cliente son FIJOS: los datos de la fuente externa
 * (cabeceras reales) sólo salen por el log.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice(basePackages = "es.manti.mercado.api")
public class MercadoErrorAdvice {

    /** Municipio sin ningún dato publicado (ni alquiler ni valor tasado). */
    @ExceptionHandler(MercadoNoEncontradoException.class)
    public ProblemDetail noEncontrado(MercadoNoEncontradoException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    /** Otra importación ocupando el candado del endpoint admin. */
    @ExceptionHandler(ImportacionEnCursoException.class)
    public ProblemDetail importacionEnCurso(ImportacionEnCursoException e,
                                             HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "importacion-en-curso",
                "Importación en curso", e.getMessage(), request);
    }

    /**
     * El MIVAU publicó un CSV con otra estructura. El cliente recibe un detail
     * fijo; la cabecera real se va al log para poder diagnosticarlo.
     */
    @ExceptionHandler(FormatoCsvInvalidoException.class)
    public ProblemDetail formatoInvalido(FormatoCsvInvalidoException e, HttpServletRequest request) {
        log.warn("El MIVAU ha publicado un CSV con estructura distinta; cabecera detectada: {}",
                e.cabeceraDetectada());
        return Problems.crear(HttpStatus.INTERNAL_SERVER_ERROR, "fuente-no-disponible",
                "Fuente no disponible", "El CSV del MIVAU tiene una estructura inesperada", request);
    }

    /**
     * El CDN del MIVAU falla o no responde. Sólo puede llegar desde el endpoint
     * admin: la lectura pública no toca la red.
     */
    @ExceptionHandler(FuenteMivauNoDisponibleException.class)
    public ProblemDetail fuenteNoDisponible(FuenteMivauNoDisponibleException e,
                                            HttpServletRequest request) {
        return Problems.crear(HttpStatus.INTERNAL_SERVER_ERROR, "fuente-no-disponible",
                "Fuente no disponible", e.getMessage(), request);
    }
}
