package es.manti.profesionales.api;

import es.manti.profesionales.domain.ClaveIdempotenciaConflictivaException;
import es.manti.profesionales.domain.EmailProfesionalYaRegistradoException;
import es.manti.profesionales.domain.EstadoProfesionalNoPermitidoException;
import es.manti.profesionales.domain.GremioInexistenteException;
import es.manti.shared.notificaciones.NotificacionException;
import es.manti.profesionales.domain.ProfesionalNoEncontradoException;
import es.manti.profesionales.domain.TelegramSinCredencialException;
import es.manti.shared.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Mapeo a ProblemDetail RFC 9457 de las excepciones de dominio de
 * PROFESIONALES (gremios, solicitudes, outbox y diagnóstico).
 *
 * <p>Acotado a {@code basePackages = "es.manti.profesionales.api"} para que el
 * advice genérico de {@code shared} —con su catch-all de 500— no capture estas
 * excepciones: éste va primero ({@link Order}). Mensajes fijos del dominio, sin
 * datos internos.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "es.manti.profesionales.api")
public class ProfesionalErrorAdvice {

    /** Algún slug de {@code gremios[]} no existe en el catálogo → 400. */
    @ExceptionHandler(GremioInexistenteException.class)
    public ProblemDetail gremioInexistente(GremioInexistenteException e, HttpServletRequest request) {
        return Problems.conErrores(HttpStatus.BAD_REQUEST, "gremio-inexistente",
                "Gremio inexistente", e.getMessage(), request,
                List.of(Problems.error("gremios", e.getMessage())));
    }

    /** Registro con un email ya usado → 409. */
    @ExceptionHandler(EmailProfesionalYaRegistradoException.class)
    public ProblemDetail emailYaRegistrado(EmailProfesionalYaRegistradoException e,
                                           HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "email-ya-registrado",
                "Email ya registrado", e.getMessage(), request);
    }

    /** Clave Idempotency-Key reutilizada con otro cuerpo → 409. */
    @ExceptionHandler(ClaveIdempotenciaConflictivaException.class)
    public ProblemDetail claveIdempotenciaConflictiva(ClaveIdempotenciaConflictivaException e,
                                                      HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "idempotencia-conflicto",
                "Clave de idempotencia en uso", e.getMessage(), request);
    }

    /** Backoffice escribiendo sobre un id inexistente → 404. */
    @ExceptionHandler(ProfesionalNoEncontradoException.class)
    public ProblemDetail profesionalNoEncontrado(ProfesionalNoEncontradoException e,
                                                 HttpServletRequest request) {
        return Problems.crear(HttpStatus.NOT_FOUND, "no-encontrado",
                "No encontrado", e.getMessage(), request);
    }

    /** Estado que la moderación no admite (p. ej. volver a PENDIENTE) → 409. */
    @ExceptionHandler(EstadoProfesionalNoPermitidoException.class)
    public ProblemDetail estadoNoPermitido(EstadoProfesionalNoPermitidoException e,
                                           HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "estado-no-permitido",
                "Estado no permitido", e.getMessage(), request);
    }

    /** Falta {@code TELEGRAM_BOT_TOKEN} en el diagnóstico → 409. */
    @ExceptionHandler(TelegramSinCredencialException.class)
    public ProblemDetail telegramSinCredencial(TelegramSinCredencialException e,
                                               HttpServletRequest request) {
        return Problems.crear(HttpStatus.CONFLICT, "telegram-sin-credencial",
                "Telegram sin credencial", e.getMessage(), request);
    }

    /**
     * El proveedor externo (Brevo/Telegram) falló en una llamada SÍNCRONA del
     * cliente → 502: la API es el cliente de un servicio que no ha respondido
     * bien. Sólo se alcanza desde endpoints (el dispatcher captura su propia
     * excepción y la guarda en el outbox).
     */
    @ExceptionHandler(NotificacionException.class)
    public ProblemDetail notificacionFallida(NotificacionException e, HttpServletRequest request) {
        return Problems.crear(HttpStatus.BAD_GATEWAY, "notificacion-fallida",
                "Servicio de notificaciones no disponible", e.getMessage(), request);
    }
}
