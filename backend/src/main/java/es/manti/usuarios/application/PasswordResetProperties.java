package es.manti.usuarios.application;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades del flujo de restablecimiento de contraseña (issue #57),
 * prefijo {@code auth.password-reset}.
 *
 * <p>{@code frontendUrl} es la base del portal ({@code FRONTEND_URL}); el
 * enlace que viaja por email es
 * {@code ${frontendUrl}/restablecer?token=<opaco>} — la página pública del
 * frontend la consume otro agente.</p>
 *
 * <p>{@code sync} (default {@code false}) fuerza el envío del email EN LÍNEA
 * en vez de asíncrono (revisión del PR #64): los tests de este repo lo
 * activan ({@code application-test.yml}) para poder verificar el envío con el
 * mock del puerto sin carreras de hilos; producción corre siempre por el
 * executor para que el 202 no espere a Brevo.</p>
 *
 * <p>Separado de {@link AuthProperties} a propósito: añadirlo allí obligaría
 * a reescribir todos los constructores de prueba de esa record y esta
 * configuración tiene vida propia (TTL, ruta del front y modo de envío).</p>
 */
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(Duration ttl, String frontendUrl, boolean sync) {

    /** Tope del TTL (Bajo de la revisión del PR #64): un enlace vivo más de 2 h alarga la ventana de abuso. */
    private static final Duration TTL_MAXIMO = Duration.ofHours(2);

    /** Falla al arrancar si algo falta o el TTL supera el tope (fail-fast). */
    @PostConstruct
    void validarConfiguracion() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("auth.password-reset.ttl debe ser positivo (p. ej. 15m)");
        }
        if (ttl.compareTo(TTL_MAXIMO) > 0) {
            throw new IllegalStateException(
                    "auth.password-reset.ttl no puede superar " + TTL_MAXIMO + " (recibido: " + ttl + ")");
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException("auth.password-reset.frontend-url es obligatorio (FRONTEND_URL)");
        }
    }
}
