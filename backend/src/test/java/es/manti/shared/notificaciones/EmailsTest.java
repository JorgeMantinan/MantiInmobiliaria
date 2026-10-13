package es.manti.shared.notificaciones;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Máscara de email para logs (follow-up #98): sólo quedan la inicial y el
 * dominio; cualquier entrada no parseable queda {@code ***}.
 */
class EmailsTest {

    @Test
    void mascarar_conDominio_dejaInicialYDominio() {
        assertThat(Emails.mascarar("ana@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void mascarar_entradasNoParseables_quedanAsteriscos() {
        assertThat(Emails.mascarar(null)).isEqualTo("***");
        assertThat(Emails.mascarar("   ")).isEqualTo("***");
        assertThat(Emails.mascarar("sin-arroba")).isEqualTo("***");
        assertThat(Emails.mascarar("@example.com")).isEqualTo("***");
    }
}
