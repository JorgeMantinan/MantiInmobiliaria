package es.manti.usuarios.domain;

/**
 * Base de las excepciones de autenticación. Los mensajes son fijos y genéricos:
 * nunca se filtra información interna ni se refleja entrada del usuario.
 * El mapeo a ProblemDetail RFC 9457 vive en el {@code @RestControllerAdvice}
 * compartido (ver skill estructura-spring-boot, paso 10.5).
 */
public abstract class AuthException extends RuntimeException {

    protected AuthException(String message) {
        super(message);
    }
}
