package es.manti.usuarios.domain;

/** Usuario inactivo (401 en autenticación). */
public class UsuarioInactivoException extends AuthException {

    public UsuarioInactivoException() {
        super("Cuenta desactivada");
    }
}
