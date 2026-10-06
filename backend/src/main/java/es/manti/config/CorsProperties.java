package es.manti.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Allowlist de orígenes CORS (prefijo {@code manti.cors}).
 * Sólo orígenes concretos: {@code *} con credenciales está prohibido
 * (Spring lo rechaza y además permitiría robar cookies).
 * La validación es fail-fast al arrancar y se loguea la allowlist efectiva
 * (URLs públicas, sin secretos).
 */
@Slf4j
@ConfigurationProperties(prefix = "manti.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final Pattern LOCALHOST = Pattern.compile("^localhost(:\\d+)?$");

    @PostConstruct
    void validarConfiguracion() {
        log.info("CORS allowlist efectiva: {}", origenesNormalizados());
    }

    /**
     * Orígenes válidos normalizados: {@code localhost[:puerto]} sin esquema se
     * completa a {@code http://}; todo lo demás debe llevar {@code http(s)://}.
     *
     * @throws IllegalStateException si la allowlist está vacía, contiene
     *                               {@code *} o un origen sin esquema
     */
    public List<String> origenesNormalizados() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "manti.cors.allowed-origins (ALLOWED_ORIGINS) no puede estar vacío");
        }
        List<String> normalizados = new ArrayList<>(allowedOrigins.size());
        for (String origenCrudo : allowedOrigins) {
            String origen = origenCrudo == null ? "" : origenCrudo.trim();
            if (origen.isEmpty()) {
                throw new IllegalStateException(
                        "manti.cors.allowed-origins (ALLOWED_ORIGINS) contiene un origen vacío");
            }
            if ("*".equals(origen)) {
                throw new IllegalStateException(
                        "manti.cors.allowed-origins nunca puede contener '*' con allowCredentials=true");
            }
            if (origen.startsWith("http://") || origen.startsWith("https://")) {
                normalizados.add(origen);
            } else if (LOCALHOST.matcher(origen).matches()) {
                normalizados.add("http://" + origen);
            } else {
                throw new IllegalStateException(
                        "Origen CORS sin esquema http(s): '" + origen + "' (ejemplo: https://app.manti.es)");
            }
        }
        return List.copyOf(normalizados);
    }
}
