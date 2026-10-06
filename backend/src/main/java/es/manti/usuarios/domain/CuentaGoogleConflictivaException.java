package es.manti.usuarios.domain;

/** La cuenta de Google no puede vincularse a la cuenta existente (409 / redirect con error). */
public class CuentaGoogleConflictivaException extends AuthException {

    public CuentaGoogleConflictivaException() {
        super("La cuenta de Google no puede vincularse a este usuario");
    }
}
