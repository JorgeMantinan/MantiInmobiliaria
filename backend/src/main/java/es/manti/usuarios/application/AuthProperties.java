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

    /**
     * Límites por ruta de {@code AuthRateLimitFilter}: peticiones admitidas por
     * IP dentro de la ventana que le corresponde (minuto o hora). Todos son
     * tunneables por variable de entorno ({@code AUTH_RATE_*}) y se suben a un
     * valor muy alto en el perfil de test para que la suite nunca dependa del
     * rate limit (sólo lo comprueban las clases dedicadas, con overrides).
     *
     * <p>Issues #17 y #22: refresh y canje OAuth2 de Google (rotación de
     * sesiones), listado público de inmuebles (COUNT + ILIKE) y las rutas de
     * swipes del comentario del issue #17.</p>
     */
    public record RateLimit(int loginPerMinute, int registerPerMinute,
                            int solicitudesPerHour, int registroProfesionalPerHour,
                            int forgotPasswordPerHour, int resetPasswordPerHour,
                            int alertasPerMinute,
                            int refreshPerMinute, int oauth2TokenPerMinute,
                            int inmueblesPerMinute, int swipesPerMinute,
                            int deckPerMinute, int favoritosPerMinute) {
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
        validarRateLimit();
    }

    /**
     * Fail-fast de los límites: un 0 o negativo por typo de variable de
     * entorno bloquearía la ruta ENTERA con 429 permanente (revisión de
     * seguridad PR #100). El comportamiento "límite 0" sólo se prueba
     * construyendo el record a mano, fuera de Spring.
     */
    private void validarRateLimit() {
        RateLimit rl = rateLimit();
        if (rl == null) {
            return;
        }
        var invalidos = java.util.stream.Stream.of(
                        rl.loginPerMinute(), rl.registerPerMinute(),
                        rl.solicitudesPerHour(), rl.registroProfesionalPerHour(),
                        rl.forgotPasswordPerHour(), rl.resetPasswordPerHour(),
                        rl.alertasPerMinute(), rl.refreshPerMinute(),
                        rl.oauth2TokenPerMinute(), rl.inmueblesPerMinute(),
                        rl.swipesPerMinute(), rl.deckPerMinute(), rl.favoritosPerMinute())
                .filter(v -> v < 1)
                .count();
        if (invalidos > 0) {
            throw new IllegalStateException(
                    "auth.rate-limit.* debe ser >= 1 en todas las rutas (hay " + invalidos
                            + " valores < 1): un 0 bloquea la ruta entera con 429 permanente");
        }
    }
}
