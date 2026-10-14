package es.manti.usuarios.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fail-fast de {@link LoginProteccionProperties} (revisión seguridad PR #102):
 * un typo por variable de entorno (p.ej. {@code AUTH_LOGIN_FALLOS_BACKOFF=0})
 * debe reventar el arranque, no dejar el login bloqueado para todas las
 * cuentas en silencio. Mismo patrón que {@code AuthPropertiesValidacionTest}.
 */
class LoginProteccionPropertiesValidacionTest {

    private static LoginProteccionProperties props(int fallos, Duration inicial, int factor,
                                                   Duration tope, Duration ventana, int pares) {
        return new LoginProteccionProperties(true, fallos, inicial, factor, tope, ventana, pares);
    }

    @Test
    void valoresValidos_noLanza() {
        assertThatCode(() -> props(5, Duration.ofMinutes(1), 2,
                        Duration.ofMinutes(15), Duration.ofMinutes(30), 5).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void fallosAntesDeBackoffCero_lanza() {
        assertThatThrownBy(() -> props(0, Duration.ofMinutes(1), 2,
                        Duration.ofMinutes(15), Duration.ofMinutes(30), 5).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallos-antes-de-backoff");
    }

    @Test
    void backoffInicialNulo_lanza() {
        assertThatThrownBy(() -> props(5, null, 2,
                        Duration.ofMinutes(15), Duration.ofMinutes(30), 5).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backoff-inicial");
    }

    @Test
    void factorCero_lanza() {
        assertThatThrownBy(() -> props(5, Duration.ofMinutes(1), 0,
                        Duration.ofMinutes(15), Duration.ofMinutes(30), 5).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("factor-backoff");
    }

    @Test
    void topeMenorQueInicial_lanza() {
        assertThatThrownBy(() -> props(5, Duration.ofMinutes(10), 2,
                        Duration.ofMinutes(5), Duration.ofMinutes(30), 5).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backoff-tope");
    }

    @Test
    void ventanaIgualQueTope_lanza() {
        assertThatThrownBy(() -> props(5, Duration.ofMinutes(1), 2,
                        Duration.ofMinutes(15), Duration.ofMinutes(15), 5).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ventana-fallos");
    }

    @Test
    void paresPorMinutoCero_lanza() {
        assertThatThrownBy(() -> props(5, Duration.ofMinutes(1), 2,
                        Duration.ofMinutes(15), Duration.ofMinutes(30), 0).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pares-por-minuto");
    }
}
