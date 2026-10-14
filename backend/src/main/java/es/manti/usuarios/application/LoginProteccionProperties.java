package es.manti.usuarios.application;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades de la protección del LOGIN (issues #10 y #5), prefijo
 * {@code auth.login} en {@code application.yml}. Todas tunneables por variable
 * de entorno y con validación fail-fast al arrancar.
 *
 * <p>Dos dimensiones además del rate limit por IP de
 * {@code AuthRateLimitFilter} (que se MANTIENE: son capas):</p>
 * <ul>
 *   <li><b>Backoff por cuenta</b> (#10): tras {@code fallos-antes-de-backoff}
 *       fallos consecutivos de la MISMA cuenta (da igual la IP), cada fallo
 *       adicional bloquea esa cuenta con backoff exponencial
 *       ({@code backoff-inicial * factor^n}, tope {@code backoff-tope}). Un
 *       acierto resetea el contador.</li>
 *   <li><b>Ventana por par IP+email</b> (#5): {@code pares-por-minuto}
 *       intentos por minuto para la combinación sha256(IP + email): un
 *       atacante con muchas IPs no concentra la fuerza bruta en una sola
 *       cuenta sin coste, y una IP compartida (NAT) no castiga a las demás
 *       cuentas detrás de ella.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "auth.login")
public record LoginProteccionProperties(
        boolean enabled,
        int fallosAntesDeBackoff,
        Duration backoffInicial,
        int factorBackoff,
        Duration backoffTope,
        Duration ventanaFallos,
        int paresPorMinuto) {

    /** Falla al arrancar si un valor inválido bloquearía el login para todos. */
    @PostConstruct
    void validarConfiguracion() {
        if (fallosAntesDeBackoff() < 1) {
            throw new IllegalStateException(
                    "auth.login.fallos-antes-de-backoff debe ser >= 1 (un valor < 1 bloquearía "
                            + "el login tras el primer fallo para TODAS las cuentas)");
        }
        if (backoffInicial() == null || backoffInicial().isZero() || backoffInicial().isNegative()) {
            throw new IllegalStateException("auth.login.backoff-inicial debe ser > 0");
        }
        if (factorBackoff() < 1) {
            throw new IllegalStateException("auth.login.factor-backoff debe ser >= 1");
        }
        if (backoffTope() == null || backoffTope().compareTo(backoffInicial()) < 0) {
            throw new IllegalStateException(
                    "auth.login.backoff-tope debe ser >= backoff-inicial (si no, el tope nunca aplica)");
        }
        if (ventanaFallos() == null || ventanaFallos().isZero() || ventanaFallos().isNegative()) {
            throw new IllegalStateException("auth.login.ventana-fallos debe ser > 0");
        }
        if (ventanaFallos().compareTo(backoffTope()) <= 0) {
            throw new IllegalStateException(
                    "auth.login.ventana-fallos debe ser MAYOR que backoff-tope (si expirara antes, "
                            + "el bloqueo se perdería por la caché y no por el reloj)");
        }
        if (paresPorMinuto() < 1) {
            throw new IllegalStateException(
                    "auth.login.pares-por-minuto debe ser >= 1 (un 0 bloquearía el login por "
                            + "IP+email de forma permanente)");
        }
    }
}
