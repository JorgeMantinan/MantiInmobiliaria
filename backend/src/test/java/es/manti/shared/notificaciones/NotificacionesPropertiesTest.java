package es.manti.shared.notificaciones;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El {@code toString()} de las propiedades de notificaciones NUNCA debe
 * filtrar secretos (Bajo de la revisión del PR #64): si un registro de
 * arranque o un log de diagnóstico imprime el objeto, la API key de Brevo y
 * el bot token de Telegram aparecen como {@code ***}.
 */
class NotificacionesPropertiesTest {

    @Test
    void toString_enmascaraLaApiKeyDeBrevoYElBotTokenDeTelegram() {
        NotificacionesProperties propiedades = new NotificacionesProperties(
                true,
                new NotificacionesProperties.Telegram("tok-secreto-123", "-100456"),
                new NotificacionesProperties.Brevo("brv-secreto-xyz", "no-reply@manti.dev", "Manti"));

        String texto = propiedades.toString();

        assertThat(texto)
                .as("ningún secreto debe aparecer en el toString")
                .doesNotContain("tok-secreto-123")
                .doesNotContain("brv-secreto-xyz")
                .contains("***")
                .contains("-100456")
                .contains("no-reply@manti.dev");
    }

    @Test
    void toString_conSecretosAusentes_noMarcaNada() {
        NotificacionesProperties propiedades = new NotificacionesProperties(
                false,
                new NotificacionesProperties.Telegram(null, null),
                new NotificacionesProperties.Brevo(null, null, null));

        assertThat(propiedades.toString())
                .as("sin credenciales no hay nada que enmascarar")
                .doesNotContain("***");
    }
}
