package es.manti.usuarios.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades de autenticación (prefijo {@code auth} en application.yml).
 * Los valores sensibles llegan exclusivamente por variables de entorno.
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

    public record RateLimit(int loginPerMinute, int registerPerMinute) {
    }
}
