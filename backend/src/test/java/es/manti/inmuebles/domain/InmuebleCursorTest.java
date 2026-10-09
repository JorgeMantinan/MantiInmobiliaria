package es.manti.inmuebles.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Codificación/decodificación del cursor opaco del feed (keyset). */
class InmuebleCursorTest {

    private static final Instant CREADO = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void codificarYDecodificar_ordenCreadoDesc_devuelveElMismoCursor() {
        InmuebleCursor original = new InmuebleCursor(InmuebleOrden.CREADO_DESC, CREADO, null, 42L);

        InmuebleCursor decodificado = InmuebleCursor.decodificar(original.codificar(), InmuebleOrden.CREADO_DESC);

        assertThat(decodificado).isEqualTo(original);
        // Opaque para el cliente: ni separador ni nombre de orden en claro.
        assertThat(original.codificar()).doesNotContain("|").doesNotContain("2026-09-01");
    }

    @Test
    void codificarYDecodificar_ordenPrecioAsc_devuelveElMismoCursor() {
        InmuebleCursor original = new InmuebleCursor(InmuebleOrden.PRECIO_ASC, null, new BigDecimal("245000.50"), 7L);

        InmuebleCursor decodificado = InmuebleCursor.decodificar(original.codificar(), InmuebleOrden.PRECIO_ASC);

        assertThat(decodificado).isEqualTo(original);
    }

    @Test
    void decodificar_textoQueNoEsUnCursor_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar("dGV4dG9xdWUtbm9jdXJzbw", InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_cursorEmitidoOtroOrden_lanzaCursorInvalido() {
        String cursorDePrecio = new InmuebleCursor(InmuebleOrden.PRECIO_DESC, null, BigDecimal.TEN, 3L).codificar();

        assertThatThrownBy(() -> InmuebleCursor.decodificar(cursorDePrecio, InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_noBase64_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar("no es base 64 !!", InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_ordenDesconocido_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar(
                cursor("VIEJO|2026-09-01T10:00:00Z|5"), InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_fechaNoValida_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar(
                cursor("CREADO_DESC|ayer|5"), InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_precioNoNumerico_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar(
                cursor("PRECIO_ASC|gratis|5"), InmuebleOrden.PRECIO_ASC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_idNoNumerico_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar(
                cursor("CREADO_DESC|2026-09-01T10:00:00Z|abc"), InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    @Test
    void decodificar_idNoPositivo_lanzaCursorInvalido() {
        assertThatThrownBy(() -> InmuebleCursor.decodificar(
                cursor("CREADO_DESC|2026-09-01T10:00:00Z|0"), InmuebleOrden.CREADO_DESC))
                .isInstanceOf(CursorInvalidoException.class);
    }

    /** Codifica un cursor a mano para probar estados ilegales sin pasar por {@code codificar()}. */
    private static String cursor(String bruto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }
}