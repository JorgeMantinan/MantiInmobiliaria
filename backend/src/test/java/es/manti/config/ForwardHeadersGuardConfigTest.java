package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue #5 — guarda de arranque del perfil prod: sólo se admiten los valores
 * que REALMENTE registran el filtro de forward headers
 * ({@code framework}, o {@code native} si el TLS lo termina el servidor
 * embebido). {@code none}, la cadena vacía y cualquier typo dejan
 * {@code remoteAddr} apuntando al proxy: el rate limit por IP y HSTS se
 * vuelven inútiles, así que el arranque FALLA (revisión seguridad PR #102).
 */
class ForwardHeadersGuardConfigTest {

    @Test
    void estrategiaNone_fallaAlArrancarConMensajeAccionable() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "none");

        assertThatThrownBy(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.forward-headers-strategy='none'")
                .hasMessageContaining("rate limit por IP");
    }

    @Test
    void estrategiaVacia_fallaAlArrancar() {
        // FORWARD_HEADERS_STRATEGY= (vacía) resuelve el placeholder a "" y
        // NO registra el filtro: mismo efecto que "none", mismo rechazo.
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "");

        assertThatThrownBy(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO es válido");
    }

    @Test
    void estrategiaConTypo_fallaAlArrancar() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "frameworkk");

        assertThatThrownBy(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void estrategiaFramework_esValida() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "framework");

        assertThatCode(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .doesNotThrowAnyException();
    }

    @Test
    void estrategiaNative_esValida() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "native");

        assertThatCode(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .doesNotThrowAnyException();
    }
}
