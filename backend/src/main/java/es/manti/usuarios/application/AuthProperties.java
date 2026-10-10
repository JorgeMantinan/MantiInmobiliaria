package es.manti.usuarios.application;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

/**
 * Propiedades de autenticación (prefijo {@code auth} en application.yml).
 * Los valores sensibles llegan exclusivamente por variables de entorno.
 * La configuración se valida al arrancar (fail-fast): una cookie con
 * {@code SameSite=None} sin {@code Secure} la rechazan los navegadores y en
 * local serviría cookies que el browser descarta en silencio.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        Cookie cookie,
        Oauth2 oauth2,
        RateLimit rateLimit) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cookie(String name, String path, String sameSite, boolean secure) {
    }

    public record Oauth2(Google google) {
    }

    public record Google(String frontendUrl, Duration codeTtl) {
    }

    public record RateLimit(int loginPerMinute, int registerPerMinute,
                            int solicitudesPerHour, int registroProfesionalPerHour,
                            int forgotPasswordPerHour, int resetPasswordPerHour) {
    }

    /** Falla al arrancar si el SameSite es inválido o incoherente con Secure. */
    @PostConstruct
    void validarConfiguracion() {
        String sameSite = cookie() == null ? null : cookie().sameSite();
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalStateException(
                    "auth.cookie.same-site es obligatorio (Lax, Strict o None)");
        }
        String normalizado = sameSite.trim();
        switch (normalizado.toLowerCase(Locale.ROOT)) {
            case "lax", "strict" -> {
                // válidos en cualquier combinación
            }
            case "none" -> {
                if (!cookie().secure()) {
                    throw new IllegalStateException(
                            "COOKIE_SAMESITE=None exige COOKIE_SECURE=true: los navegadores "
                                    + "rechazan SameSite=None sin Secure");
                }
            }
            default -> throw new IllegalStateException(
                    "auth.cookie.same-site inválido: '" + sameSite + "' (valores: Lax, Strict, None)");
        }
    }
}
