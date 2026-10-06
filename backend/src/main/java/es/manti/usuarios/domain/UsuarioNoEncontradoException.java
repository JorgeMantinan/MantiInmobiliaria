package es.manti.usuarios.domain;

/** Usuario inexistente (404). */
public class UsuarioNoEncontradoException extends AuthException {

    public UsuarioNoEncontradoException() {
        super("Usuario no encontrado");
    }
}
