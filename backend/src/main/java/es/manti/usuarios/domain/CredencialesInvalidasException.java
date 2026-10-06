package es.manti.usuarios.domain;

/** Credenciales de login incorrectas (401). Mensaje único para no revelar si el email existe. */
public class CredencialesInvalidasException extends AuthException {

    public CredencialesInvalidasException() {
        super("Credenciales incorrectas");
    }
}
