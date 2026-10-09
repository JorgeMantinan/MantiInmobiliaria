package es.manti.swipes.domain;

import es.manti.swipes.domain.SwipeCursor.Ambito;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unitario del cursor opaco del deck y de favoritos (patrón keyset de inmuebles). */
class SwipeCursorTest {

    private static final Instant INSTANTE = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void codificarYDecodificar_recuperaLosMismosValores() {
        SwipeCursor cursor = new SwipeCursor(Ambito.DECK, INSTANTE, 42L);

        SwipeCursor decodificado = SwipeCursor.decodificar(cursor.codificar(), Ambito.DECK);

        assertThat(decodificado).isEqualTo(cursor);
        assertThat(decodificado.ambito()).isEqualTo(Ambito.DECK);
        assertThat(decodificado.creadoEn()).isEqualTo(INSTANTE);
        assertThat(decodificado.id()).isEqualTo(42L);
    }

    @Test
    void codificar_esBase64UrlSinRellenar() {
        String codificado = new SwipeCursor(Ambito.FAVORITOS, INSTANTE, 7L).codificar();

        assertThat(codificado).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }

    @Test
    void decodificar_cursorDeOtroAmbito_lanzaCursorInvalido() {
        String cursorDeck = new SwipeCursor(Ambito.DECK, INSTANTE, 1L).codificar();

        assertThatThrownBy(() -> SwipeCursor.decodificar(cursorDeck, Ambito.FAVORITOS))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_textoQueNoEsUnCursor_lanzaCursorInvalido() {
        assertThatThrownBy(() -> SwipeCursor.decodificar("dGV4dG9xdWU", Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> SwipeCursor.decodificar("", Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        // base64 con caracteres fuera del subconjunto url-safe
        assertThatThrownBy(() -> SwipeCursor.decodificar("a+b/c==", Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_base64SinFormatoDeTresPartes_lanzaCursorInvalido() {
        String sinId = codificarBruto("DECK|2026-10-01T10:00:00Z");
        String conMasPartes = codificarBruto("DECK|2026-10-01T10:00:00Z|1|extra");

        assertThatThrownBy(() -> SwipeCursor.decodificar(sinId, Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> SwipeCursor.decodificar(conMasPartes, Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_ambitoDesconocido_lanzaCursorInvalido() {
        assertThatThrownBy(() -> SwipeCursor.decodificar(
                codificarBruto("OTRO|2026-10-01T10:00:00Z|1"), Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_instanteOIdNoParseables_lanzaCursorInvalido() {
        assertThatThrownBy(() -> SwipeCursor.decodificar(
                codificarBruto("DECK|ayer|1"), Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> SwipeCursor.decodificar(
                codificarBruto("DECK|2026-10-01T10:00:00Z|abc"), Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> SwipeCursor.decodificar(
                codificarBruto("DECK|2026-10-01T10:00:00Z|0"), Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
        assertThatThrownBy(() -> SwipeCursor.decodificar(
                codificarBruto("DECK|2026-10-01T10:00:00Z|-5"), Ambito.DECK))
                .isInstanceOf(CursorInvalidoException.class);
    }

    private static String codificarBruto(String bruto) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bruto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
