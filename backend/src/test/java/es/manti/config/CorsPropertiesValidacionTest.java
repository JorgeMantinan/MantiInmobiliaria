package es.manti.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validación fail-fast de ALLOWED_ORIGINS: la allowlist CORS se comprueba al
 * arrancar (vacíos, {@code *} y orígenes sin esquema rechazados) y se loguea
 * la allowlist efectiva.
 */
class CorsPropertiesValidacionTest {

    @Test
    void asterisco_fallaAlArrancar() {
        CorsProperties props = new CorsProperties(List.of("*"));
        assertThatThrownBy(props::origenesNormalizados)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'*'");
    }

    @Test
    void listaVacia_fallaAlArrancar() {
        assertThatThrownBy(() -> new CorsProperties(List.of()).origenesNormalizados())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void origenVacio_fallaAlArrancar() {
        assertThatThrownBy(() -> new CorsProperties(List.of("  ")).origenesNormalizados())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void origenSinEsquema_fallaAlArrancar() {
        assertThatThrownBy(() -> new CorsProperties(List.of("app.manti.es")).origenesNormalizados())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("esquema");
    }

    @Test
    void origenConEsquema_esValido() {
        CorsProperties props = new CorsProperties(List.of("https://app.manti.es"));
        assertThat(props.origenesNormalizados()).containsExactly("https://app.manti.es");
    }

    @Test
    void localhostSinEsquema_seNormalizaAHttp() {
        CorsProperties props = new CorsProperties(List.of(" localhost:3000 "));
        assertThat(props.origenesNormalizados()).containsExactly("http://localhost:3000");
    }

    @Test
    void localhostFalsoNoDominio_fallaAlArrancar() {
        assertThatThrownBy(() -> new CorsProperties(List.of("localhostevil.com")).origenesNormalizados())
                .isInstanceOf(IllegalStateException.class);
    }
}
