package es.manti.profesionales.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de las notificaciones (prefijo {@code manti.notificaciones}).
 * El interruptor global {@code enabled} decide si el despachador envía o sólo
 * encola: en local y en CI está a {@code false} para no spamear proveedores.
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
    }

    /** {@code BREVO_API_KEY} + remitente del email. */
    public record Brevo(String apiKey, String fromEmail, String fromName) {

        public boolean tieneCredencial() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
