package es.manti.usuarios.domain;

/**
 * Base de las excepciones de autenticación. Los mensajes son fijos y genéricos:
 * nunca se filtra información interna ni se refleja entrada del usuario.
 * El mapeo a ProblemDetail RFC 9457 vive en {@code UsuarioErrorAdvice}
 * (paquete {@code es.manti.usuarios.api}), nunca en {@code shared}: así el
 * transversal no importa dominio de módulos (ver skill estructura-spring-boot).
 */
public abstract class AuthException extends RuntimeException {

    protected AuthException(String message) {
        super(message);
    }
}
