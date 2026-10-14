package es.manti.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue #5 — guarda de arranque del perfil prod: {@code
 * FORWARD_HEADERS_STRATEGY=none} debe rechazarse con un mensaje accionable,
 * porque el rate limit por IP y HSTS dependen de que el proxy sobrescriba
 * X-Forwarded-For/-Proto. Sin la variable (default del perfil: framework) o
 * con framework/native, el arranque continúa.
 */
class ForwardHeadersGuardConfigTest {

    @Test
    void estrategiaNone_fallaAlArrancarConMensajeAccionable() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "none");

        assertThatThrownBy(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORWARD_HEADERS_STRATEGY=none")
                .hasMessageContaining("rate limit por IP");
    }

    @Test
    void estrategiaFramework_esValida() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "framework");

        assertThatCode(() -> new ForwardHeadersGuardConfig(entorno).validarForwardHeadersEnProd())
                .doesNotThrowAnyException();
    }

    @Test
    void sinVariable_esValidoUsaElDefaultDelPerfil() {
        assertThatCode(() -> new ForwardHeadersGuardConfig(new MockEnvironment())
                .validarForwardHeadersEnProd())
                .doesNotThrowAnyException();
    }
}
