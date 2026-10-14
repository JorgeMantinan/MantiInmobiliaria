package es.manti.usuarios.domain;

/**
 * 400 (issue #10): la contraseña propuesta aparece en filtraciones públicas
 * (comprobación HIBP en registro y restablecimiento). El mensaje es fijo y
 * NUNCA refleja la contraseña ni el número de apariciones.
 */
public class PasswordComprometidoException extends AuthException {

    public PasswordComprometidoException() {
        super("La contraseña forma parte de una filtración pública; elija una distinta");
    }
}
