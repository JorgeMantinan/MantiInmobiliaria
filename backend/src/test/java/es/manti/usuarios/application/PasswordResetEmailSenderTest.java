package es.manti.usuarios.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Texto del TTL en el cuerpo del email (issue #57, revisión del PR #64): se
 * deriva de {@code auth.password-reset.ttl}, nunca está hardcodeado.
 */
class PasswordResetEmailSenderTest {

    @Test
    void textoTtl_deQuinceMinutos_devuelveMinutos() {
        assertThat(PasswordResetEmailSender.textoTtl(Duration.ofMinutes(15)))
                .isEqualTo("15 minutos");
    }

    @Test
    void textoTtl_deDosHoras_devuelveHoras() {
        assertThat(PasswordResetEmailSender.textoTtl(Duration.ofHours(2)))
                .isEqualTo("2 horas");
    }

    @Test
    void textoTtl_deUnaHora_usaSingular() {
        assertThat(PasswordResetEmailSender.textoTtl(Duration.ofHours(1)))
                .isEqualTo("1 hora");
    }

    @Test
    void textoTtl_deUnMinuto_usaSingular() {
        assertThat(PasswordResetEmailSender.textoTtl(Duration.ofMinutes(1)))
                .isEqualTo("1 minuto");
    }

    @Test
    void textoTtl_subMinuto_noDiceCeroMinutos() {
        assertThat(PasswordResetEmailSender.textoTtl(Duration.ofSeconds(30)))
                .as("el email nunca debe mentir con '0 minutos'")
                .isEqualTo("1 minuto");
    }
}
