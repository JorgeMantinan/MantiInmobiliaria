package es.manti.profesionales.domain;

/** Dos profesionales no pueden compartir email (409 en el registro público). */
public class EmailProfesionalYaRegistradoException extends RuntimeException {

    public EmailProfesionalYaRegistradoException() {
        super("El email ya está registrado como profesional");
    }
}
