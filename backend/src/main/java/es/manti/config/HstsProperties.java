package es.manti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HSTS — HTTP Strict Transport Security (issue #9), prefijo
 * {@code manti.seguridad.hsts}.
 *
 * <p>SÓLO se emite en el perfil prod ({@code application-prod.yml} lo activa):
 * la cabecera la escribe Spring Security únicamente cuando la petición es
 * SEGURA, y detrás del TLS terminator de Vercel lo es gracias a
 * {@code server.forward-headers-strategy=framework} (X-Forwarded-Proto). En
 * local sobre http:// la cabecera no aparece (navegadores la ignorarían o
 * marcarían el origen como no confiable).</p>
 *
 * @param enabled       emite o no la cabecera (default false fuera de prod)
 * @param maxAgeSegundos vida del pin en el navegador (1 año por defecto)
 */
@ConfigurationProperties(prefix = "manti.seguridad.hsts")
public record HstsProperties(boolean enabled, long maxAgeSegundos) {
}
