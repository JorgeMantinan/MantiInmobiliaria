package es.manti.usuarios.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validación fail-fast de la configuración de cookies de sesión: una
 * configuración inválida debe romper el arrancado, no fallar en silencio en
 * producción.
 */
class AuthPropertiesValidacionTest {

    private static AuthProperties conCookie(String sameSite, boolean secure) {
        return new AuthProperties(
                null,
                new AuthProperties.Cookie("refresh_token", "/api/v1/auth", sameSite, secure),
                null,
                null);
    }

    @Test
    void sameSiteNone_sinSecure_fallaAlArrancar() {
        assertThatThrownBy(() -> conCookie("None", false).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SECURE=true");
    }

    @Test
    void sameSiteNone_conSecure_esValido() {
        assertThatCode(() -> conCookie("None", true).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void sameSiteLax_sinSecure_esValido() {
        assertThatCode(() -> conCookie("Lax", false).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void sameSiteDesconocido_fallaAlArrancar() {
        assertThatThrownBy(() -> conCookie("Unicornio", false).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void cookieNula_fallaAlArrancar() {
        AuthProperties sinCookie = new AuthProperties(null, null, null, null);
        assertThatThrownBy(sinCookie::validarConfiguracion)
                .isInstanceOf(IllegalStateException.class);
    }
}
