package es.manti.usuarios.application;

/**
 * Evento de dominio en proceso (issue #57, revisión del PR #64): un enlace de
 * restablecimiento queda PENDIENTE de enviar tras el commit de la transacción
 * que creó su token.
 *
 * <p>Se publica SÓLO dentro de la transacción de emisión y SÓLO si
 * {@code auth.password-reset.sync=false}; el listener
 * {@link PasswordResetEmailSender} lo consume en el executor acotado
 * {@code passwordResetEmail} después del commit (skill microservicios:
 * efectos externos siempre tras el commit y fuera de la transacción).</p>
 *
 * @param email email del destinatario (la cuenta ya existe y tiene password)
 * @param token token OPACO en claro; en BD sólo vive su hash SHA-256
 */
public record EnlacePasswordReset(String email, String token) {
}
