package es.manti.usuarios.domain;

/**
 * Token de restablecimiento de contraseña inválido, caducado o ya canjeado
 * (400 con ProblemDetail {@code token-invalido}).
 *
 * <p>Un único mensaje para los tres casos a propósito: no se distingue "el
 * token no existe" de "ya se usó" para no convertir el endpoint en un
 * oráculo de tokens.</p>
 */
public class PasswordResetInvalidoException extends AuthException {

    public PasswordResetInvalidoException() {
        super("El enlace de restablecimiento no es válido, ha caducado o ya ha sido utilizado");
    }
}
