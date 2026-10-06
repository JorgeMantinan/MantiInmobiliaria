package es.manti.usuarios.domain;

/** Código de un solo uso de Google inválido o caducado (401). */
public class CodigoInvalidoException extends AuthException {

    public CodigoInvalidoException() {
        super("Código de autenticación inválido o caducado");
    }
}
