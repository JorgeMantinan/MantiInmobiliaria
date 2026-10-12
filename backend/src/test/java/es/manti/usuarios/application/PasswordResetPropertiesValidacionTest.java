package es.manti.usuarios.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validación fail-fast de {@code auth.password-reset} (revisión del PR #64):
 * el TTL debe ser positivo y no superar el tope de 2 h — un enlace de
 * restablecimiento vivo demasiado tiempo alarga la ventana de abuso.
 */
class PasswordResetPropertiesValidacionTest {

    private static PasswordResetProperties conTtl(Duration ttl) {
        return new PasswordResetProperties(ttl, "http://localhost:3000", true);
    }

    @Test
    void ttlDeQuinceMinutos_esValido() {
        assertThatCode(() -> conTtl(Duration.ofMinutes(15)).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void ttlDeDosHoras_esElTopeValido() {
        assertThatCode(() -> conTtl(Duration.ofHours(2)).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void ttlPorEncimaDeDosHoras_fallaAlArrancar() {
        assertThatThrownBy(() -> conTtl(Duration.ofHours(3)).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no puede superar");
    }

    @Test
    void ttlNulo_fallaAlArrancar() {
        assertThatThrownBy(() -> conTtl(null).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ttlNegativo_fallaAlArrancar() {
        assertThatThrownBy(() -> conTtl(Duration.ofMinutes(-5)).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void frontendUrlVacio_fallaAlArrancar() {
        assertThatThrownBy(() -> new PasswordResetProperties(Duration.ofMinutes(15), "  ", true)
                .validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frontend-url");
    }
}
