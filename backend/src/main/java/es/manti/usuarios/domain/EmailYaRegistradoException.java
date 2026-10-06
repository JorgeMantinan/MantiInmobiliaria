package es.manti.usuarios.domain;

/** El email ya está registrado (409). */
public class EmailYaRegistradoException extends AuthException {

    public EmailYaRegistradoException() {
        super("El email ya está registrado");
    }
}
