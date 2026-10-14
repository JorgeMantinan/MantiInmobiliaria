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

    // ------------------------------------------------------------------
    // Invariantes del prefijo __Host- (revisión seguridad PR #102): el
    // navegador descarta en silencio la cookie incumplidora y el síntoma en
    // prod sería "refresh roto" sin error al arrancar.
    // ------------------------------------------------------------------

    private static AuthProperties conCookieHost(String nombre, String path, boolean secure) {
        // Lax: aisla la validación del prefijo __Host- de la de SameSite.
        return new AuthProperties(
                null,
                new AuthProperties.Cookie(nombre, path, "Lax", secure),
                null,
                null);
    }

    @Test
    void prefijoHost_sinSecure_fallaAlArrancar() {
        assertThatThrownBy(() -> conCookieHost("__Host-refresh_token", "/", false).validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("__Host-")
                .hasMessageContaining("COOKIE_SECURE");
    }

    @Test
    void prefijoHost_conPathDistintoDeSlash_fallaAlArrancar() {
        assertThatThrownBy(() -> conCookieHost("__Host-refresh_token", "/api/v1/auth", true)
                        .validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("__Host-")
                .hasMessageContaining("Path");
    }

    @Test
    void prefijoHost_conSecureYPathSlash_esValido() {
        assertThatCode(() -> conCookieHost("__Host-refresh_token", "/", true).validarConfiguracion())
                .doesNotThrowAnyException();
    }

    @Test
    void sinPrefijoHost_noExigeSecureNiPath() {
        assertThatCode(() -> conCookieHost("refresh_token", "/api/v1/auth", false).validarConfiguracion())
                .doesNotThrowAnyException();
    }
}
