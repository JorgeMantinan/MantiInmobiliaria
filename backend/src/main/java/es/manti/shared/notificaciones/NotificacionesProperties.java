package es.manti.shared.notificaciones;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de las notificaciones (prefijo {@code manti.notificaciones}).
 * El interruptor global {@code enabled} decide si el despachador envía o sólo
 * encola: en local y en CI está a {@code false} para no spamear proveedores.
 *
 * <p>Esta clase vive en {@code shared} (issue #57): es configuración de la
 * infraestructura de envío (Brevo/Telegram) compartida por todos los módulos,
 * no lógica de dominio de ninguno de ellos.</p>
 *
 * <p>Los secretos llegan SÓLO por variables de entorno (skill config-secrets):
 * nunca se escriben literales aquí.</p>
 */
@ConfigurationProperties(prefix = "manti.notificaciones")
public record NotificacionesProperties(boolean enabled, Telegram telegram, Brevo brevo) {

    /** {@code TELEGRAM_BOT_TOKEN} + {@code TELEGRAM_CHAT_ID}. */
    public record Telegram(String botToken, String chatId) {

        public boolean tieneToken() {
            return botToken != null && !botToken.isBlank();
        }

        /** El chat_id del grupo es obligatorio para encolar avisos de Telegram. */
        public boolean tieneChat() {
            return chatId != null && !chatId.isBlank();
        }

        /** El bot token NUNCA aparece en logs (Bajo de la revisión del PR #64). */
        @Override
        public String toString() {
            return "Telegram[botToken=" + enmascarar(botToken) + ", chatId=" + chatId + "]";
        }
    }

    /** {@code BREVO_API_KEY} + remitente del email. */
    public record Brevo(String apiKey, String fromEmail, String fromName) {

        public boolean tieneCredencial() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** La API key NUNCA aparece en logs (Bajo de la revisión del PR #64). */
        @Override
        public String toString() {
            return "Brevo[apiKey=" + enmascarar(apiKey)
                    + ", fromEmail=" + fromEmail + ", fromName=" + fromName + "]";
        }
    }

    /** Un secreto presente se sustituye por {@code ***}; el ausente se ve tal cual. */
    private static String enmascarar(String secreto) {
        return secreto == null || secreto.isBlank() ? String.valueOf(secreto) : "***";
    }
}
