package es.manti.shared.notificaciones;

/**
 * Utilidades sobre direcciones de email para logs: los avisos de este backend
 * no deben dejar direcciones completas en los logs (PII).
 *
 * <p>Nació como {@code PasswordResetEmailSender.mascararEmail} (issue #57) y
 * se unifica aquí con el follow-up #98 para reutilizarlo también en el
 * adaptador de Brevo.</p>
 */
public final class Emails {

    private Emails() {
    }

    /**
     * Dominio del email y la inicial enmascarados ({@code a***@ejemplo.com});
     * cualquier entrada no parseable queda {@code ***}.
     */
    public static String mascarar(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***@" + email.substring(arroba + 1);
    }
}
