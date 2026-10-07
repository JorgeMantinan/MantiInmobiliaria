package es.manti.inmuebles.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Interpretación del query param {@code sort} del listado público. */
class InmuebleOrdenTest {

    @Test
    void desdeParametro_ausente_devuelveOrdenPorDefecto() {
        assertThat(InmuebleOrden.desdeParametro(null)).isEqualTo(InmuebleOrden.CREADO_DESC);
        assertThat(InmuebleOrden.desdeParametro("  ")).isEqualTo(InmuebleOrden.CREADO_DESC);
    }

    @Test
    void desdeParametro_losValoresAdmitidos_devuelvenSuOrden() {
        assertThat(InmuebleOrden.desdeParametro("precio_asc")).isEqualTo(InmuebleOrden.PRECIO_ASC);
        assertThat(InmuebleOrden.desdeParametro("precio_desc")).isEqualTo(InmuebleOrden.PRECIO_DESC);
    }

    @Test
    void desdeParametro_valorDesconocido_lanzaParametroInvalido() {
        assertThatThrownBy(() -> InmuebleOrden.desdeParametro("fecha"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("precio_asc");
    }
}
