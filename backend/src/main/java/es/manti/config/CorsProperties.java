package es.manti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Allowlist de orígenes CORS (prefijo {@code manti.cors}).
 * Sólo orígenes concretos: {@code *} con credenciales está prohibido
 * (Spring lo rechaza y además permitiría robar cookies).
 */
@ConfigurationProperties(prefix = "manti.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
