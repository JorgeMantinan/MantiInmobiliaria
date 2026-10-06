package es.manti.usuarios.application;

/**
 * Datos de registro recibidos del controller (la validación vive en el DTO de api).
 */
public record RegistroCommand(String nombre, String email, String password) {
}
