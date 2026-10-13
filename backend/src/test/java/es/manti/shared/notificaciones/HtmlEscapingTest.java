package es.manti.shared.notificaciones;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escape de HTML compartido (follow-up #98): las 5 ramas del escapador y el
 * caso {@code null}, que los cuerpos de email interpretan como vacío.
 */
class HtmlEscapingTest {

    @Test
    void escapar_losCincoCaracteresSignificativos() {
        assertThat(HtmlEscaping.escapar("A<b & \"c\" 'd'>"))
                .isEqualTo("A&lt;b &amp; &quot;c&quot; &#39;d&#39;&gt;");
    }

    @Test
    void escapar_textoLimpio_quedaIgual() {
        assertThat(HtmlEscaping.escapar("Piso céntrico 250.000 €")).isEqualTo("Piso céntrico 250.000 €");
    }

    @Test
    void escapar_null_devuelveVacio() {
        assertThat(HtmlEscaping.escapar(null)).isEmpty();
    }
}
