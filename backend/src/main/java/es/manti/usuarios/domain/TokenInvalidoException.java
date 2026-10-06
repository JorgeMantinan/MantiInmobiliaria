package es.manti.usuarios.domain;

/** Refresh token inválido, expirado o ya rotado (401). */
public class TokenInvalidoException extends AuthException {

    public TokenInvalidoException() {
        super("Sesión inválida o caducada");
    }
}
